/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.TextRange;

public class DeleteToWordEndAction extends TextComponentEditorAction {
  public DeleteToWordEndAction() {
    super(new Handler(false));
  }

  static class Handler extends EditorWriteActionHandler {
    
    private final boolean myNegateCamelMode;

    Handler(boolean negateCamelMode) {
      super(true);
      myNegateCamelMode = negateCamelMode;
    }

    @Override
    public void executeWriteAction(Editor editor, Caret caret, DataContext dataContext) {
      CommandProcessor.getInstance().setCurrentCommandGroupId(EditorActionUtil.DELETE_COMMAND_GROUP);
      CopyPasteManager.getInstance().stopKillRings();

      boolean camelMode = editor.getSettings().isCamelWords();
      if (myNegateCamelMode) {
        camelMode = !camelMode;
      }

      if (editor.getSelectionModel().hasSelection()) {
        EditorModificationUtil.deleteSelectedText(editor);
        return;
      }

      deleteToWordEnd(editor, camelMode);
    }
  }

  private static void deleteToWordEnd(Editor editor, boolean camelMode) {
    final TextRange range = EditorActionUtil.getRangeToWordEnd(editor, camelMode, true);
    if (!range.isEmpty()) {
      editor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());
    }
  }
}
