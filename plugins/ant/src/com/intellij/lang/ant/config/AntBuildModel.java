package com.intellij.lang.ant.config;

import com.intellij.lang.ant.config.impl.BuildTask;
import com.intellij.lang.ant.psi.AntProject;
import org.jetbrains.annotations.Nullable;

public interface AntBuildModel {
  @Nullable
  String getDefaultTargetName();

  AntBuildTarget[] getTargets();

  AntBuildTarget[] getFilteredTargets();

  @Nullable
  String getDefaultTargetActionId();

  AntBuildFile getBuildFile();

  @Nullable
  AntBuildTarget findTarget(final String name);

  @Nullable
  String getName();

  @Nullable
  BuildTask findTask(final String targetName, final String taskName);

  AntProject getAntProject();
}
