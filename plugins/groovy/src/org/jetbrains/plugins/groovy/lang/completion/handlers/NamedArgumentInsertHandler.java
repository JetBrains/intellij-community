// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.completion.handlers;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Maxim.Medvedev
 */
public final class NamedArgumentInsertHandler implements InsertHandler<LookupElement> {

  public static final NamedArgumentInsertHandler INSTANCE = new NamedArgumentInsertHandler();

  private NamedArgumentInsertHandler() {}

  @Override
  public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
    int tailOffset = context.getTailOffset();

    PsiElement argumentList = context.getFile().findElementAt(tailOffset - 1);
    while (argumentList != null && !(argumentList instanceof GrArgumentList) && !(argumentList instanceof GrListOrMap)) {
      argumentList = argumentList.getParent();
    }

    final Editor editor = context.getEditor();

    if (argumentList != null) {
      CodeStyleSettings settings = CodeStyle.getSettings(context.getFile());
      GroovyCodeStyleSettings codeStyleSettings = settings.getCustomSettings(GroovyCodeStyleSettings.class);
      CommonCodeStyleSettings commonCodeStyleSettings = settings.getCommonSettings(GroovyLanguage.INSTANCE);

      boolean insertSpace = codeStyleSettings.SPACE_IN_NAMED_ARGUMENT;

      if (context.getCompletionChar() == ':' || (insertSpace && context.getCompletionChar() == ' ')) {
        context.setAddCompletionChar(false);
      }

      String argumentListText = argumentList.getText();

      String s = argumentListText.substring(tailOffset - argumentList.getTextOffset());
      s = StringUtil.trimEnd(s, ")");

      if (s.trim().isEmpty()) {
        String toInsert = insertSpace ? ": " : ":";
        editor.getDocument().insertString(tailOffset, toInsert);
        editor.getCaretModel().moveToOffset(tailOffset + toInsert.length());
      }
      else {
        if (context.getCompletionChar() == Lookup.REPLACE_SELECT_CHAR) {
          char a = s.charAt(0);
          if (Character.isLetterOrDigit(a)) {
            return;
          }
        }

        Matcher m = Pattern.compile("([ \\t]*):([ \\t]*)(.*)", Pattern.DOTALL).matcher(s);
        if (m.matches()) {
          int caret = tailOffset + m.end(2);

          if (m.group(2).isEmpty()) {
            editor.getDocument().insertString(caret, " ");
            caret++;
          }

          editor.getCaretModel().moveToOffset(caret);
        }
        else {
          m = Pattern.compile("([ \\t]*)([\\n \\t]*)[\\],](.*)", Pattern.DOTALL).matcher(s);
          if (m.matches()) {
            String toInsert = insertSpace ? ": " : ":";
            editor.getDocument().replaceString(tailOffset, tailOffset + m.start(2), toInsert);
            editor.getCaretModel().moveToOffset(tailOffset + toInsert.length());
          }
          else {
            m = Pattern.compile("([ \\t]*)(.*)", Pattern.DOTALL).matcher(s);
            if (!m.matches()) throw new RuntimeException("This pattern must match any non-empty string! (" + s + ")");

            StringBuilder sb = new StringBuilder(3);
            sb.append(':');
            int shiftCaret = 1;
            if (insertSpace) {
              sb.append(' ');
              shiftCaret++;
            }

            if (!m.group(2).startsWith("\n") && commonCodeStyleSettings.SPACE_AFTER_COMMA) {
              sb.append(' ');
            }

            editor.getDocument().replaceString(tailOffset, tailOffset + m.start(2), sb);
            editor.getCaretModel().moveToOffset(tailOffset + shiftCaret);
          }
        }
      }

      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      editor.getSelectionModel().removeSelection();
    }
  }
}
