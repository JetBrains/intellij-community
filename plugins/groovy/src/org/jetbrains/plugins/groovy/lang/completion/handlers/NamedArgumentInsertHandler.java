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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Maxim.Medvedev
 */
public class NamedArgumentInsertHandler implements InsertHandler<LookupElement> {

  public static final NamedArgumentInsertHandler INSTANCE = new NamedArgumentInsertHandler();

  private NamedArgumentInsertHandler() {

  }

  public void handleInsert(InsertionContext context, LookupElement item) {
    int tailOffset = context.getTailOffset();

    PsiElement argumentList = context.getFile().findElementAt(tailOffset - 1);
    while (argumentList != null && !(argumentList instanceof GrArgumentList) && !(argumentList instanceof GrListOrMap)) {
      argumentList = argumentList.getParent();
    }

    final Editor editor = context.getEditor();

    if (argumentList != null) {
      String argumentListText = argumentList.getText();

      String s = argumentListText.substring(tailOffset - argumentList.getTextOffset());
      s = StringUtil.trimEnd(s, ")");

      if (s.trim().length() == 0) {
        editor.getDocument().insertString(tailOffset, ": ");
        editor.getCaretModel().moveToOffset(tailOffset + 2);
      }
      else {
        Matcher m = Pattern.compile("(\\s*)(:)?(\\s*),?\\s?(\\s*)(.*)", Pattern.DOTALL).matcher(s);
        if (!m.matches()) throw new RuntimeException("This pattern must match any non-empty string! (" + s + ")");

        if (m.group(2) != null) {
          editor.getCaretModel().moveToOffset(tailOffset + m.end(3));
        }
        else {
          String toInsert = m.group(5).startsWith("]") ? ": " : ": , ";
          editor.getDocument().replaceString(tailOffset, tailOffset + m.start(4), toInsert);
          editor.getCaretModel().moveToOffset(tailOffset + 2);
        }
      }
    }

    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().removeSelection();

    if (context.getCompletionChar() == ':' || context.getCompletionChar() == ' ') {
      context.setAddCompletionChar(false);
    }
  }
}
