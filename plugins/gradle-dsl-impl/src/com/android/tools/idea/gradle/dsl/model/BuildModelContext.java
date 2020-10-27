/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model;

import com.android.tools.idea.gradle.dsl.GradleDslBuildScriptUtil;
import com.android.tools.idea.gradle.dsl.api.BuildModelNotification;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.model.notifications.NotificationTypeReference;
import com.android.tools.idea.gradle.dsl.parser.DependencyManager;
import com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement;
import com.android.tools.idea.gradle.dsl.parser.build.SubProjectsDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.files.*;
import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.MutableClassToInstanceMap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.gradle.dsl.GradleUtil.FN_GRADLE_PROPERTIES;
import static com.android.tools.idea.gradle.dsl.parser.build.SubProjectsDslElement.SUBPROJECTS;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;

/**
 * A context object used to hold information relevant to each unique instance of the project/build model.
 * This means there is one {@link BuildModelContext} for each call to the following methods,
 * {@link GradleBuildModel#parseBuildFile(VirtualFile, Project)}, {@link GradleBuildModel#get(Module)}
 * and {@link ProjectBuildModel#get(Project)}. This can be accessed from each of the {@link GradleDslFile}s.
 */
public final class BuildModelContext {

  public interface ResolvedConfigurationFileLocationProvider {
    @Nullable
    VirtualFile getGradleBuildFile(@NotNull Module module);

    @Nullable
    @SystemIndependent String getGradleProjectRootPath(@NotNull Module module);

    @Nullable
    @SystemIndependent String getGradleProjectRootPath(@NotNull Project project);
  }

  @NotNull
  private final Project myProject;
  @NotNull
  private final GradleDslFileCache myFileCache;
  @NotNull
  private final ResolvedConfigurationFileLocationProvider myResolvedConfigurationFileLocationProvider;
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
  public static BuildModelContext create(@NotNull Project project,
                                         @NotNull ResolvedConfigurationFileLocationProvider resolvedConfigurationFileLocationProvider) {
    return new BuildModelContext(project, resolvedConfigurationFileLocationProvider);
  }

  private BuildModelContext(@NotNull Project project,
                            @NotNull ResolvedConfigurationFileLocationProvider resolvedConfigurationFileLocationProvider) {
    myProject = project;
    myFileCache = new GradleDslFileCache(project);
    myResolvedConfigurationFileLocationProvider = resolvedConfigurationFileLocationProvider;
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

  @NotNull
  public GradleSettingsFile getOrCreateSettingsFile(@NotNull VirtualFile settingsFile) {
    return myFileCache.getOrCreateSettingsFile(settingsFile, this);
  }

  @Nullable
  public GradlePropertiesFile getOrCreatePropertiesFile(@NotNull VirtualFile file, @NotNull String moduleName) {
    return myFileCache.getOrCreatePropertiesFile(file, moduleName, this);
  }

  /**
   * Parses a build file and produces the {@link GradleBuildFile} that represents it.
   *
   * @param project    the project that the build file belongs to
   * @param file       the build file that should be parsed, this must be a gradle build file
   * @param moduleName the name of the module
   * @param isApplied  whether or not the file should be parsed as if it was applied, if true we do not populate the
   *                   file with the properties found in the subprojects block. This should be true for any file that is not part of the
   *                   main build.gradle structure (i.e project and module files) otherwise we might attempt to parse the file we are parsing
   *                   again leading to a stack overflow.
   * @return the model of the given Gradle file.
   */
  @NotNull
  public GradleBuildFile parseBuildFile(@NotNull Project project,
                                        @NotNull VirtualFile file,
                                        @NotNull String moduleName,
                                        boolean isApplied) {
    GradleBuildFile buildDslFile = new GradleBuildFile(file, project, moduleName, this);
    ApplicationManager.getApplication().runReadAction(() -> {
      if (!isApplied) {
        populateWithParentModuleSubProjectsProperties(buildDslFile);
      }
      populateSiblingDslFileWithGradlePropertiesFile(buildDslFile);
      buildDslFile.parse();
    });
    return buildDslFile;
  }

  public GradleBuildFile parseProjectBuildFile(@NotNull Project project, @Nullable VirtualFile file) {
    // First parse the main project build file.
    GradleBuildFile result = file != null ? new GradleBuildFile(file, project, ":", this) : null;
    if (result != null) {
      setRootProjectFile(result);
      ApplicationManager.getApplication().runReadAction(() -> {
        populateWithParentModuleSubProjectsProperties(result);
        populateSiblingDslFileWithGradlePropertiesFile(result);
        result.parse();
      });
      putBuildFile(file.getUrl(), result);
    }
    return result;
  }

  private void putBuildFile(@NotNull String name, @NotNull GradleDslFile buildFile) {
    myFileCache.putBuildFile(name, buildFile);
  }

  @NotNull
  public List<GradleDslFile> getAllRequestedFiles() {
    return myFileCache.getAllFiles();
  }

  private void populateSiblingDslFileWithGradlePropertiesFile(@NotNull GradleBuildFile buildDslFile) {
    File propertiesFilePath = new File(buildDslFile.getDirectoryPath(), FN_GRADLE_PROPERTIES);
    VirtualFile propertiesFile = findFileByIoFile(propertiesFilePath, false);
    if (propertiesFile == null) {
      return;
    }

    GradlePropertiesFile parsedProperties = getOrCreatePropertiesFile(propertiesFile, buildDslFile.getName());
    if (parsedProperties == null) {
      return;
    }
    GradlePropertiesModel propertiesModel = new GradlePropertiesModel(parsedProperties);

    GradleDslFile propertiesDslFile = propertiesModel.myGradleDslFile;
    buildDslFile.setSiblingDslFile(propertiesDslFile);
    propertiesDslFile.setSiblingDslFile(buildDslFile);
  }

  private void populateWithParentModuleSubProjectsProperties(@NotNull GradleBuildFile buildDslFile) {
    VirtualFile maybeSettingsFile = buildDslFile.tryToFindSettingsFile();
    if (maybeSettingsFile == null) {
      return;
    }
    GradleSettingsFile settingsFile = getOrCreateSettingsFile(maybeSettingsFile);

    GradleSettingsModel gradleSettingsModel = new GradleSettingsModelImpl(settingsFile);
    String modulePath = gradleSettingsModel.moduleWithDirectory(buildDslFile.getDirectoryPath());
    if (modulePath == null) {
      return;
    }

    GradleBuildModel parentModuleModel = gradleSettingsModel.getParentModuleModel(modulePath);
    if (!(parentModuleModel instanceof GradleBuildModelImpl)) {
      return;
    }

    GradleBuildModelImpl parentModuleModelImpl = (GradleBuildModelImpl)parentModuleModel;

    GradleDslFile parentModuleDslFile = parentModuleModelImpl.myGradleDslFile;
    buildDslFile.setParentModuleDslFile(parentModuleDslFile);

    SubProjectsDslElement subProjectsDslElement = parentModuleDslFile.getPropertyElement(SUBPROJECTS);
    if (subProjectsDslElement == null) {
      return;
    }

    buildDslFile.setParsedElement(subProjectsDslElement);
    for (Map.Entry<String, GradleDslElement> entry : subProjectsDslElement.getPropertyElements().entrySet()) {
      GradleDslElement element = entry.getValue();
      // TODO(b/147139838): we need to implement a sufficiently-deep copy to handle subprojects correctly: as it stands, this special-case
      //  shallow copy works around a particular bug, but e.g. configuring some android properties in a subprojects block, then
      //  modifying the configuration in one subproject will cause the parser to propagate that modification to all other subprojects
      //  because of the shared structure.  (The copy must not be too deep: we probably want to preserve the association of properties
      //  to the actual file they're defined in).
      if (element instanceof ApplyDslElement) {
        ApplyDslElement subProjectsApply = (ApplyDslElement)element;
        ApplyDslElement myApply = new ApplyDslElement(buildDslFile);
        buildDslFile.setParsedElement(myApply);
        for (GradleDslElement appliedElement : subProjectsApply.getAllElements()) {
          myApply.addParsedElement(appliedElement);
        }
      }
      else {
        // TODO(b/147139838): I believe this is wrong in general (see comment above, and the referenced bug, for details)
        buildDslFile.setParsedElement(element);
      }
    }
  }

  @Nullable
  public VirtualFile getGradleBuildFile(@NotNull Module module) {
    VirtualFile result = myResolvedConfigurationFileLocationProvider.getGradleBuildFile(module);
    if (result != null) return result;

    @SystemIndependent String rootPath = myResolvedConfigurationFileLocationProvider.getGradleProjectRootPath(module);
    if (rootPath == null) return null;
    File moduleRoot = new File(toSystemDependentName(rootPath));
    return getGradleBuildFile(moduleRoot);
  }

  /**
   * Returns the build.gradle file that is expected right in the directory at the given path. For example, if the directory path is
   * '~/myProject/myModule', this method will look for the file '~/myProject/myModule/build.gradle'. This method does not cause a VFS
   * refresh of the file, this should be done by the caller if it is likely that the file has just been created on disk.
   * <p>
   * <b>Note:</b> Only use this method if you do <b>not</b> have a reference to a {@link Module}. Otherwise use
   * {@link #getGradleBuildFile(Module)}.
   * </p>
   *
   * @param dirPath the given directory path.
   * @return the build.gradle file in the directory at the given path, or {@code null} if there is no build.gradle file in the given
   * directory path.
   */
  @Nullable
  public VirtualFile getGradleBuildFile(@NotNull File dirPath) {
    File gradleBuildFilePath = GradleDslBuildScriptUtil.findGradleBuildFile(dirPath);
    VirtualFile result = findFileByIoFile(gradleBuildFilePath, false);
    return (result != null && result.isValid()) ? result : null;
  }

  /**
   * Returns the VirtualFile corresponding to the Gradle settings file for the given directory, this method will not attempt to refresh the
   * file system which means it is safe to be called from a read action. If the most up to date information is needed then the caller
   * should use {@link BuildScriptUtil#findGradleSettingsFile(File)} along with
   * {@link com.intellij.openapi.vfs.VfsUtil#findFileByIoFile(File, boolean)}
   * to ensure a refresh occurs.
   *
   * @param dirPath the path to find the Gradle settings file for.
   * @return the VirtualFile representing the Gradle settings file or null if it was unable to be found or the file is invalid.
   */
  @Nullable
  public VirtualFile getGradleSettingsFile(@NotNull File dirPath) {
    File gradleSettingsFilePath = GradleDslBuildScriptUtil.findGradleSettingsFile(dirPath);
    VirtualFile result = findFileByIoFile(gradleSettingsFilePath, false);
    return (result != null && result.isValid()) ? result : null;
  }

  @Nullable
  public VirtualFile getProjectSettingsFile() {
    @SystemIndependent String rootPath = myResolvedConfigurationFileLocationProvider.getGradleProjectRootPath(getProject());
    if (rootPath == null) return null;
    return getGradleSettingsFile(new File(toSystemDependentName(rootPath)));
  }
}
