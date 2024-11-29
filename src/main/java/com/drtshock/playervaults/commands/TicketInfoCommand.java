/*
 * PlayerVaultsX
 * Copyright (C) 2013 Trent Hensler
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.drtshock.playervaults.commands;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.util.Permission;
import com.google.gson.Gson;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.scheduler.BukkitRunnable;
import org.kitteh.pastegg.PasteBuilder;
import org.kitteh.pastegg.PasteContent;
import org.kitteh.pastegg.PasteFile;
import org.kitteh.pastegg.Visibility;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.zip.GZIPOutputStream;

public class TicketInfoCommand implements CommandExecutor {
    private static class TicketInfo {
        private final String type = "PVX";
        private final String id = "%%__USER__%%";
        private String pluginVersion;
        private String javaVersion;
        private String serverVersion;
        private String serverName;
        private String userName;
        private boolean likesCats;
        private UUID userUUID;
        private String online;
        private int num;
        private List<PlayerInfo> permissions;
        private List<PluginInfo> plugins;
        private String startup;
        private String exceptions;
        private String mainconf;

        private static class PlayerInfo {
            private static class PermInfo {
                private String node;
                private boolean has;

                public PermInfo(String node, boolean has) {
                    this.node = node;
                    this.has = has;
                }
            }

            private String name;
            private UUID uuid;
            private List<PermInfo> permissions;

            public PlayerInfo(Player player) {
                this.name = player.getName();
                this.uuid = player.getUniqueId();
                this.permissions = new ArrayList<>();
                for (String permission : Permission.getAllConstant()) {
                    this.permissions.add(new PermInfo(permission, player.hasPermission(permission)));
                }
            }
        }

        private static class PluginInfo {
            private String name;
            private String version;
            private List<String> authors;
            private List<String> depend;
            private List<String> softdepend;
            private List<String> loadBefore;
            private boolean enabled;

            PluginInfo(Plugin plugin) {
                this.name = plugin.getName();
                PluginDescriptionFile desc = plugin.getDescription();
                this.version = desc.getVersion();
                this.authors = desc.getAuthors();
                this.depend = desc.getDepend().isEmpty() ? null : desc.getDepend();
                this.softdepend = desc.getSoftDepend().isEmpty() ? null : desc.getSoftDepend();
                this.loadBefore = desc.getLoadBefore().isEmpty() ? null : desc.getLoadBefore();
                this.enabled = plugin.isEnabled();
            }
        }
    }

    private static class TicketResponse {
        private boolean success;
        private String message;
    }

    public static final boolean likesCats = Arrays.stream(PlayerVaults.class.getDeclaredMethods()).anyMatch(m -> m.isSynthetic() && m.getName().startsWith("loadC") && m.getName().endsWith("0"));
    private final PlayerVaults plugin;

    public TicketInfoCommand(PlayerVaults plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(final CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(Permission.ADMIN)) {
            this.plugin.getTL().noPerms().title().send(sender);
            return true;
        }

        TicketInfo info = new TicketInfo();
        info.pluginVersion = plugin.getDescription().getVersion();
        info.javaVersion = System.getProperty("java.version");
        info.likesCats = likesCats;
        info.serverName = Bukkit.getName();
        info.serverVersion = Bukkit.getVersion();
        info.userName = sender.getName();
        info.userUUID = sender instanceof Player player ? player.getUniqueId() : null;
        try {
            info.num = PlayerVaults.class.getDeclaredMethods().length;
        } catch (Throwable ignored) {
        }

        Audience audience = plugin.getPlatform().sender(sender);

        info.plugins = new ArrayList<>();
        for (Plugin plug : Bukkit.getPluginManager().getPlugins()) {
            info.plugins.add(new TicketInfo.PluginInfo(plug));
        }

        if (!Bukkit.getOnlinePlayers().isEmpty()) {
            info.permissions = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                info.permissions.add(new TicketInfo.PlayerInfo(player));
            }
        }

        new BukkitRunnable() {
            private String getFile(Path file) {
                try {
                    return Files.readString(file);
                } catch (IOException e) {
                    StringWriter stringWriter = new StringWriter();
                    PrintWriter printWriter = new PrintWriter(stringWriter, true);
                    e.printStackTrace(printWriter);
                    return stringWriter.getBuffer().toString();
                }
            }

            @Override
            public void run() {
                try {
                    Path dataPath = PlayerVaults.getInstance().getDataFolder().toPath();
                    // TODO split by paper
                    String spigotConf = getFile(Paths.get("spigot.yml"));
                    info.online = Boolean.toString(Bukkit.getOnlineMode());
                    if (!Bukkit.getOnlineMode()) {
                        for (String line : spigotConf.split("\n")) {
                            if (line.contains("bungeecord") && line.contains("true")) {
                                info.online = "Bungee";
                                break;
                            }
                        }
                    }
                    info.startup = plugin.getStartupLog();
                    if (!plugin.getExceptions().isEmpty()) {
                        info.exceptions = plugin.getExceptions();
                    }
                    info.mainconf = getFile(dataPath.resolve("config/main.conf"));

                    Gson gson = new Gson();
                    String string = gson.toJson(info);
                    ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                    GZIPOutputStream gzip = new GZIPOutputStream(byteStream);
                    gzip.write(string.getBytes(StandardCharsets.UTF_8));
                    gzip.finish();
                    byte[] bytes = byteStream.toByteArray();
                    URL url = new URI("https://ticket.plugin.party/ticket").toURL();
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(3000);
                    connection.setReadTimeout(3000);
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/octet-stream");
                    connection.setRequestProperty("Accept", "application/json");
                    connection.setDoOutput(true);
                    try (OutputStream os = connection.getOutputStream()) {
                        os.write(bytes, 0, bytes.length);
                    }
                    StringBuilder content = new StringBuilder();
                    try (
                            InputStream stream = connection.getInputStream();
                            InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
                            BufferedReader in = new BufferedReader(reader)) {
                        String inputLine;
                        while ((inputLine = in.readLine()) != null) {
                            content.append(inputLine);
                        }
                    }
                    TicketResponse response = gson.fromJson(content.toString(), TicketResponse.class);

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (response.success) {
                                String url = response.message;
                                audience.sendMessage(Component.text().color(NamedTextColor.YELLOW).content("Share this URL: " + url).clickEvent(ClickEvent.openUrl(url)));
                                if (sender instanceof Player) {
                                    PlayerVaults.getInstance().getLogger().info("Share this URL: " + url);
                                }
                            } else {
                                audience.sendMessage(Component.text().color(NamedTextColor.RED).content("ERROR! Could not generate ticket info. See console for why."));
                                PlayerVaults.getInstance().getLogger().warning("Received: " + response.message);
                            }
                        }
                    }.runTask(PlayerVaults.getInstance());
                } catch (Exception e) {
                    PlayerVaults.getInstance().getLogger().log(Level.SEVERE, "Failed to execute ticketinfo command", e);
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            audience.sendMessage(Component.text().color(NamedTextColor.RED).content("ERROR! Could not generate ticket info. See console for why."));
                        }
                    }.runTask(PlayerVaults.getInstance());
                }
            }
        }.runTaskAsynchronously(PlayerVaults.getInstance());
        audience.sendMessage(Component.text().color(NamedTextColor.YELLOW).content("Now running..."));

        return true;
    }
}