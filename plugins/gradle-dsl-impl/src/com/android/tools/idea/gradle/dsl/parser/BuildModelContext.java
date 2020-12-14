/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.parser;

import com.android.tools.idea.gradle.dsl.api.BuildModelNotification;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.model.notifications.NotificationTypeReference;
import com.android.tools.idea.gradle.dsl.parser.files.GradleBuildFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFileCache;
import com.android.tools.idea.gradle.dsl.parser.files.GradlePropertiesFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleSettingsFile;
import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.MutableClassToInstanceMap;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A context object used to hold information relevant to each unique instance of the project/build model.
 * This means there is one {@link BuildModelContext} for each call to the following methods,
 * {@link GradleBuildModel#parseBuildFile(VirtualFile, Project)}, {@link GradleBuildModel#get(Module)}
 * and {@link ProjectBuildModel#get(Project)}. This can be accessed from each of the {@link GradleDslFile}s.
 */
public final class BuildModelContext {
  @NotNull
  private final Project myProject;
  @NotNull
  private final GradleDslFileCache myFileCache;
  @NotNull
  private final Map<GradleDslFile, ClassToInstanceMap<BuildModelNotification>> myNotifications = new HashMap<>();
  @NotNull
  private final DependencyManager myDependencyManager;
  @Nullable
  private GradleDslFile myRootProjectFile;

  public void setRootProjectFile(@NotNull GradleDslFile rootProjectFile) {
    myRootProjectFile = rootProjectFile;
  }

  @Nullable
  public GradleDslFile getRootProjectFile() {
    return myRootProjectFile;
  }

  @NotNull
  public static BuildModelContext create(@NotNull Project project) {
    return new BuildModelContext(project);
  }

  private BuildModelContext(@NotNull Project project) {
    myProject = project;
    myFileCache = new GradleDslFileCache(project);
    myDependencyManager = DependencyManager.create();
    myRootProjectFile = null;
  }

  @NotNull
  public DependencyManager getDependencyManager() {
    return myDependencyManager;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public List<BuildModelNotification> getPublicNotifications(@NotNull GradleDslFile file) {
    return new ArrayList<>(myNotifications.getOrDefault(file, MutableClassToInstanceMap.create()).values());
  }

  @NotNull
  public <T extends BuildModelNotification> T getNotificationForType(@NotNull GradleDslFile file,
                                                                     @NotNull NotificationTypeReference<T> type) {
    ClassToInstanceMap<BuildModelNotification> notificationMap =
      myNotifications.computeIfAbsent(file, (f) -> MutableClassToInstanceMap.create());
    if (notificationMap.containsKey(type.getClazz())) {
      return notificationMap.getInstance(type.getClazz());
    }
    else {
      T notification = type.getConstructor().produce();
      notificationMap.putInstance(type.getClazz(), notification);
      return notification;
    }
  }

  @Nullable
  public VirtualFile getCurrentParsingRoot() {
    return myFileCache.getCurrentParsingRoot();
  }

  /**
   * Resets the state of the build context.
   */
  public void reset() {
    myFileCache.clearAllFiles();
  }

  /* The following methods are just wrappers around the same methods in GradleDslFileCache but pass this build
   * context along as well. */
  @NotNull
  public GradleBuildFile getOrCreateBuildFile(@NotNull VirtualFile file, @NotNull String name, boolean isApplied) {
    return myFileCache.getOrCreateBuildFile(file, name, this, isApplied);
  }

  @NotNull
  public GradleBuildFile getOrCreateBuildFile(@NotNull VirtualFile file, boolean isApplied) {
    return getOrCreateBuildFile(file, file.getName(), isApplied);
  }

  @Nullable
  public GradleSettingsFile getSettingsFile(@NotNull Project project) {
    return myFileCache.getSettingsFile(project);
  }

  @NotNull
  public GradleSettingsFile getOrCreateSettingsFile(@NotNull VirtualFile settingsFile) {
    return myFileCache.getOrCreateSettingsFile(settingsFile, this);
  }

  @Nullable
  public GradlePropertiesFile getOrCreatePropertiesFile(@NotNull VirtualFile file, @NotNull String moduleName) {
    return myFileCache.getOrCreatePropertiesFile(file, moduleName, this);
  }

  // This should normally not be used. Please use getOrCreateBuildFile
  public void putBuildFile(@NotNull String name, @NotNull GradleDslFile buildFile) {
    myFileCache.putBuildFile(name, buildFile);
  }

  @NotNull
  public List<GradleDslFile> getAllRequestedFiles() {
    return myFileCache.getAllFiles();
  }
}
