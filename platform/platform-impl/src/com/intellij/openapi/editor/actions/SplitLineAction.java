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
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.util.text.CharArrayUtil;

/**
 * @author max
 */
public class SplitLineAction extends EditorAction {
  public SplitLineAction() {
    super(new Handler());
    setEnabledInModalContext(false);
  }

  private static class Handler extends EditorWriteActionHandler {
    public boolean isEnabled(Editor editor, DataContext dataContext) {
      return getEnterHandler().isEnabled(editor, dataContext) &&
             !((EditorEx)editor).isEmbeddedIntoDialogWrapper();
    }

    public void executeWriteAction(Editor editor, DataContext dataContext) {
      final Document document = editor.getDocument();
      final RangeMarker rangeMarker =
        document.createRangeMarker(editor.getCaretModel().getOffset(), editor.getCaretModel().getOffset() );
      final CharSequence chars = document.getCharsSequence();

      int offset = editor.getCaretModel().getOffset();
      int lineStart = document.getLineStartOffset(document.getLineNumber(offset));

      final CharSequence beforeCaret = chars.subSequence(lineStart, offset);

      if (CharArrayUtil.containsOnlyWhiteSpaces(beforeCaret)) {
        String strToInsert = "";
        if (beforeCaret != null) {
          strToInsert +=  beforeCaret.toString();
        }
        strToInsert += "\n";
        document.insertString(lineStart, strToInsert);
        editor.getCaretModel().moveToOffset(offset);
      } else {
        getEnterHandler().execute(editor, dataContext);

        editor.getCaretModel().moveToOffset(rangeMarker.getStartOffset());
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      }

    }

    private static EditorActionHandler getEnterHandler() {
      return EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER);
    }
  }
}
