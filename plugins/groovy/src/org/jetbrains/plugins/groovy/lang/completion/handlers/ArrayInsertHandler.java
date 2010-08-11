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

package org.jetbrains.plugins.groovy.lang.completion.handlers;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionUtil;

/**
 * @author Maxim.Medvedev
 */
public class ArrayInsertHandler implements InsertHandler<LookupItem> {
  public void handleInsert(InsertionContext context, LookupItem item) {
    int caretOffset = 0;
    int tailOffset = context.getTailOffset();
    final Integer bracketsAttr = (Integer)item.getUserData(LookupItem.BRACKETS_COUNT_ATTR);
    final Editor editor = context.getEditor();
    if (bracketsAttr != null) {
      int count = bracketsAttr.intValue();
      if (count > 0) {
        caretOffset = tailOffset + 1;
      }
      for (int i = 0; i < count; i++) {
        editor.getDocument().insertString(tailOffset, "[]");
      }
    }
    editor.getCaretModel().moveToOffset(caretOffset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().removeSelection();
    GroovyCompletionUtil.addImportForItem(context.getFile(), context.getStartOffset(), item);
  }


}
