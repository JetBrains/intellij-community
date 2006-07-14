package com.intellij.lang.ant.config;

import com.intellij.lang.ant.config.impl.BuildTask;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntTarget;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import org.jetbrains.annotations.Nullable;

public interface AntBuildTarget {
  AntBuildTarget[] EMPTY_ARRAY = new AntBuildTarget[0];

  @Nullable
  String getName();

  @Nullable
  String getNotEmptyDescription();

  boolean isDefault();

  void run(DataContext dataContext, AntBuildListener buildListener);

  @Nullable
  AntTarget getAntTarget();

  AntFile getAntFile();

  @Nullable
  String getActionId();

  AntBuildModel getModel();

  @Nullable
  OpenFileDescriptor getOpenFileDescriptor();

  @Nullable
  BuildTask findTask(final String taskName);
}
