package com.intellij.lang.ant.config;

import com.intellij.lang.ant.config.impl.BuildTask;
import com.intellij.lang.ant.psi.AntTarget;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

public interface AntBuildTargetBase extends AntBuildTarget {
  AntBuildTarget[] EMPTY_ARRAY = new AntBuildTarget[0];

  @Nullable
  AntTarget getAntTarget();

  PsiFile getAntFile();

  @Nullable
  String getActionId();

  AntBuildModelBase getModel();

  @Nullable
  OpenFileDescriptor getOpenFileDescriptor();

  @Nullable
  BuildTask findTask(final String taskName);
}
