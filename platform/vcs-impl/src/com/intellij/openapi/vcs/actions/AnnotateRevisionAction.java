// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsFileRevisionEx;
import com.intellij.openapi.vcs.vfs.VcsVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Supplier;

abstract class AnnotateRevisionAction extends AnnotateRevisionActionBase implements DumbAware {
  protected final @NotNull FileAnnotation myAnnotation;
  private final @NotNull AbstractVcs myVcs;

  AnnotateRevisionAction(@NotNull Supplier<String> dynamicText,
                         @NotNull Supplier<String> dynamicDescription,
                         @Nullable Icon icon,
                         @NotNull FileAnnotation annotation,
                         @NotNull AbstractVcs vcs) {
    super(dynamicText, dynamicDescription, icon);
    myAnnotation = annotation;
    myVcs = vcs;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (Boolean.TRUE.equals(e.getData(PlatformCoreDataKeys.IS_MODAL_CONTEXT))) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    if (myAnnotation.getFile() == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    e.getPresentation().setVisible(true);
    super.update(e);
  }

  @Override
  protected @Nullable AbstractVcs getVcs(@NotNull AnActionEvent e) {
    return myVcs;
  }

  @Override
  protected @Nullable VirtualFile getFile(@NotNull AnActionEvent e) {
    VcsFileRevision revision = getFileRevision(e);
    if (revision == null) return null;

    final FileType currentFileType = myAnnotation.getFile().getFileType();
    FilePath filePath =
      (revision instanceof VcsFileRevisionEx ? ((VcsFileRevisionEx)revision).getPath() : VcsUtil.getFilePath(myAnnotation.getFile()));
    return new MyVcsVirtualFile(filePath, revision, currentFileType);
  }

  @Override
  protected @Nullable Editor getEditor(@NotNull AnActionEvent e) {
    return e.getData(CommonDataKeys.EDITOR);
  }

  private static class MyVcsVirtualFile extends VcsVirtualFile {
    private final @NotNull FileType myCurrentFileType;

    MyVcsVirtualFile(@NotNull FilePath filePath, @NotNull VcsFileRevision revision, @NotNull FileType currentFileType) {
      super(filePath, revision);
      myCurrentFileType = currentFileType;
    }

    @Override
    public @NotNull FileType getFileType() {
      FileType type = super.getFileType();
      if (!type.isBinary()) return type;
      if (!myCurrentFileType.isBinary()) return myCurrentFileType;
      return PlainTextFileType.INSTANCE;
    }
  }
}