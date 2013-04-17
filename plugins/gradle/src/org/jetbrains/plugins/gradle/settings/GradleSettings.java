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
package org.jetbrains.plugins.gradle.settings;

import com.intellij.openapi.components.*;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
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
public class GradleSettings extends AbstractExternalSystemSettings<GradleSettingsListener, GradleSettings>
  implements PersistentStateComponent<GradleSettings.MyState>
{

  private String  myGradleHome;
  private String  myServiceDirectoryPath;
  private boolean myPreferLocalInstallationToWrapper;
  
  public GradleSettings(@NotNull Project project) {
    super(GradleSettingsListener.TOPIC, project);
  }

  @NotNull
  public static GradleSettings getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleSettings.class);
  }

  @SuppressWarnings("unchecked")
  @Nullable
  @Override
  public GradleSettings.MyState getState() {
    MyState state = new MyState();
    fillState(state);
    state.gradleHome = myGradleHome;
    state.serviceDirectoryPath = myServiceDirectoryPath;
    state.preferLocalInstallationToWrapper = myPreferLocalInstallationToWrapper;
    return state;
  }

  @Override
  public void loadState(MyState state) {
    super.loadState(state);
    myGradleHome = state.gradleHome;
    myServiceDirectoryPath = state.serviceDirectoryPath;
    myPreferLocalInstallationToWrapper = state.preferLocalInstallationToWrapper;
  }
  
  @Nullable
  public String getGradleHome() {
    return myGradleHome;
  }

  @SuppressWarnings("UnusedDeclaration")
  public void setGradleHome(@Nullable String gradleHome) {
    if (!Comparing.equal(myGradleHome, gradleHome)) {
      final String oldHome = myGradleHome;
      myGradleHome = gradleHome;
      getPublisher().onGradleHomeChange(oldHome, myGradleHome);
    }
  }

  public boolean isPreferLocalInstallationToWrapper() {
    return myPreferLocalInstallationToWrapper;
  }

  public void setPreferLocalInstallationToWrapper(boolean preferLocalInstallationToWrapper) {
    if (myPreferLocalInstallationToWrapper != preferLocalInstallationToWrapper) {
      myPreferLocalInstallationToWrapper = preferLocalInstallationToWrapper;
      getPublisher().onPreferLocalGradleDistributionToWrapperChange(preferLocalInstallationToWrapper);
    }
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

  public void setServiceDirectoryPath(@Nullable String path) {
    if (!Comparing.equal(myServiceDirectoryPath, path)) {
      final String oldPath = myServiceDirectoryPath;
      getPublisher().onServiceDirectoryPathChange(oldPath, path);
    } 
  }

  public void applySettings(@Nullable String linkedProjectPath,
                            @Nullable String gradleHomePath,
                            boolean preferLocalInstallationToWrapper,
                            boolean useAutoImport,
                            @Nullable String serviceDirectoryPath)
  {
    
    GradleSettingsListener publisher = getPublisher();
    publisher.onBulkChangeStart();
    try {
      setLinkedExternalProjectPath(linkedProjectPath);
      setGradleHome(gradleHomePath);
      setPreferLocalInstallationToWrapper(preferLocalInstallationToWrapper);
      setUseAutoImport(useAutoImport);
      setServiceDirectoryPath(serviceDirectoryPath);
    }
    finally {
      publisher.onBulkChangeEnd();
    }
  }

  @Override
  public String toString() {
    return "home: " + myGradleHome + ", path: " + getLinkedExternalProjectPath();
  }
  
  public static class MyState extends State {
    public String gradleHome;
    public String serviceDirectoryPath;
    public boolean preferLocalInstallationToWrapper;
  }
}