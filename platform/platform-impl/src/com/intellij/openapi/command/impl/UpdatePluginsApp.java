/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.command.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationStarterEx;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.updateSettings.impl.UpdateInstaller;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.BuildNumber;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

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
    List<String> pluginsToUpdate = getPluginsToUpdate(args);
      ActionCallback callback = new ActionCallback()
        .doWhenDone(() -> System.exit(0))
        .doWhenRejected(() -> System.exit(1));

      Runnable update = () -> updateAllPlugins(callback, pluginsToUpdate);
      ApplicationManager.getApplication().executeOnPooledThread(update);
  }

  @Nullable
  private static List<String> getPluginsToUpdate(String[] args) {
    if (args.length < 2) return null;
    ArrayList<String> pluginIds = new ArrayList<>(args.length - 1);
    //skip "update" command and read plugin ids
    for (int i = 1; i < args.length; i++) {
      pluginIds.add(i - 1, args[i]);
    }
    return pluginIds;
  }

  private static void updateAllPlugins(ActionCallback callback, @Nullable List<String> plugins) {
    Collection<PluginDownloader> availableUpdates = UpdateChecker.checkPluginsUpdate(UpdateSettings.getInstance(),
                                                                                    new EmptyProgressIndicator(),
                                                                                    new HashSet<>(),
                                                                                    BuildNumber.currentVersion());
    if (availableUpdates == null) {
      log("All plugins up to date.");
      callback.setDone();
      return;
    }

    Collection<PluginDownloader> pluginsToUpdate = plugins == null || plugins.isEmpty()
                                                   ? availableUpdates
                                                   : availableUpdates.stream()
                                                     .filter(downloader -> plugins.contains(downloader.getPluginId()))
                                                     .collect(Collectors.toList());


    log("Plugins to update:");
    pluginsToUpdate.forEach(d -> log("\t" + d.getPluginName()));

    if (UpdateInstaller.installPluginUpdates(pluginsToUpdate, new EmptyProgressIndicator())) {
      callback.setDone();
    } else {
      log("Update failed");
      callback.setRejected();
    }
  }

  private static void log(String msg) {
    System.out.println(msg);
  }
}