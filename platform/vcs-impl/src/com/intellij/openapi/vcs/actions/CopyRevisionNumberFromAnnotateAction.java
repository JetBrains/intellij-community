// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.TextTransferable;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
final class CopyRevisionNumberFromAnnotateAction extends DumbAwareAction {
  private final FileAnnotation myAnnotation;

  CopyRevisionNumberFromAnnotateAction(FileAnnotation annotation) {
    super(VcsBundle.messagePointer("copy.revision.number.action"),
          ActionsBundle.messagePointer("action.Vcs.CopyRevisionNumberAction.description"),
          PlatformIcons.COPY_ICON);
    myAnnotation = annotation;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    int lineNumber = ShowAnnotateOperationsPopup.getAnnotationLineNumber(e.getDataContext());
    if (lineNumber < 0) return;
    final VcsRevisionNumber revisionNumber = myAnnotation.getLineRevisionNumber(lineNumber);
    if (revisionNumber != null) {
      CopyPasteManager.getInstance().setContents(new TextTransferable(revisionNumber.asString()));
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    int lineNumber = ShowAnnotateOperationsPopup.getAnnotationLineNumber(e.getDataContext());
    final boolean enabled = lineNumber >= 0 && myAnnotation.getLineRevisionNumber(lineNumber) != null;
    e.getPresentation().setEnabledAndVisible(enabled);
  }
}
