/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

public class GroovyStringLiteralManipulator extends AbstractElementManipulator<GrLiteral> {
  private static final Logger LOG = Logger.getInstance(GroovyStringLiteralManipulator.class);

  public GrLiteralImpl handleContentChange(GrLiteral expr, TextRange range, String newContent) throws IncorrectOperationException {
    if (!(expr.getValue() instanceof String)) {
      throw new IncorrectOperationException("cannot handle content change, expr.getValue()=" + expr.getValue());
    }

    LOG.assertTrue(expr instanceof GrLiteralImpl);

    String oldText = expr.getText();
    final String quote = GrStringUtil.getStartQuote(oldText);

    if (StringUtil.startsWithChar(quote, '\'')) {
      newContent = GrStringUtil.escapeSymbolsForString(newContent, !quote.equals("'''"), true);
    }
    else if (StringUtil.startsWithChar(quote, '\"')) {
      newContent = GrStringUtil.escapeSymbolsForGString(newContent, !quote.equals("\"\"\""), true);
    }
    else if ("/".equals(quote)) {
      newContent = GrStringUtil.escapeSymbolsForSlashyStrings(newContent);
    }
    else if ("$/".equals(quote)) {
      newContent = GrStringUtil.escapeSymbolsForDollarSlashyStrings(newContent);
    }

    String newText = oldText.substring(0, range.getStartOffset()) + newContent + oldText.substring(range.getEndOffset());
    return ((GrLiteralImpl)expr).updateText(newText);
  }

  public TextRange getRangeInElement(final GrLiteral element) {
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
      start += 2;
      begin = text.substring(0, 3);
    }

    if (text.length() >= begin.length() * 2 && text.endsWith(begin)) {
      fin -= begin.length();
    }
    return new TextRange(start, Math.max(1, fin));
  }
}