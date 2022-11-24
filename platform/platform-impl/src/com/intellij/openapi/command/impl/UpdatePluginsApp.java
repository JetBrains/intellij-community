// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.idea.AppMode;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.updateSettings.impl.UpdateInstaller;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jvnet.winp.Main;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Works in two stages. On the first run, it collects available updates and writes an update script. The second run needs
 * {@code idea.force.plugin.updates = "true"} system property to apply the updates.
 *
 * @author Konstantin Bulenkov
 * @see Main#FORCE_PLUGIN_UPDATES
 * @see Main#installPluginUpdates()
 */
final class UpdatePluginsApp implements ApplicationStarter {
  @Override
  public String getCommandName() {
    return "update";
  }

  @Override
  public void premain(@NotNull List<String> args) {
    System.setProperty("idea.skip.indices.initialization", "true");
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

    Set<String> filter = new HashSet<>(args.subList(1, args.size()));
    List<PluginDownloader> pluginsToUpdate = availableUpdates.stream()
      .filter(downloader -> filter.contains(downloader.getId().getIdString()))
      .toList();

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
