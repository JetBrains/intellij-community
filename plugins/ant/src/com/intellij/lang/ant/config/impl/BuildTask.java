package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntTask;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public final class BuildTask {
  public static final BuildTask[] EMPTY_ARRAY = new BuildTask[0];
  private final AntBuildTarget myTarget;
  private final AntTask myTask;

  public BuildTask(final AntBuildTarget target, final AntTask task) {
    myTarget = target;
    myTask = task;
  }

  public String getName() {
    return myTask.toString();
  }

  @NotNull
  private PsiElement getPsiElement() {
    return myTask;
  }

  public OpenFileDescriptor getOpenFileDescriptor() {
    final AntFile file = getAntFile();
    return new OpenFileDescriptor(file.getProject(), file.getVirtualFile(), getPsiElement().getTextOffset());
  }

  private AntFile getAntFile() {
    return myTarget.getAntFile();
  }
}
