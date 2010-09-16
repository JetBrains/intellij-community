/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;

/**
 * @author Maxim.Medvedev
 */
public class NamedArgumentInsertHandler implements InsertHandler<LookupElement> {

  public static final NamedArgumentInsertHandler INSTANCE = new NamedArgumentInsertHandler();

  private NamedArgumentInsertHandler() {

  }

  public void handleInsert(InsertionContext context, LookupElement item) {
    int tailOffset = context.getTailOffset();
    final Editor editor = context.getEditor();
    editor.getDocument().insertString(tailOffset, ": ");
    editor.getCaretModel().moveToOffset(tailOffset + 2);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().removeSelection();
  }
}
