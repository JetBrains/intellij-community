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
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;

/**
 * Provides functionality similar to the emacs
 * <a href="http://www.gnu.org/software/emacs/manual/html_node/emacs/Setting-Mark.html">exchange-point-and-mark</a>.
 * 
 * @author Denis Zhdanov
 * @since 3/18/12 3:14 PM
 */
public class SwapSelectionBoundariesAction extends EditorAction {

  public SwapSelectionBoundariesAction() {
    super(new Handler());
  }
  
  private static class Handler extends EditorActionHandler {
    public Handler() {
      super(true);
    }

    @Override
    public void execute(Editor editor, DataContext dataContext) {
      if (!(editor instanceof EditorEx)) {
        return;
      }
      final SelectionModel selectionModel = editor.getSelectionModel();
      if (!selectionModel.hasSelection()) {
        return;
      }
      
      EditorEx editorEx = (EditorEx)editor;
      final int start = selectionModel.getSelectionStart();
      final int end = selectionModel.getSelectionEnd();
      final CaretModel caretModel = editor.getCaretModel();
      boolean moveToEnd = caretModel.getOffset() == start;
      editorEx.setStickySelection(false);
      editorEx.setStickySelection(true);
      if (moveToEnd) {
        caretModel.moveToOffset(end);
      }
      else {
        caretModel.moveToOffset(start);
      }
    }
  }
}
