package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.localVcs.UpToDateLineNumberProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.LineNumberListener;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsFileRevisionEx;
import com.intellij.openapi.vcs.vfs.VcsFileSystem;
import com.intellij.openapi.vcs.vfs.VcsVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

abstract class AnnotateRevisionAction extends AnnotateRevisionActionBase implements DumbAware, LineNumberListener {
  private final UpToDateLineNumberProvider myGetUpToDateLineNumber;

  @NotNull private final FileAnnotation myAnnotation;
  @NotNull private final AbstractVcs myVcs;

  private int currentLine;

  public AnnotateRevisionAction(@Nullable String text, @Nullable String description, @Nullable Icon icon,
                                @NotNull UpToDateLineNumberProvider getUpToDateLineNumber,
                                @NotNull FileAnnotation annotation, @NotNull AbstractVcs vcs) {
    super(text, description, icon);
    myGetUpToDateLineNumber = getUpToDateLineNumber;
    myAnnotation = annotation;
    myVcs = vcs;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (getRevisions() == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    e.getPresentation().setVisible(true);

    super.update(e);
  }

  @Nullable
  protected abstract List<VcsFileRevision> getRevisions();

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
      (revision instanceof VcsFileRevisionEx ? ((VcsFileRevisionEx)revision).getPath() : new FilePathImpl(myAnnotation.getFile()));
    return new VcsVirtualFile(filePath.getPath(), revision, VcsFileSystem.getInstance()) {
      @NotNull
      @Override
      public FileType getFileType() {
        FileType type = super.getFileType();
        return type.isBinary() ? currentFileType : type;
      }
    };
  }

  @Nullable
  @Override
  protected VcsFileRevision getFileRevision(@NotNull AnActionEvent e) {
    List<VcsFileRevision> revisions = getRevisions();
    assert getRevisions() != null;

    if (currentLine < 0) return null;
    int corrected = myGetUpToDateLineNumber.getLineNumber(currentLine);

    if (corrected < 0 || corrected >= revisions.size()) return null;
    return revisions.get(corrected);
  }

  @Override
  public void consume(Integer integer) {
    currentLine = integer;
  }
}
