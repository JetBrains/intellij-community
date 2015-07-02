/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
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
    public void doExecute(Editor editor, Caret caret, DataContext dataContext) {
      assert caret != null;
      
      if (!caret.hasSelection()) {
        return;
      }
      final int start = caret.getSelectionStart();
      final int end = caret.getSelectionEnd();
      boolean moveToEnd = caret.getOffset() == start;
      
      if (editor instanceof EditorEx) {
        EditorEx editorEx = (EditorEx)editor;
        if (editorEx.isStickySelection()) {
          editorEx.setStickySelection(false);
          editorEx.setStickySelection(true);
        }
      }
      
      if (moveToEnd) {
        caret.moveToOffset(end);
      }
      else {
        caret.moveToOffset(start);
      }
    }
  }
}
