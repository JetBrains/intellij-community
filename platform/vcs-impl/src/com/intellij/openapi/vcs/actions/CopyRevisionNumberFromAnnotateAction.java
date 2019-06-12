// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.UpToDateLineNumberListener;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.util.ui.TextTransferable;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class CopyRevisionNumberFromAnnotateAction extends DumbAwareAction implements UpToDateLineNumberListener {
  private final FileAnnotation myAnnotation;
  private int myLineNumber = -1;

  public CopyRevisionNumberFromAnnotateAction(FileAnnotation annotation) {
    super("Copy Revision Number");
    myAnnotation = annotation;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    if (myLineNumber < 0) return;
    final VcsRevisionNumber revisionNumber = myAnnotation.getLineRevisionNumber(myLineNumber);
    if (revisionNumber != null) {
      CopyPasteManager.getInstance().setContents(new TextTransferable(revisionNumber.asString()));
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final boolean enabled = myLineNumber >= 0 && myAnnotation.getLineRevisionNumber(myLineNumber) != null;
    e.getPresentation().setEnabledAndVisible(enabled);
  }

  @Override
  public void consume(Integer integer) {
    myLineNumber = integer;
  }
}
