// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.settings;

import com.intellij.openapi.components.*;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service(Service.Level.PROJECT)
@State(name = "GradleLocalSettings", storages = @Storage(StoragePathMacros.CACHE_FILE))
public final class GradleLocalSettings extends AbstractExternalSystemLocalSettings<GradleLocalSettings.MyState>
  implements PersistentStateComponent<GradleLocalSettings.MyState> {

  public GradleLocalSettings(@NotNull Project project) {
    super(GradleConstants.SYSTEM_ID, project, new MyState());
  }

  public static @NotNull GradleLocalSettings getInstance(@NotNull Project project) {
    return project.getService(GradleLocalSettings.class);
  }

  public @Nullable String getGradleHome(String linkedProjectPath) {
    return ContainerUtil.notNullize(state.myGradleHomes).get(linkedProjectPath);
  }

  public @Nullable String getGradleVersion(String linkedProjectPath) {
    return ContainerUtil.notNullize(state.myGradleVersions).get(linkedProjectPath);
  }

  public void setGradleHome(@NotNull String linkedProjectPath, @NotNull String gradleHome) {
    if (state.myGradleHomes == null) {
      state.myGradleHomes = new HashMap<>();
    }
    state.myGradleHomes.put(linkedProjectPath, gradleHome);
    if (state.myGradleVersions == null) {
      state.myGradleVersions = new HashMap<>();
    }
    state.myGradleVersions.put(linkedProjectPath, GradleInstallationManager.getGradleVersion(Path.of(gradleHome)));
  }

  @ApiStatus.Internal
  public @Nullable String getGradleUserHome() {
    return state.myGradleUserHome;
  }

  @ApiStatus.Internal
  public void setGradleUserHome(@Nullable String gradleUserHome) {
    state.myGradleUserHome = gradleUserHome;
    // --- to be removed with the removal of GradleSystemSettings#getServiceDirectoryPath method ---
    //noinspection deprecation
    GradleSystemSettings.getInstance().setServiceDirectoryPath(gradleUserHome);
    // ----
  }

  @Override
  public void forgetExternalProjects(@NotNull Set<String> linkedProjectPathsToForget) {
    super.forgetExternalProjects(linkedProjectPathsToForget);
    for (String path : linkedProjectPathsToForget) {
      if (state.myGradleHomes != null) {
        state.myGradleHomes.remove(path);
      }
      if (state.myGradleVersions != null) {
        state.myGradleVersions.remove(path);
      }
    }
  }

  @Override
  public void loadState(@NotNull MyState state) {
    super.loadState(state);
    // --- to be removed with the removal of GradleSystemSettings#getServiceDirectoryPath method ---
    //noinspection deprecation
    String serviceDirectoryPath = GradleSystemSettings.getInstance().getServiceDirectoryPath();
    if (state.myGradleUserHome == null && serviceDirectoryPath != null) {
      state.myGradleUserHome = serviceDirectoryPath;
    }
    // ----
  }

  @Override
  public void invalidateCaches() {
    super.invalidateCaches();
    if (state.myGradleHomes != null) {
      state.myGradleHomes.clear();
    }
    if (state.myGradleVersions != null) {
      state.myGradleVersions.clear();
    }
  }

  public static class MyState extends AbstractExternalSystemLocalSettings.State {
    public String myGradleUserHome;
    public Map<String/* project path */, String> myGradleHomes;
    public Map<String/* project path */, String> myGradleVersions;
  }
}
