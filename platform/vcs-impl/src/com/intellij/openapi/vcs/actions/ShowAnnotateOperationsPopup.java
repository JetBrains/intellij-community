// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.TextAnnotationPresentation;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.impl.AbstractVcsHelperImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ShowAnnotateOperationsPopup extends DumbAwareAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    List<AnAction> actions = getActions(e.getDataContext());
    e.getPresentation().setEnabled(actions != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    List<AnAction> actions = getActions(e.getDataContext());
    if (actions == null) return;

    String title = getTemplatePresentation().getText();
    DefaultActionGroup group = new DefaultActionGroup(actions);
    ListPopup popup = JBPopupFactory.getInstance().
      createActionGroupPopup(title, group, e.getDataContext(), JBPopupFactory.ActionSelectionAid.NUMBERING, true);
    popup.showInBestPositionFor(e.getDataContext());
  }

  @Nullable
  private static List<AnAction> getActions(@NotNull DataContext context) {
    Editor editor = context.getData(CommonDataKeys.EDITOR);
    if (editor == null) return null;

    TextAnnotationPresentation presentation = AnnotateToggleAction.getAnnotationPresentation(editor);
    if (presentation == null) return null;

    int line = editor.getCaretModel().getLogicalPosition().line;
    List<AnAction> actions = presentation.getActions(line);

    FileAnnotation fileAnnotation = AnnotateToggleAction.getFileAnnotation(editor);
    if (fileAnnotation != null) {
      int annotationLine = presentation.getAnnotationLine(line);
      actions = ContainerUtil.prepend(actions, new ShowAffectedFilesAction(fileAnnotation, annotationLine));
    }

    return ContainerUtil.nullize(actions);
  }

  public static class Group extends ActionGroup implements DumbAware {
    @Override
    public boolean hideIfNoVisibleChildren() {
      return true;
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      if (e == null) return AnAction.EMPTY_ARRAY;

      List<AnAction> actions = getActions(e.getDataContext());
      return actions != null ? actions.toArray(AnAction.EMPTY_ARRAY) : AnAction.EMPTY_ARRAY;
    }
  }

  private static final class ShowAffectedFilesAction extends DumbAwareAction {
    private final FileAnnotation myFileAnnotation;
    private final int myLine;

    private final FileAnnotation.RevisionChangesProvider myChangesProvider;
    private final VcsRevisionNumber myRevisionNumber;
    private final VirtualFile myFile;

    private ShowAffectedFilesAction(@NotNull FileAnnotation fileAnnotation, int line) {
      super(VcsBundle.messagePointer("action.ShowAffectedFilesAction.show.affected.files.text"), AllIcons.Actions.ListChanges);
      myFileAnnotation = fileAnnotation;
      myLine = line;

      myChangesProvider = fileAnnotation.getRevisionsChangesProvider();
      myRevisionNumber = fileAnnotation.getLineRevisionNumber(myLine);
      myFile = fileAnnotation.getFile();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      boolean visible = myChangesProvider != null && myFile != null;
      e.getPresentation().setVisible(visible);
      e.getPresentation().setEnabled(visible && myRevisionNumber != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      FilePath filePath = VcsUtil.getFilePath(myFile);
      AbstractVcsHelperImpl.loadAndShowCommittedChangesDetails(myFileAnnotation.getProject(), myRevisionNumber, filePath, () -> myChangesProvider.getChangesIn(myLine));
    }
  }
}
