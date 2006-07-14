package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.config.AntBuildTargetBase;
import com.intellij.lang.ant.psi.AntTask;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BuildTask {
  public static final BuildTask[] EMPTY_ARRAY = new BuildTask[0];
  private final AntBuildTargetBase myTarget;
  private final AntTask myTask;

  public BuildTask(final AntBuildTargetBase target, final AntTask task) {
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

  @Nullable
  public OpenFileDescriptor getOpenFileDescriptor() {
    final PsiFile file = myTarget.getAntFile();
    final VirtualFile vFile = file.getVirtualFile();
    if( vFile == null) return null;
    return new OpenFileDescriptor(file.getProject(), vFile, getPsiElement().getTextOffset());
  }
}
