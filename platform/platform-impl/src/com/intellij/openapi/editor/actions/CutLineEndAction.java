/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

public class CutLineEndAction extends EditorAction {
  public CutLineEndAction() {
    super(new Handler(true));
  }

  static class Handler extends EditorWriteActionHandler {
    private final boolean myCopyToClipboard;

    Handler(boolean copyToClipboard) {
      myCopyToClipboard = copyToClipboard;
    }

    public void executeWriteAction(Editor editor, DataContext dataContext) {
      final Document doc = editor.getDocument();
      int caretOffset = editor.getCaretModel().getOffset();
      if (caretOffset >= doc.getTextLength()) {
        return;
      }
      final int lineNumber = doc.getLineNumber(caretOffset);
      int lineEndOffset = doc.getLineEndOffset(lineNumber);

      int start;
      int end;
      if (caretOffset >= lineEndOffset) {
        start = lineEndOffset;
        end = lineEndOffset + 1;
      }
      else {
        start = caretOffset;
        end = lineEndOffset;
        if (lineEndOffset < doc.getTextLength() && CharArrayUtil.isEmptyOrSpaces(doc.getCharsSequence(), caretOffset, lineEndOffset)) {
          end++;
        }
      }

      delete(doc, start, end);
    }

    private void delete(@NotNull Document document, int start, int end) {
      if (myCopyToClipboard) {
        KillRingUtil.copyToKillRing(document, start, end, true);
      }
      document.deleteString(start, end);
    }
  }
}
