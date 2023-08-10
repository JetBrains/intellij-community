// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.util.ui.TextTransferable;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class CopyRevisionNumberFromAnnotateAction extends DumbAwareAction {
  private final FileAnnotation myAnnotation;

  public CopyRevisionNumberFromAnnotateAction(FileAnnotation annotation) {
    super(VcsBundle.messagePointer("copy.revision.number.action"));
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
