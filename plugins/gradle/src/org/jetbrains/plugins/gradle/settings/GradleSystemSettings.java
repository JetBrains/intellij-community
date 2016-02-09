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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 * @since 14/8/2014
 */
@State(name = "GradleSystemSettings", storages = @Storage("gradle.settings.xml"))
public class GradleSystemSettings implements PersistentStateComponent<GradleSystemSettings.MyState> {

  @Nullable private String myServiceDirectoryPath;
  @Nullable private String myGradleVmOptions;
  private boolean myIsOfflineWork;

  @NotNull
  public static GradleSystemSettings getInstance() {
    return ServiceManager.getService(GradleSystemSettings.class);
  }

  @SuppressWarnings("unchecked")
  @Nullable
  @Override
  public GradleSystemSettings.MyState getState() {
    MyState state = new MyState();
    state.serviceDirectoryPath = myServiceDirectoryPath;
    state.gradleVmOptions = myGradleVmOptions;
    state.offlineWork = myIsOfflineWork;
    return state;
  }

  @Override
  public void loadState(MyState state) {
    myServiceDirectoryPath = state.serviceDirectoryPath;
    myGradleVmOptions = state.gradleVmOptions;
    myIsOfflineWork = state.offlineWork;
  }

  @Nullable
  public String getServiceDirectoryPath() {
    return myServiceDirectoryPath;
  }

  public void setServiceDirectoryPath(@Nullable String newPath) {
    myServiceDirectoryPath = newPath;
  }

  @Nullable
  public String getGradleVmOptions() {
    return myGradleVmOptions;
  }

  public void setGradleVmOptions(@Nullable String gradleVmOptions) {
    myGradleVmOptions = gradleVmOptions;
  }

  public boolean isOfflineWork() {
    return myIsOfflineWork;
  }

  public void setOfflineWork(boolean isOfflineWork) {
    myIsOfflineWork = isOfflineWork;
  }

  public static class MyState {
    public String serviceDirectoryPath;
    public String gradleVmOptions;
    public boolean offlineWork;
  }
}