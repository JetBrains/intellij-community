/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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