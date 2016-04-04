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
 * Date: May 14, 2002
 * Time: 6:29:03 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.impl.EditorImpl;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TextEndWithSelectionAction extends TextComponentEditorAction {
  public TextEndWithSelectionAction() {
    super(new Handler());
  }

  private static class Handler extends EditorActionHandler {
    @Override
    public void doExecute(Editor editor, @Nullable Caret caret, DataContext dataContext) {
      int endOffset = editor.getDocument().getTextLength();
      List<Caret> carets = editor.getCaretModel().getAllCarets();
      if (editor.isColumnMode() && editor.getCaretModel().supportsMultipleCarets()) {
        if (caret == null) { // normally we're always called with null caret
          caret = carets.get(0) == editor.getCaretModel().getPrimaryCaret() ? carets.get(carets.size() - 1) : carets.get(0);
        }
        LogicalPosition leadSelectionPosition = editor.visualToLogicalPosition(caret.getLeadSelectionPosition());
        LogicalPosition targetPosition = editor.offsetToLogicalPosition(endOffset).leanForward(true);
        editor.getSelectionModel().setBlockSelection(leadSelectionPosition, targetPosition);
      }
      else {
        if (caret == null) { // normally we're always called with null caret
          caret = carets.get(0);
        }
        int selectionStart = caret.getLeadSelectionOffset();
        if (editor instanceof EditorImpl && ((EditorImpl)editor).myUseNewRendering) {
          caret.moveToLogicalPosition(editor.offsetToLogicalPosition(endOffset).leanForward(true));
        }
        else {
          caret.moveToOffset(endOffset);
        }
        caret.setSelection(selectionStart, endOffset);
      }
      ScrollingModel scrollingModel = editor.getScrollingModel();
      scrollingModel.disableAnimation();
      scrollingModel.scrollToCaret(ScrollType.CENTER);
      scrollingModel.enableAnimation();
    }
  }
}
