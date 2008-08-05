package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.config.AntBuildTargetBase;
import com.intellij.lang.ant.psi.AntTask;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

public final class BuildTask {
  public static final BuildTask[] EMPTY_ARRAY = new BuildTask[0];
  private final AntBuildTargetBase myTarget;
  private final String myName;
  private final int myOffset;

  public BuildTask(final AntBuildTargetBase target, final AntTask task) {
    myTarget = target;
    myName = task.toString();
    myOffset = task.getTextOffset();
  }

  public String getName() {
    return myName;
  }

  @Nullable
  public OpenFileDescriptor getOpenFileDescriptor() {
    final VirtualFile vFile = myTarget.getContainingFile();
    return vFile != null? new OpenFileDescriptor(myTarget.getProject(), vFile, myOffset) : null;
  }
}
