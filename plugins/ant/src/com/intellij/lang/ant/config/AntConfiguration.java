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

package com.intellij.lang.ant.config;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class AntConfiguration extends SimpleModificationTracker {
  private final Project myProject;
  @NonNls public static final String ACTION_ID_PREFIX = "Ant_";

  protected AntConfiguration(final Project project) {
    myProject = project;
  }

  public static AntConfiguration getInstance(final Project project) {
    return ServiceManager.getService(project, AntConfiguration.class);
  }

  private static final Key<Boolean> ANT_SUPPORT_INITIALIZED_KEY = new Key<>("AntSupportInitialized");
  public static void initAntSupport(final Project project) {
    if (!Boolean.TRUE.equals(project.getUserData(ANT_SUPPORT_INITIALIZED_KEY))) {
      ServiceManager.getService(project, AntConfiguration.class);
      project.putUserData(ANT_SUPPORT_INITIALIZED_KEY, Boolean.TRUE);
    }
  }

  public Project getProject() {
    return myProject;
  }

  /**
   * @param project
   * @return prefix for all ant actions registered withing this project
   */
  public static String getActionIdPrefix(Project project) {
    return ACTION_ID_PREFIX + project.getLocationHash();
  }
  
  public abstract boolean isInitialized();
  
  public abstract AntBuildFile[] getBuildFiles();

  public abstract List<AntBuildFileBase> getBuildFileList();

  public abstract AntBuildFile addBuildFile(final VirtualFile file) throws AntNoFileException;

  public abstract void removeBuildFile(final AntBuildFile file);

  public abstract void addAntConfigurationListener(final AntConfigurationListener listener);

  public abstract void removeAntConfigurationListener(final AntConfigurationListener listener);

  public abstract AntBuildTarget[] getMetaTargets(final AntBuildFile buildFile);

  public abstract void updateBuildFile(final AntBuildFile buildFile);

  @Nullable
  public abstract AntBuildModelBase getModelIfRegistered(@NotNull AntBuildFileBase buildFile);

  public abstract AntBuildModel getModel(@NotNull AntBuildFile buildFile);

  @Nullable
  public abstract AntBuildFile findBuildFileByActionId(final String id);

  public abstract boolean executeTargetBeforeCompile(DataContext context);

  public abstract boolean executeTargetAfterCompile(DataContext context);

}