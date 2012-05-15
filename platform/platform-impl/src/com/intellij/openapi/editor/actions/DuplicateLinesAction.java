/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.util.Pair;

/**
 * @author yole
 */
public class DuplicateLinesAction extends EditorAction {
  public DuplicateLinesAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    @Override
    public void executeWriteAction(Editor editor, DataContext dataContext) {
      if (editor.getSelectionModel().hasSelection()) {
        int selStart = editor.getSelectionModel().getSelectionStart();
        int selEnd = editor.getSelectionModel().getSelectionEnd();
        VisualPosition rangeStart = editor.offsetToVisualPosition(Math.min(selStart, selEnd));
        VisualPosition rangeEnd = editor.offsetToVisualPosition(Math.max(selStart, selEnd));
        final Pair<Integer,Integer> copiedRange =
          DuplicateAction.duplicateLinesRange(editor, editor.getDocument(), rangeStart, rangeEnd);
        if (copiedRange != null) {
          editor.getSelectionModel().setSelection(copiedRange.first, copiedRange.second);
        }
      }
      else {
        VisualPosition caretPos = editor.getCaretModel().getVisualPosition();
        DuplicateAction.duplicateLinesRange(editor, editor.getDocument(), caretPos, caretPos);
      }
    }
  }
}
