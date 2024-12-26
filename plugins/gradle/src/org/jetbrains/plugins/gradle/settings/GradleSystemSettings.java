// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
@State(name = "GradleSystemSettings",
  category = SettingsCategory.TOOLS,
  exportable = true,
  storages = @Storage(value = "gradle.settings.xml", roamingType = RoamingType.DISABLED))
public class GradleSystemSettings implements PersistentStateComponent<GradleSystemSettings.MyState> {

  private @Nullable String myServiceDirectoryPath;
  private @Nullable String myGradleVmOptions;

  public static @NotNull GradleSystemSettings getInstance() {
    return ApplicationManager.getApplication().getService(GradleSystemSettings.class);
  }

  @Override
  public @Nullable GradleSystemSettings.MyState getState() {
    MyState state = new MyState();
    state.serviceDirectoryPath = myServiceDirectoryPath;
    state.gradleVmOptions = myGradleVmOptions;
    return state;
  }

  @Override
  public void loadState(@NotNull MyState state) {
    myServiceDirectoryPath = state.serviceDirectoryPath;
    myGradleVmOptions = state.gradleVmOptions;
  }

  /**
   * @deprecated use GradleSettings#getServiceDirectoryPath()
   */
  @Deprecated(forRemoval = true)
  public @Nullable String getServiceDirectoryPath() {
    return myServiceDirectoryPath;
  }

  /**
   * @deprecated use GradleSettings#setServiceDirectoryPath(java.lang.String)
   */
  @Deprecated(forRemoval = true)
  public void setServiceDirectoryPath(@Nullable String newPath) {
    myServiceDirectoryPath = newPath;
  }

  public @Nullable String getGradleVmOptions() {
    return myGradleVmOptions;
  }

  public void setGradleVmOptions(@Nullable String gradleVmOptions) {
    myGradleVmOptions = gradleVmOptions;
  }

  public boolean isDownloadSources() {
    return AdvancedSettings.getBoolean("gradle.download.sources");
  }

  public void setDownloadSources(boolean downloadSources) {
    AdvancedSettings.setBoolean("gradle.download.sources", downloadSources);
  }

  /**
   * @see GradleSettings#isOfflineWork
   * @deprecated this settings parameter must be a project level
   */
  @Deprecated(forRemoval = true)
  public boolean isOfflineWork() {
    return false;
  }

  public static class MyState {
    public String serviceDirectoryPath;
    public String gradleVmOptions;
  }
}