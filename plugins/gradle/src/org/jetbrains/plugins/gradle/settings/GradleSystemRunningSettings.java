// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.gradle.internal.impldep.com.google.common.base.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
@State(name = "GradleSystemRunningSettings", storages = @Storage("gradle.run.settings.xml"))
public class GradleSystemRunningSettings implements PersistentStateComponent<GradleSystemRunningSettings.MyState> {
  private boolean myUseGradleAwareMake;
  @NotNull private PreferredTestRunner myPreferredTestRunner = PreferredTestRunner.PLATFORM_TEST_RUNNER;

  @NotNull
  public static GradleSystemRunningSettings getInstance() {
    return ServiceManager.getService(GradleSystemRunningSettings.class);
  }

  @Nullable
  @Override
  public GradleSystemRunningSettings.MyState getState() {
    MyState state = new MyState();
    state.useGradleAwareMake = myUseGradleAwareMake;
    state.preferredTestRunner = myPreferredTestRunner;
    return state;
  }

  @Override
  public void loadState(@NotNull MyState state) {
    myUseGradleAwareMake = state.useGradleAwareMake;
    myPreferredTestRunner = state.preferredTestRunner;
  }

  @NotNull
  public PreferredTestRunner getPreferredTestRunner() {
    return myPreferredTestRunner;
  }

  @NotNull
  PreferredTestRunner getLastPreferredTestRunner() {
    return myPreferredTestRunner;
  }

  public void setPreferredTestRunner(@NotNull PreferredTestRunner preferredTestRunner) {
    myPreferredTestRunner = preferredTestRunner;
  }

  public boolean isUseGradleAwareMake() {
    return myUseGradleAwareMake;
  }

  public void setUseGradleAwareMake(boolean useGradleAwareMake) {
    this.myUseGradleAwareMake = useGradleAwareMake;
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
    return myUseGradleAwareMake == settings.myUseGradleAwareMake &&
           myPreferredTestRunner == settings.myPreferredTestRunner;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myPreferredTestRunner, myUseGradleAwareMake);
  }

  public enum PreferredTestRunner {
    PLATFORM_TEST_RUNNER, GRADLE_TEST_RUNNER, CHOOSE_PER_TEST
  }
}