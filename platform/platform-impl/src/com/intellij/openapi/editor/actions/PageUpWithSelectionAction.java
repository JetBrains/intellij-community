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
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PageUpWithSelectionAction extends EditorAction {
  public static class Handler extends EditorActionHandler {
    @Override
    public void doExecute(@NotNull final Editor editor, @Nullable Caret caret, DataContext dataContext) {
      if (!editor.getCaretModel().supportsMultipleCarets()) {
        EditorActionUtil.moveCaretPageUp(editor, true);
        return;
      }
      if (editor.isColumnMode()) {
        int lines = editor.getScrollingModel().getVisibleArea().height / editor.getLineHeight();
        CloneCaretActionHandler handler = new CloneCaretActionHandler(true);
        for (int i = 0; i < lines; i++) {
          handler.execute(editor, caret, dataContext);
          handler.setRepeatedInvocation(true);
        }
      }
      else {
        if (caret == null) {
          editor.getCaretModel().runForEachCaret(__ -> EditorActionUtil.moveCaretPageUp(editor, true));
        }
        else {
          // assuming caret is equal to CaretModel.getCurrentCaret()
          EditorActionUtil.moveCaretPageUp(editor, true);
        }
      }
    }
  }

  public PageUpWithSelectionAction() {
    super(new Handler());
  }
}
