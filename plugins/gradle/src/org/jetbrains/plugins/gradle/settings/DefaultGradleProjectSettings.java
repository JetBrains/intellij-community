// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.settings;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.settings.GradleSettingsService;

/**
 * {@link DefaultGradleProjectSettings} holds IDE project level settings defaults for gradle projects.
 *
 * @see GradleSettingsService
 *
 * @author Vladislav.Soroka
 */
@State(name = "DefaultGradleProjectSettings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class DefaultGradleProjectSettings implements PersistentStateComponent<DefaultGradleProjectSettings.MyState> {
  private boolean myMigrated;
  private boolean myDelegatedBuild = true;
  @NotNull private TestRunner myTestRunner = TestRunner.GRADLE;

  @NotNull
  public TestRunner getTestRunner() {
    return myTestRunner;
  }

  void setTestRunner(@NotNull TestRunner testRunner) {
    myTestRunner = testRunner;
  }

  public boolean isDelegatedBuild() {
    return myDelegatedBuild;
  }

  public void setDelegatedBuild(boolean delegatedBuild) {
    myDelegatedBuild = delegatedBuild;
  }

  @ApiStatus.ScheduledForRemoval(inVersion = "2019.2")
  boolean isMigrated() {
    return myMigrated;
  }

  @ApiStatus.ScheduledForRemoval(inVersion = "2019.2")
  void setMigrated(boolean migrated) {
    myMigrated = migrated;
  }

  @Nullable
  @Override
  public DefaultGradleProjectSettings.MyState getState() {
    MyState state = new MyState();
    state.delegatedBuild = myDelegatedBuild;
    state.testRunner = myTestRunner;
    state.isMigrated = myMigrated;
    return state;
  }

  @Override
  public void loadState(@NotNull MyState state) {
    if (!state.isMigrated) {
      migrateOldSettings();
    }
    else {
      myDelegatedBuild = state.delegatedBuild;
      myTestRunner = state.testRunner;
    }
    myMigrated = true;
  }

  @SuppressWarnings("deprecation")
  private void migrateOldSettings() {
    GradleSystemRunningSettings oldAppSettings = GradleSystemRunningSettings.getInstance();
    myDelegatedBuild = oldAppSettings.isUseGradleAwareMake();
    GradleSystemRunningSettings.PreferredTestRunner oldTestRunner = oldAppSettings.getDefaultTestRunner();
    if (oldTestRunner == GradleSystemRunningSettings.PreferredTestRunner.PLATFORM_TEST_RUNNER) {
      myTestRunner = TestRunner.PLATFORM;
    }
    else if (oldTestRunner == GradleSystemRunningSettings.PreferredTestRunner.GRADLE_TEST_RUNNER) {
      myTestRunner = TestRunner.GRADLE;
    }
    else if (oldTestRunner == GradleSystemRunningSettings.PreferredTestRunner.CHOOSE_PER_TEST) {
      myTestRunner = TestRunner.CHOOSE_PER_TEST;
    }
  }

  @NotNull
  public static DefaultGradleProjectSettings getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, DefaultGradleProjectSettings.class);
  }

  /**
   * Do not use the class directly. Consider to use {@link GradleSettingsService} or {@link GradleProjectSettings}
   */
  @ApiStatus.Experimental
  public static class MyState {
    public TestRunner testRunner = TestRunner.PLATFORM;
    public boolean delegatedBuild;
    /**
     * @deprecated Do not use. Only for settings migration purposes.
     */
    @Deprecated
    @ApiStatus.ScheduledForRemoval(inVersion = "2019.2")
    public boolean isMigrated;
  }
}