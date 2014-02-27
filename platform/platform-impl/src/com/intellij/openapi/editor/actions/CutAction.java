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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 13, 2002
 * Time: 6:41:17 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.util.registry.Registry;

public class CutAction extends EditorAction {
  public CutAction() {
    super(new Handler());
  }

  public static class Handler extends EditorWriteActionHandler {
    @Override
    public void executeWriteAction(final Editor editor, DataContext dataContext) {
      if(!editor.getSelectionModel().hasSelection(true)) {
        if (Registry.is(CopyAction.SKIP_COPY_AND_CUT_FOR_EMPTY_SELECTION_KEY)) {
          return;
        }
        editor.getCaretModel().runForEachCaret(new CaretAction() {
          @Override
          public void perform(Caret caret) {
            editor.getSelectionModel().selectLineAtCaret();
          }
        });
      }
      editor.getSelectionModel().copySelectionToClipboard();
      EditorModificationUtil.deleteSelectedTextForAllCarets(editor);
    }
  }
}