// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public abstract class UpdateResultCallback {
  @NotNull final private CheckForUpdateResult myCheckForUpdateResult;
  @Nullable final private Collection<PluginDownloader> myUpdatedPlugins;
  @Nullable final private Collection<IdeaPluginDescriptor> myIncompatiblePlugins;
  @NotNull final private UpdateSettings myUpdateSettings;
  final private boolean myEnableLink;

  /**
   * @param checkForUpdateResult the result of checkPlatformUpdate
   * @param updatedPlugins the result of checkPluginsUpdate
   * @param incompatiblePlugins collection of incompatible plugins from checkPluginsUpdate
   * @param updateSettings customized or default UpdateSettings
   * @param enableLink whether to show the "Configure automatic updates" link in update dialog
   */
  public UpdateResultCallback(@NotNull CheckForUpdateResult checkForUpdateResult,
                              @Nullable Collection<PluginDownloader> updatedPlugins,
                              @Nullable Collection<IdeaPluginDescriptor> incompatiblePlugins,
                              @NotNull UpdateSettings updateSettings,
                              boolean enableLink) {
    myCheckForUpdateResult = checkForUpdateResult;
    myUpdatedPlugins = updatedPlugins;
    myIncompatiblePlugins = incompatiblePlugins;
    myUpdateSettings = updateSettings;
    myEnableLink = enableLink;
  }

  /**
   * Subclasses implement this method for desired behavior after all the update checks.
   */
  public abstract void run();

  @NotNull
  public CheckForUpdateResult getCheckForUpdateResult() {
    return myCheckForUpdateResult;
  }

  @Nullable
  public Collection<PluginDownloader> getUpdatedPlugins() {
    return myUpdatedPlugins;
  }

  @Nullable
  public Collection<IdeaPluginDescriptor> getIncompatiblePlugins() {
    return myIncompatiblePlugins;
  }

  @NotNull
  public UpdateSettings getUpdateSettings() {
    return myUpdateSettings;
  }

  public boolean shouldEnableLink() {
    return myEnableLink;
  }
}