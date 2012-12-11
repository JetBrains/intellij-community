/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.config;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holds shared project-level gradle-related settings (should be kept at the '*.ipr' or under '.idea').
 * 
 * @author peter
 */
@State(
    name = "GradleSettings",
    storages = {
      @Storage(file = StoragePathMacros.PROJECT_FILE),
      @Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/gradle.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public class GradleSettings implements PersistentStateComponent<GradleSettings> {

  /** Holds information about 'expand/collapse' status of the 'sync project structure tree' nodes. */
  //private final AtomicReference<Set<GradleProjectStructureChange>>             myAcceptedChanges
  //  = new AtomicReference<Set<GradleProjectStructureChange>>();

  private String  myLinkedProjectPath;
  private String  myGradleHome;
  private String  myServiceDirectoryPath;
  private boolean myPreferLocalInstallationToWrapper;

  @Override
  public GradleSettings getState() {
    return this;
  }

  @Override
  public void loadState(GradleSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @NotNull
  public static GradleSettings getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleSettings.class);
  }

  @Nullable
  public String getGradleHome() {
    return myGradleHome;
  }

  @SuppressWarnings("UnusedDeclaration")
  public void setGradleHome(@Nullable String gradleHome) { // Necessary for the serialization.
    myGradleHome = gradleHome;
  }

  public static void applyGradleHome(@Nullable String newPath, @NotNull Project project) {
    final GradleSettings settings = getInstance(project);
    final String oldPath = settings.myGradleHome;
    if (!Comparing.equal(oldPath, newPath)) {
      settings.myGradleHome = newPath;
      project.getMessageBus().syncPublisher(GradleConfigNotifier.TOPIC).onGradleHomeChange(oldPath, newPath);
    }
  }

  @Nullable
  public String getLinkedProjectPath() {
    return myLinkedProjectPath;
  }

  @SuppressWarnings("UnusedDeclaration")
  public void setLinkedProjectPath(@Nullable String linkedProjectPath) { // Necessary for the serialization.
    myLinkedProjectPath = linkedProjectPath;
  }

  public static void applyLinkedProjectPath(@Nullable String path, @NotNull Project project) {
    final GradleSettings settings = getInstance(project);
    final String oldPath = settings.myLinkedProjectPath;
    if (!Comparing.equal(oldPath, path)) {
      settings.myLinkedProjectPath = path;
      project.getMessageBus().syncPublisher(GradleConfigNotifier.TOPIC).onLinkedProjectPathChange(oldPath, path);
    }
  }

  public boolean isPreferLocalInstallationToWrapper() {
    return myPreferLocalInstallationToWrapper;
  }

  public static void applyPreferLocalInstallationToWrapper(boolean preferLocal, @NotNull Project project) {
    final GradleSettings settings = getInstance(project);
    boolean oldValue = settings.isPreferLocalInstallationToWrapper();
    if (oldValue != preferLocal) {
      settings.setPreferLocalInstallationToWrapper(preferLocal);
      project.getMessageBus().syncPublisher(GradleConfigNotifier.TOPIC).onPreferLocalGradleDistributionToWrapperChange(preferLocal);
    }
  }
  
  public void setPreferLocalInstallationToWrapper(boolean preferLocalInstallationToWrapper) {
    myPreferLocalInstallationToWrapper = preferLocalInstallationToWrapper;
  }

  /**
   * @return    service directory path (if defined). 'Service directory' is a directory which is used internally by gradle during
   *            calls to the tooling api. E.g. it holds downloaded binaries (dependency jars). We allow to define it because there
   *            is a possible situation when a user wants to configure particular directory to be excluded from anti-virus protection
   *            in order to increase performance
   */
  @Nullable
  public String getServiceDirectoryPath() {
    return myServiceDirectoryPath;
  }

  public void setServiceDirectoryPath(@Nullable String serviceDirectoryPath) {
    myServiceDirectoryPath = serviceDirectoryPath;
  }

  public static void applyServiceDirectoryPath(@Nullable String path, @NotNull Project project) {
    final GradleSettings settings = getInstance(project);
    final String oldPath = settings.getServiceDirectoryPath();
    if (!Comparing.equal(oldPath, path)) {
      settings.setServiceDirectoryPath(path);
      project.getMessageBus().syncPublisher(GradleConfigNotifier.TOPIC).onServiceDirectoryPathChange(oldPath, path);
    }
  }

  public static void applySettings(@Nullable String linkedProjectPath,
                                   @Nullable String gradleHomePath,
                                   boolean preferLocalInstallationToWrapper,
                                   @Nullable String serviceDirectoryPath,
                                   @NotNull Project project)
  {
    GradleConfigNotifier notifier = project.getMessageBus().syncPublisher(GradleConfigNotifier.TOPIC);
    notifier.onBulkChangeStart();
    try {
      applyLinkedProjectPath(linkedProjectPath, project);
      applyGradleHome(gradleHomePath, project);
      applyPreferLocalInstallationToWrapper(preferLocalInstallationToWrapper, project);
      applyServiceDirectoryPath(serviceDirectoryPath, project);
    }
    finally {
      notifier.onBulkChangeEnd();
    }
  }

  @Override
  public String toString() {
    return "home: " + myGradleHome + ", path: " + myLinkedProjectPath;
  }
}