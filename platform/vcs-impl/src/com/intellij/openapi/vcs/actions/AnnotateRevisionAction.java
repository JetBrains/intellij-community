package com.intellij.openapi.vcs.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.LineNumberListener;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsFileRevisionEx;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.vfs.VcsFileSystem;
import com.intellij.openapi.vcs.vfs.VcsVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class AnnotateRevisionAction extends AnnotateRevisionActionBase implements DumbAware, LineNumberListener {
  @NotNull private final FileAnnotation myAnnotation;
  @NotNull private final AbstractVcs myVcs;

  @NotNull private final List<VcsFileRevision> myRevisions;

  private int currentLine;

  public AnnotateRevisionAction(@NotNull FileAnnotation annotation, @NotNull AbstractVcs vcs) {
    super("Annotate revision", "Annotate selected revision in new tab", AllIcons.Actions.Annotate);
    myAnnotation = annotation;
    myVcs = vcs;

    Map<VcsRevisionNumber, VcsFileRevision> map = new HashMap<VcsRevisionNumber, VcsFileRevision>();
    List<VcsFileRevision> revisions = myAnnotation.getRevisions();
    if (revisions != null) {
      for (VcsFileRevision revision : revisions) {
        map.put(revision.getRevisionNumber(), revision);
      }
    }

    myRevisions = new ArrayList<VcsFileRevision>(myAnnotation.getLineCount());
    for (int i = 0; i < myAnnotation.getLineCount(); i++) {
      myRevisions.add(map.get(myAnnotation.getLineRevisionNumber(i)));
    }
  }

  @Nullable
  protected AbstractVcs getVcs(@NotNull AnActionEvent e) {
    return myVcs;
  }

  @Nullable
  @Override
  protected VirtualFile getFile(@NotNull AnActionEvent e) {
    VcsFileRevision revision = getFileRevision(e);
    if (revision == null) return null;

    FilePath filePath =
      (revision instanceof VcsFileRevisionEx ? ((VcsFileRevisionEx)revision).getPath() : new FilePathImpl(myAnnotation.getFile()));
    return new VcsVirtualFile(filePath.getPath(), revision, VcsFileSystem.getInstance());
  }

  @Nullable
  @Override
  protected VcsFileRevision getFileRevision(@NotNull AnActionEvent e) {
    if (currentLine < 0 || currentLine >= myRevisions.size()) return null;
    return myRevisions.get(currentLine);
  }

  @Override
  public void consume(Integer integer) {
    currentLine = integer;
  }
}
