package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.UpToDateLineNumberListener;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsFileRevisionEx;
import com.intellij.openapi.vcs.vfs.VcsFileSystem;
import com.intellij.openapi.vcs.vfs.VcsVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

abstract class AnnotateRevisionAction extends AnnotateRevisionActionBase implements DumbAware, UpToDateLineNumberListener {
  @NotNull protected final FileAnnotation myAnnotation;
  @NotNull private final AbstractVcs myVcs;

  private int currentLine;

  public AnnotateRevisionAction(@Nullable String text, @Nullable String description, @Nullable Icon icon,
                                @NotNull FileAnnotation annotation, @NotNull AbstractVcs vcs) {
    super(text, description, icon);
    myAnnotation = annotation;
    myVcs = vcs;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (Boolean.TRUE.equals(e.getData(PlatformDataKeys.IS_MODAL_CONTEXT))) {
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

  @Nullable
  protected abstract VcsFileRevision getRevision(int lineNumber);

  @Nullable
  protected AbstractVcs getVcs(@NotNull AnActionEvent e) {
    return myVcs;
  }

  @Nullable
  @Override
  protected VirtualFile getFile(@NotNull AnActionEvent e) {
    VcsFileRevision revision = getFileRevision(e);
    if (revision == null) return null;

    final FileType currentFileType = myAnnotation.getFile().getFileType();
    FilePath filePath =
      (revision instanceof VcsFileRevisionEx ? ((VcsFileRevisionEx)revision).getPath() : VcsUtil.getFilePath(myAnnotation.getFile()));
    return new MyVcsVirtualFile(filePath, revision, currentFileType);
  }

  @Nullable
  @Override
  protected VcsFileRevision getFileRevision(@NotNull AnActionEvent e) {
    return getRevision(currentLine);
  }

  @Override
  protected int getAnnotatedLine(@NotNull AnActionEvent e) {
    if (currentLine < 0) return super.getAnnotatedLine(e);
    return currentLine;
  }

  @Nullable
  @Override
  protected Editor getEditor(@NotNull AnActionEvent e) {
    return e.getData(CommonDataKeys.EDITOR);
  }

  @Override
  public void consume(Integer integer) {
    currentLine = integer;
  }

  private static class MyVcsVirtualFile extends VcsVirtualFile {
    @NotNull private final FileType myCurrentFileType;

    public MyVcsVirtualFile(@NotNull FilePath filePath, @NotNull VcsFileRevision revision, @NotNull FileType currentFileType) {
      super(filePath.getPath(), revision, VcsFileSystem.getInstance());
      myCurrentFileType = currentFileType;
    }

    @NotNull
    @Override
    public FileType getFileType() {
      FileType type = super.getFileType();
      if (!type.isBinary()) return type;
      if (!myCurrentFileType.isBinary()) return myCurrentFileType;
      return PlainTextFileType.INSTANCE;
    }
  }
}
