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
package com.intellij.openapi.editor.actions;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public abstract class ConvertIndentsActionBase extends EditorAction {
  protected ConvertIndentsActionBase() {
    super(null);
    setupHandler(new Handler());
  }

  protected abstract int performAction(Editor editor, TextRange textRange);

  private class Handler extends EditorWriteActionHandler {
    @Override
    public void executeWriteAction(final Editor editor, @Nullable Caret caret, DataContext dataContext) {
      final SelectionModel selectionModel = editor.getSelectionModel();
      int changedLines = 0;
      if (selectionModel.hasSelection()) {
        changedLines = performAction(editor, new TextRange(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd()));
      }
      else {
        changedLines += performAction(editor, new TextRange(0, editor.getDocument().getTextLength()));
      }
      if (changedLines == 0) {
        HintManager.getInstance().showInformationHint(editor, IdeBundle.message("hint.text.all.lines.already.have.requested.indentation"));
      }
      else {
        HintManager.getInstance().showInformationHint(editor, IdeBundle.message("hint.text.changed.indentation.in", changedLines));
      }
    }
  }
}
