// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.lang.ant.config;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class AntConfiguration extends SimpleModificationTracker {
  public static final @NonNls String ACTION_ID_PREFIX = "Ant_";

  public static AntConfiguration getInstance(final Project project) {
    return project.getService(AntConfiguration.class);
  }

  /**
   * @return prefix for all ant actions registered withing this project
   */
  public static String getActionIdPrefix(Project project) {
    return ACTION_ID_PREFIX + project.getLocationHash();
  }

  public abstract boolean isInitialized();

  public abstract boolean hasBuildFiles();

  public abstract AntBuildFile[] getBuildFiles();

  public abstract List<AntBuildFileBase> getBuildFileList();

  public abstract @Nullable AntBuildFile addBuildFile(final VirtualFile file) throws AntNoFileException;

  public abstract void removeBuildFile(final AntBuildFile file);

  public abstract void addAntConfigurationListener(final AntConfigurationListener listener);

  public abstract void removeAntConfigurationListener(final AntConfigurationListener listener);

  public abstract AntBuildTarget[] getMetaTargets(final AntBuildFile buildFile);

  public abstract void updateBuildFile(final AntBuildFile buildFile);

  public abstract @Nullable AntBuildModelBase getModelIfRegistered(@NotNull AntBuildFileBase buildFile);

  public abstract AntBuildModel getModel(@NotNull AntBuildFile buildFile);

  public abstract boolean executeTargetBeforeCompile(CompileContext compileContext, DataContext dataContext);

  public abstract boolean executeTargetAfterCompile(CompileContext compileContext, DataContext dataContext);
}