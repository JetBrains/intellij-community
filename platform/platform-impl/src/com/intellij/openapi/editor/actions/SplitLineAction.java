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

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.Key;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class SplitLineAction extends EditorAction {
  public static Key<Boolean> SPLIT_LINE_KEY = Key.create("com.intellij.openapi.editor.actions.SplitLineAction");

  public SplitLineAction() {
    super(new Handler());
    setEnabledInModalContext(false);
  }

  private static class Handler extends EditorWriteActionHandler {
    public Handler() {
      super(true);
    }

    @Override
    public boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      return getEnterHandler().isEnabled(editor, caret, dataContext) &&
             !((EditorEx)editor).isEmbeddedIntoDialogWrapper();
    }

    @Override
    public void executeWriteAction(Editor editor, Caret caret, DataContext dataContext) {
      CopyPasteManager.getInstance().stopKillRings();
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
        DataManager.getInstance().saveInDataContext(dataContext, SPLIT_LINE_KEY, true);
        try {
          getEnterHandler().execute(editor, caret, dataContext);
        }
        finally {
          DataManager.getInstance().saveInDataContext(dataContext, SPLIT_LINE_KEY, null);
        }

        editor.getCaretModel().moveToOffset(Math.min(document.getTextLength(), rangeMarker.getStartOffset()));
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      }

    }

    private static EditorActionHandler getEnterHandler() {
      return EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER);
    }
  }
}
