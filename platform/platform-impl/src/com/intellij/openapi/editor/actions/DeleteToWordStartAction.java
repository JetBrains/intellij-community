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
 * Time: 7:18:30 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.actionSystem.DataContext;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
import org.jetbrains.annotations.NotNull;

public class DeleteToWordStartAction extends TextComponentEditorAction {

  /**
   * We need to provide special processing for quote symbols.
   * <p/>
   * Examples:
   *   <table border='1'>
   *     <tr>
   *       <th>Text before action call</td>
   *       <th>Text after action call</td>
   *     </tr>
   *     <tr>
   *       <td>one "two" [caret]</td>
   *       <td>one [caret]</td>
   *     </tr>
   *     <tr>
   *       <td>one "two[caret]"</td>
   *       <td>one "[caret]"</td>
   *     </tr>
   *   </table>
   */
  private static final TIntHashSet QUOTE_SYMBOLS = new TIntHashSet();

  static {
    QUOTE_SYMBOLS.add('\'');
    QUOTE_SYMBOLS.add('\"');
  }

  private static final int[] QUOTE_SYMBOLS_ARRAY = QUOTE_SYMBOLS.toArray();

  public DeleteToWordStartAction() {
    super(new Handler(false));
  }

  static class Handler extends EditorWriteActionHandler {

    @NotNull private final TIntIntHashMap myQuotesNumber = new TIntIntHashMap();
    private final boolean myNegateCamelMode;

    Handler(boolean negateCamelMode) {
      myNegateCamelMode = negateCamelMode;
    }

    @Override
    public void executeWriteAction(Editor editor, DataContext dataContext) {
      CommandProcessor.getInstance().setCurrentCommandGroupId(EditorActionUtil.DELETE_COMMAND_GROUP);
      deleteToWordStart(editor);
    }

    private void deleteToWordStart(Editor editor) {
      boolean camel = editor.getSettings().isCamelWords();
      if (myNegateCamelMode) {
        camel = !camel;
      }
      CharSequence text = editor.getDocument().getCharsSequence();
      CaretModel caretModel = editor.getCaretModel();
      int endOffset = caretModel.getOffset();
      int minOffset = editor.getDocument().getLineStartOffset(caretModel.getLogicalPosition().line);
      
      myQuotesNumber.clear();
      for (int i : QUOTE_SYMBOLS_ARRAY) {
        myQuotesNumber.put(i, 0);
      }
      countQuotes(myQuotesNumber, text, minOffset, endOffset);
      
      EditorActionUtil.moveCaretToPreviousWord(editor, false, camel);
      
      for (int offset = caretModel.getOffset(); offset > minOffset; offset = caretModel.getOffset()) {
        char previous = text.charAt(offset - 1);
        char current = text.charAt(offset);
        if (QUOTE_SYMBOLS.contains(current)) {
          if (Character.isWhitespace(previous)) {
            break;
          }
          else if (offset < endOffset - 1 && !Character.isJavaIdentifierPart(text.charAt(offset + 1))) {
            // Handle a situation like ' "one", "two", [caret] '. We want to delete up to the previous literal end here.
            editor.getCaretModel().moveToOffset(offset + 1);
            break;
          }
          if (myQuotesNumber.get(current) % 2 == 0) {
            // Was 'one "two" [caret]', now 'one "two[caret]"', we want to get 'one [caret]"two"'
            EditorActionUtil.moveCaretToPreviousWord(editor, false, camel);
            continue;
          }
          break;
        }

        if (QUOTE_SYMBOLS.contains(previous)) {
          if (myQuotesNumber.get(previous) % 2 == 0) {
            // Was 'one "two[caret]", now 'one "[caret]two"', we want 'one [caret]"two"'
            editor.getCaretModel().moveToOffset(offset - 1);
          }
        }
        break;
      }

      int startOffset = caretModel.getOffset();
      Document document = editor.getDocument();
      document.deleteString(startOffset, endOffset);
    }
  }

  private static void countQuotes(@NotNull TIntIntHashMap holder, @NotNull CharSequence text, int start, int end) {
    for (int i = end - 1; i >= start; i--) {
      char c = text.charAt(i);
      if (holder.containsKey(c)) {
        holder.put(c, holder.get(c) + 1);
      }
    }
  }
}
