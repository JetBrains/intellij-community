// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.application.ApplicationStarterEx;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.updateSettings.impl.UpdateInstaller;
import com.intellij.util.containers.ContainerUtil;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Konstantin Bulenkov
 */
public class UpdatePluginsApp extends ApplicationStarterEx {
  @Override
  public boolean isHeadless() {
    return true;
  }

  @Override
  public String getCommandName() {
    return "update";
  }

  @Override
  public void premain(String[] args) {
    System.setProperty("idea.skip.indices.initialization", "true");
  }

  @Override
  public void main(String[] args) {
    Collection<PluginDownloader> availableUpdates = UpdateChecker.getPluginUpdates();
    if (availableUpdates == null) {
      log("All plugins up to date.");
      System.exit(0);
      return;
    }

    Set<String> filter = Stream.of(args).skip(1).collect(Collectors.toSet());
    if (!filter.isEmpty()) {
      availableUpdates = ContainerUtil.filter(availableUpdates, downloader -> filter.contains(downloader.getPluginId()));
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