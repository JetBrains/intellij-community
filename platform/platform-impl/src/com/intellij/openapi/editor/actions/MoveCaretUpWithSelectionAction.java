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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 13, 2002
 * Time: 9:58:23 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import org.jetbrains.annotations.Nullable;

public class MoveCaretUpWithSelectionAction extends EditorAction {
  public MoveCaretUpWithSelectionAction() {
    super(new Handler());
  }

  private static class Handler extends EditorActionHandler {
    @Override
    public void doExecute(final Editor editor, @Nullable Caret caret, DataContext dataContext) {
      if (!editor.getCaretModel().supportsMultipleCarets()) {
        editor.getCaretModel().moveCaretRelatively(0, -1, true, editor.isColumnMode(), true);
        return;
      }
      if (editor.isColumnMode()) {
        new CloneCaretActionHandler(true).execute(editor, caret, dataContext);
      }
      else {
        CaretAction caretAction = new CaretAction() {
          @Override
          public void perform(Caret caret) {
            caret.moveCaretRelatively(0, -1, true, caret == editor.getCaretModel().getPrimaryCaret());
          }
        };
        if (caret == null) {
          editor.getCaretModel().runForEachCaret(caretAction);
        }
        else {
          caretAction.perform(caret);
        }
      }
    }
  }
}
