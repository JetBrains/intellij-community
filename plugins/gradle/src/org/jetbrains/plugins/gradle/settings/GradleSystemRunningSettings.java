/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
 * @since 14/8/2014
 */
@State(name = "GradleSystemRunningSettings", storages = @Storage("gradle.run.settings.xml"))
public class GradleSystemRunningSettings implements PersistentStateComponent<GradleSystemRunningSettings.MyState> {
  private boolean myUseGradleAwareMake;
  @NotNull private PreferredTestRunner myPreferredTestRunner = PreferredTestRunner.PLATFORM_TEST_RUNNER;

  @NotNull
  public static GradleSystemRunningSettings getInstance() {
    return ServiceManager.getService(GradleSystemRunningSettings.class);
  }

  @SuppressWarnings("unchecked")
  @Nullable
  @Override
  public GradleSystemRunningSettings.MyState getState() {
    MyState state = new MyState();
    state.useGradleAwareMake = myUseGradleAwareMake;
    state.preferredTestRunner = myPreferredTestRunner;
    return state;
  }

  @Override
  public void loadState(MyState state) {
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