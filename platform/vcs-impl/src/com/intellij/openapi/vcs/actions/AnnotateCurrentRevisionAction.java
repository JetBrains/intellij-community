package com.intellij.openapi.vcs.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class AnnotateCurrentRevisionAction extends AnnotateRevisionAction {
  @Nullable private final FileAnnotation.CurrentFileRevisionProvider myProvider;

  AnnotateCurrentRevisionAction(@NotNull FileAnnotation annotation, @NotNull AbstractVcs vcs) {
    super(VcsBundle.messagePointer("action.annotate.revision.text"),
          VcsBundle.messagePointer("action.annotate.selected.revision.in.new.tab.description"),
          AllIcons.Actions.Annotate,
          annotation,
          vcs);
    myProvider = annotation.getCurrentFileRevisionProvider();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (myProvider == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    super.update(e);
  }

  @Nullable
  @Override
  protected VcsFileRevision getRevision(int lineNumber) {
    assert myProvider != null;

    if (lineNumber < 0 || lineNumber >= myAnnotation.getLineCount()) return null;
    return myProvider.getRevision(lineNumber);
  }
}
