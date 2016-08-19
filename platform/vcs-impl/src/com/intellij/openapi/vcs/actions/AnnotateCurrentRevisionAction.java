package com.intellij.openapi.vcs.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.history.VcsRevisionDescription;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class AnnotateCurrentRevisionAction extends AnnotateRevisionAction {
  @Nullable private final List<VcsRevisionDescription> myRevisions;

  public AnnotateCurrentRevisionAction(@NotNull FileAnnotation annotation, @NotNull AbstractVcs vcs) {
    super("Annotate Revision", "Annotate selected revision in new tab", AllIcons.Actions.Annotate,
          annotation, vcs);
    List<? extends VcsRevisionDescription> revisions = annotation.getRevisionDescriptions();
    if (revisions == null) {
      myRevisions = null;
      return;
    }

    Map<VcsRevisionNumber, VcsRevisionDescription> map = new HashMap<>();
    for (VcsRevisionDescription revision : revisions) {
      map.put(revision.getRevisionNumber(), revision);
    }

    myRevisions = new ArrayList<>(annotation.getLineCount());
    for (int i = 0; i < annotation.getLineCount(); i++) {
      myRevisions.add(map.get(annotation.getLineRevisionNumber(i)));
    }
  }

  @Override
  @Nullable
  public List<VcsRevisionDescription> getRevisions() {
    return myRevisions;
  }
}
