/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.UpToDateLineNumberListener;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.util.ui.TextTransferable;

/**
 * @author Konstantin Bulenkov
 */
public class CopyRevisionNumberFromAnnotateAction extends AnAction implements UpToDateLineNumberListener {
  private final FileAnnotation myAnnotation;
  private int myLineNumber = -1;

  public CopyRevisionNumberFromAnnotateAction(FileAnnotation annotation) {
    super("Copy revision number");
    myAnnotation = annotation;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    if (myLineNumber < 0) return;
    final VcsRevisionNumber revisionNumber = myAnnotation.getLineRevisionNumber(myLineNumber);
    if (revisionNumber != null) {
      final String revision = revisionNumber.asString();
      CopyPasteManager.getInstance().setContents(new TextTransferable(revision));
    }
  }

  @Override
  public void update(AnActionEvent e) {
    final boolean enabled = myLineNumber >= 0 && myAnnotation.getLineRevisionNumber(myLineNumber) != null;
    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setVisible(enabled);
  }

  @Override
  public void consume(Integer integer) {
    myLineNumber = integer;
  }
}
