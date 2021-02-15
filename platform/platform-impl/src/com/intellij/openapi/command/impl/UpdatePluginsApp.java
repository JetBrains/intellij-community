// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.impl;

import com.intellij.idea.Main;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.updateSettings.impl.UpdateInstaller;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

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
    if (Boolean.getBoolean(Main.FORCE_PLUGIN_UPDATES)) {
      log("Updates applied.");
      System.exit(0);
    }

    Collection<PluginDownloader> availableUpdates = UpdateChecker.getPluginUpdates();
    if (availableUpdates == null) {
      log("All plugins up to date.");
      System.exit(0);
      return;
    }

    Set<String> filter = new HashSet<>(args.subList(1, args.size()));
    if (!filter.isEmpty()) {
      availableUpdates = ContainerUtil.filter(availableUpdates, downloader -> filter.contains(downloader.getId().getIdString()));
    }

    log("Plugins to update:");
    availableUpdates.forEach(d -> log("\t" + d.getPluginName()));

    if (UpdateInstaller.installPluginUpdates(availableUpdates, new EmptyProgressIndicator())) {
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
