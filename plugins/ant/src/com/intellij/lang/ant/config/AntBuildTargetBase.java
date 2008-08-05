package com.intellij.lang.ant.config;

import com.intellij.lang.ant.config.impl.BuildTask;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

public interface AntBuildTargetBase extends AntBuildTarget {
  AntBuildTarget[] EMPTY_ARRAY = new AntBuildTarget[0];

  @Nullable
  VirtualFile getContainingFile();
  
  Project getProject();

  @Nullable
  String getActionId();

  @Nullable
  OpenFileDescriptor getOpenFileDescriptor();

  @Nullable
  BuildTask findTask(final String taskName);
}
