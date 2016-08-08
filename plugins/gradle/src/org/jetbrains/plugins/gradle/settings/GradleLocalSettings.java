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

import com.intellij.openapi.components.*;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Denis Zhdanov
 * @since 5/3/12 6:16 PM
 */
@State(name = "GradleLocalSettings", storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)} )
public class GradleLocalSettings extends AbstractExternalSystemLocalSettings
  implements PersistentStateComponent<GradleLocalSettings.MyState>
{
  private final AtomicReference<Map<String/* external project path */, String>> myGradleHomes =
    new AtomicReference<>(ContainerUtilRt.newHashMap());
  private final AtomicReference<Map<String/* external project path */, String>> myGradleVersions =
    new AtomicReference<>(ContainerUtilRt.newHashMap());

  public GradleLocalSettings(@NotNull Project project) {
    super(GradleConstants.SYSTEM_ID, project);
  }

  @NotNull
  public static GradleLocalSettings getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleLocalSettings.class);
  }

  @Nullable
  public String getGradleHome(String linkedProjectPath) {
    return myGradleHomes.get().get(linkedProjectPath);
  }

  @Nullable
  public String getGradleVersion(String linkedProjectPath) {
    return myGradleVersions.get().get(linkedProjectPath);
  }

  public void setGradleHome(@NotNull String linkedProjectPath, @NotNull String gradleHome) {
    myGradleHomes.get().put(linkedProjectPath, gradleHome);
    myGradleVersions.get().put(linkedProjectPath, GradleInstallationManager.getGradleVersion(gradleHome));
  }

  @Override
  public void forgetExternalProjects(@NotNull Set<String> linkedProjectPathsToForget) {
    super.forgetExternalProjects(linkedProjectPathsToForget);
    for (String path : linkedProjectPathsToForget) {
      myGradleHomes.get().remove(path);
      myGradleVersions.get().remove(path);
    }
  }

  @Nullable
  @Override
  public MyState getState() {
    MyState state = new MyState();
    fillState(state);
    state.myGradleHomes = myGradleHomes.get();
    state.myGradleVersions = myGradleVersions.get();
    return state;
  }

  @Override
  public void loadState(@NotNull MyState state) {
    super.loadState(state);
    setIfNotNull(myGradleHomes, state.myGradleHomes);
    setIfNotNull(myGradleVersions, state.myGradleVersions);
  }

  public static class MyState extends AbstractExternalSystemLocalSettings.State {
    public Map<String/* project path */, String> myGradleHomes = ContainerUtilRt.newHashMap();
    public Map<String/* project path */, String> myGradleVersions = ContainerUtilRt.newHashMap();
  }
}
