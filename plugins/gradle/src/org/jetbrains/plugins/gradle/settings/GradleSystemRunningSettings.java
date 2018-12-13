// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.module.Module;
import com.intellij.util.xmlb.annotations.OptionTag;
import org.gradle.internal.impldep.com.google.common.base.Objects;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.settings.GradleSettingsService;

/**
 * @author Vladislav.Soroka
 *
 * @deprecated use {@link GradleSettingsService}
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2019.2")
@State(name = "GradleSystemRunningSettings", storages = @Storage(value = "gradle.run.settings.xml", deprecated = true))
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

  @OptionTag("preferredTestRunner")
  @NotNull
  public PreferredTestRunner getDefaultTestRunner() {
    return myPreferredTestRunner;
  }

  /**
   * @deprecated use {@link GradleSettingsService#isDelegatedBuildEnabled(Module)} )
   */
  @Deprecated
  public boolean isUseGradleAwareMake() {
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