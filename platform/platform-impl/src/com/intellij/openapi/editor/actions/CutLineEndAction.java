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
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.util.DocumentUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CutLineEndAction extends TextComponentEditorAction {
  public CutLineEndAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    private Handler() {
      super(false);
    }

    @Override
    public void executeWriteAction(final Editor editor, @Nullable Caret caret, DataContext dataContext) {
      if (caret == null && editor.getCaretModel().getCaretCount() > 1) {
        editor.getCaretModel().runForEachCaret(new CaretAction() {
          @Override
          public void perform(Caret caret) {
            caret.setSelection(caret.getOffset(), getEndOffset(caret));
          }
        });
        // We don't support kill-ring operations for multiple carets currently
        EditorCopyPasteHelper.getInstance().copySelectionToClipboard(editor);
        EditorModificationUtil.deleteSelectedTextForAllCarets(editor);
      }
      else {
        if (caret == null) {
          caret = editor.getCaretModel().getCurrentCaret();
        }
        int startOffset = caret.getOffset();
        int endOffset = getEndOffset(caret);
        KillRingUtil.cut(editor, startOffset, endOffset);
        // in case the caret was in the virtual space, we force it to go back to the real offset
        caret.moveToOffset(startOffset);
      }
    }

    private static int getEndOffset(@NotNull Caret caret) {
      Document document = caret.getEditor().getDocument();
      int startOffset = caret.getOffset();
      int endOffset = DocumentUtil.getLineEndOffset(startOffset, document);
      if (endOffset < document.getTextLength() &&
          CharArrayUtil.isEmptyOrSpaces(document.getImmutableCharSequence(), startOffset, endOffset)) {
        endOffset++;
      }
      return endOffset;
    }
  }
}
