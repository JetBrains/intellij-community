// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.annotations.OptionTag;
import org.gradle.internal.impldep.com.google.common.base.Objects;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
@State(name = "GradleSystemRunningSettings", storages = @Storage("gradle.run.settings.xml"))
public class GradleSystemRunningSettings implements PersistentStateComponent<GradleSystemRunningSettings.MyState> {
  private boolean myDelegatedBuildEnabledByDefault;
  @NotNull private PreferredTestRunner myPreferredTestRunner = PreferredTestRunner.PLATFORM_TEST_RUNNER;

  @NotNull
  public static GradleSystemRunningSettings getInstance() {
    return ServiceManager.getService(GradleSystemRunningSettings.class);
  }

  @Nullable
  @Override
  public GradleSystemRunningSettings.MyState getState() {
    MyState state = new MyState();
    state.useGradleAwareMake = myDelegatedBuildEnabledByDefault;
    state.preferredTestRunner = myPreferredTestRunner;
    return state;
  }

  @Override
  public void loadState(@NotNull MyState state) {
    myDelegatedBuildEnabledByDefault = state.useGradleAwareMake;
    myPreferredTestRunner = state.preferredTestRunner;
  }

  @NotNull
  public PreferredTestRunner getTestRunner(@NotNull Module module) {
    String projectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module);
    if (projectPath == null) return myPreferredTestRunner;
    return getTestRunner(module.getProject(), projectPath);
  }

  @NotNull
  public PreferredTestRunner getTestRunner(@NotNull Project project, @NotNull String gradleProjectPath) {
    GradleProjectSettings projectSettings = GradleSettings.getInstance(project).getLinkedProjectSettings(gradleProjectPath);
    return projectSettings == null ? myPreferredTestRunner : projectSettings.getEffectiveTestRunner();
  }

  @OptionTag("preferredTestRunner")
  @NotNull
  public PreferredTestRunner getDefaultTestRunner() {
    return myPreferredTestRunner;
  }

  void setDefaultTestRunner(@NotNull PreferredTestRunner preferredTestRunner) {
    myPreferredTestRunner = preferredTestRunner;
  }

  public boolean isDelegatedBuildEnabled(@NotNull Module module) {
    String projectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module);
    if (projectPath == null) return false;
    return isDelegatedBuildEnabled(module.getProject(), projectPath);
  }

  public boolean isDelegatedBuildEnabled(@NotNull Project project, @NotNull String gradleProjectPath) {
    GradleProjectSettings projectSettings = GradleSettings.getInstance(project).getLinkedProjectSettings(gradleProjectPath);
    if (projectSettings == null) return false;
    return projectSettings.getEffectiveDelegateBuild().toBoolean();
  }

  /**
   * @deprecated use {@link #isDelegatedBuildEnabled(Module)} or {@link #isDelegatedBuildEnabled(Project, String)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2019.1")
  public boolean isUseGradleAwareMake() {
    return myDelegatedBuildEnabledByDefault;
  }

  void setDelegatedBuildEnabledByDefault(boolean delegatedBuildEnabledByDefault) {
    this.myDelegatedBuildEnabledByDefault = delegatedBuildEnabledByDefault;
  }

  @OptionTag("useGradleAwareMake")
  public boolean isDelegatedBuildEnabledByDefault() {
    return myDelegatedBuildEnabledByDefault;
  }

  public static class MyState {
    public PreferredTestRunner preferredTestRunner = PreferredTestRunner.PLATFORM_TEST_RUNNER;
    public boolean useGradleAwareMake;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GradleSystemRunningSettings settings = (GradleSystemRunningSettings)o;
    return myDelegatedBuildEnabledByDefault == settings.myDelegatedBuildEnabledByDefault &&
           myPreferredTestRunner == settings.myPreferredTestRunner;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myPreferredTestRunner, myDelegatedBuildEnabledByDefault);
  }

  public enum PreferredTestRunner {
    PLATFORM_TEST_RUNNER, GRADLE_TEST_RUNNER, CHOOSE_PER_TEST
  }
}