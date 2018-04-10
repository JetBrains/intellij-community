// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteralContainer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringContent;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

public class GroovyStringLiteralManipulator extends AbstractElementManipulator<GrLiteralContainer> {

  @Override
  public GrLiteralContainer handleContentChange(@NotNull GrLiteralContainer expr, @NotNull TextRange range, String newContent) throws IncorrectOperationException {
    if (!(expr.getValue() instanceof String)) {
//      throw new IncorrectOperationException("cannot handle content change, expr.getValue()=" + expr.getValue());
    }

    final String oldText = expr instanceof GrLiteral ? expr.getText() : expr.getParent().getText();
    final String startQuote = GrStringUtil.getStartQuote(oldText);

    if (StringUtil.startsWithChar(startQuote, '\'')) {
      newContent = GrStringUtil.escapeSymbolsForString(newContent, !startQuote.equals("'''"), true);
    }
    else if (StringUtil.startsWithChar(startQuote, '\"')) {
      newContent = GrStringUtil.escapeSymbolsForGString(newContent, !startQuote.equals("\"\"\""), false);
    }
    else if ("/".equals(startQuote)) {
      newContent = GrStringUtil.escapeSymbolsForSlashyStrings(newContent);
    }
    else if ("$/".equals(startQuote)) {
      newContent = GrStringUtil.escapeSymbolsForDollarSlashyStrings(newContent);
    }

    String newText = expr instanceof GrLiteral
                     ? oldText.substring(0, range.getStartOffset()) + newContent + oldText.substring(range.getEndOffset())
                     : newContent;
    return expr.updateText(newText);
  }

  @NotNull
  @Override
  public TextRange getRangeInElement(@NotNull final GrLiteralContainer element) {
    if (element instanceof GrStringContent) {
      return TextRange.from(0, element.getTextLength());
    }
    final String text = element.getText();
    if (!(element.getValue() instanceof String)) {
      return super.getRangeInElement(element);
    }
    return getLiteralRange(text);
  }

  public static TextRange getLiteralRange(String text) {
    int start = 1;
    int fin = text.length();

    String begin = text.substring(0, 1);
    if (text.startsWith("$/")) {
      start = 2;
      if (text.endsWith("/$")) {
        return new TextRange(start, Math.max(1, fin - 2));
      }
      else {
        return new TextRange(start, fin);
      }
    }

    if (text.startsWith("\"\"\"") || text.startsWith("'''")) {
      start = 3;
      begin = text.substring(0, 3);
    }

    if (text.length() >= begin.length() * 2 && text.endsWith(begin)) {
      fin -= begin.length();
    }
    return new TextRange(start, Math.max(1, fin));
  }
}
