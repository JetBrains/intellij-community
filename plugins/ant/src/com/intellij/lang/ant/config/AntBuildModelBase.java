package com.intellij.lang.ant.config;

import com.intellij.lang.ant.config.impl.BuildTask;
import com.intellij.lang.ant.psi.AntProject;
import org.jetbrains.annotations.Nullable;

public interface AntBuildModelBase extends AntBuildModel {

  @Nullable
  String getDefaultTargetActionId();

  @Nullable
  BuildTask findTask(final String targetName, final String taskName);

  AntProject getAntProject();

  boolean hasTargetWithActionId(final String id);
}
