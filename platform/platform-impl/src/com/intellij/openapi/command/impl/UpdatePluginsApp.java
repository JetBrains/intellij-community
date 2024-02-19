// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.configurationStore.XmlSerializer;
import com.intellij.ide.plugins.RepositoryHelper;
import com.intellij.idea.AppMode;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.components.impl.stores.ComponentStorageUtil;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.updateSettings.impl.UpdateInstaller;
import com.intellij.openapi.updateSettings.impl.UpdateOptions;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Ref;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Works in two stages. On the first run, it collects available updates and writes an update script. The second run needs
 * {@code idea.force.plugin.updates = "true"} system property to apply the updates.
 *
 * @see AppMode#FORCE_PLUGIN_UPDATES
 * @see com.intellij.idea.Main#installPluginUpdates
 */
final class UpdatePluginsApp implements ApplicationStarter {
  private static final String OLD_CONFIG_DIR_PROPERTY = "idea.plugin.migration.config.dir";

  @Override
  @SuppressWarnings("deprecation")
  public String getCommandName() {
    return "update";
  }

  @Override
  public void premain(@NotNull List<String> args) {
    System.setProperty("idea.skip.indices.initialization", "true");

    var oldConfig = System.getProperty(OLD_CONFIG_DIR_PROPERTY);
    if (oldConfig != null) {
      log("Reading plugin repositories from " + oldConfig);
      try {
        var text = ComponentStorageUtil.loadTextContent(Path.of(oldConfig).resolve("options/updates.xml"));
        var components = ComponentStorageUtil.loadComponents(JDOMUtil.load(text), null);
        var element = components.get("UpdatesConfigurable");
        if (element != null) {
          var hosts = XmlSerializer.deserialize(element, UpdateOptions.class).getPluginHosts();
          if (!hosts.isEmpty()) {
            RepositoryHelper.amendPluginHostsProperty(hosts);
            log("Plugin hosts: " + System.getProperty("idea.plugin.hosts"));
          }
        }
      }
      catch (InvalidPathException | IOException | JDOMException e) {
        log("... failed: " + e.getMessage());
      }
    }
  }

  @Override
  public void main(@NotNull List<String> args) {
    if (Boolean.getBoolean(AppMode.FORCE_PLUGIN_UPDATES)) {
      log("Updates applied.");
      System.exit(0);
    }

    Collection<PluginDownloader> availableUpdates = UpdateChecker.getInternalPluginUpdates()
      .getPluginUpdates()
      .getAllEnabled();
    if (availableUpdates.isEmpty()) {
      log("All plugins up to date.");
      System.exit(0);
      return;
    }

    Collection<PluginDownloader> pluginsToUpdate;
    if (args.size() > 1) {
      Set<String> filter = new HashSet<>(args.subList(1, args.size()));
      pluginsToUpdate = availableUpdates.stream()
        .filter(downloader -> filter.contains(downloader.getId().getIdString()))
        .toList();
    }
    else {
      pluginsToUpdate = availableUpdates;
    }

    log("Plugins to update:");
    pluginsToUpdate.forEach(d -> log("\t" + d.getPluginName()));

    Ref<Boolean> installed = Ref.create();
    PluginDownloader.runSynchronouslyInBackground(() -> {
      installed.set(UpdateInstaller.installPluginUpdates(pluginsToUpdate, new EmptyProgressIndicator()));
    });

    if (installed.get()) {
      System.exit(0);
    }
    else {
      log("Update failed");
      System.exit(1);
    }
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static void log(String msg) {
    System.out.println(msg);
  }
}
