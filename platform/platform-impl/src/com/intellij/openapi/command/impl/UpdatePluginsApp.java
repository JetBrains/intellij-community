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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.util.containers.ContainerUtil.newTroveSet;

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

  }

  @Override
  public void main(String[] args) {
    Set<String> pluginsToUpdate = getPluginsToUpdate(args);
    ActionCallback callback = new ActionCallback()
      .doWhenDone(() -> System.exit(0))
      .doWhenRejected(() -> System.exit(1));

    Runnable update = () -> updateAllPlugins(callback, pluginsToUpdate, getBuildNumber(args, callback));
    ApplicationManager.getApplication().executeOnPooledThread(update);
  }

  @Nullable
  private static Set<String> getPluginsToUpdate(String[] args) {
    if (args.length < 2) return null;
    //skip "update" command and read plugin ids
    final int fromIndex = "-b".equals(args[1]) ? 3 : 1;
    return newTroveSet(Arrays.asList(args).subList(fromIndex, args.length));
  }

  @NotNull
  private static BuildNumber getBuildNumber(final String[] args, final ActionCallback callback) {
    if (args.length > 2 && "-b".equals(args[1])) {
      final BuildNumber buildNumber = BuildNumber.fromString(args[2]);
      if (buildNumber != null) {
        return buildNumber;
      }
      else {
        log("Invalid build number: " + args[2]);
        callback.setRejected();
      }
    }
    return BuildNumber.currentVersion();
  }

  private static void updateAllPlugins(ActionCallback callback, @Nullable Set<String> plugins, BuildNumber buildNumber) {
    Collection<PluginDownloader> availableUpdates = UpdateChecker.checkPluginsUpdate(UpdateSettings.getInstance(),
                                                                                    new EmptyProgressIndicator(),
                                                                                    new HashSet<>(),
                                                                                    buildNumber);
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