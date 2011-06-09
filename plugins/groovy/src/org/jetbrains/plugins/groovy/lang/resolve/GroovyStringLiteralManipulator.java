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
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

public class GroovyStringLiteralManipulator extends AbstractElementManipulator<GrLiteral> {
  public GrLiteral handleContentChange(GrLiteral expr, TextRange range, String newContent) throws IncorrectOperationException {
    if (!(expr.getValue() instanceof String)) throw new IncorrectOperationException("cannot handle content change");
    String oldText = expr.getText();
    if (oldText.startsWith("'")) {
      newContent = GrStringUtil.escapeSymbolsForString(newContent, !oldText.startsWith("'''"), true);
    }
    else {
      newContent = GrStringUtil.escapeSymbolsForGString(newContent, !oldText.startsWith("\"\"\""), true);
    }
    String newText;
    if (range.getStartOffset() == 1 && (newContent.indexOf('\n') >= 0 || newContent.indexOf('\r') >= 0)) {
      String corner = oldText.substring(0, 1) + oldText.substring(0, 1) + oldText.substring(0, 1);
      newText = corner + newContent + corner;
    }
    else {
      newText = oldText.substring(0, range.getStartOffset()) + newContent + oldText.substring(range.getEndOffset());
    }
    final GrExpression newExpr = GroovyPsiElementFactory.getInstance(expr.getProject()).createExpressionFromText(newText);

    PsiElement firstChild = expr.getFirstChild();
    assert firstChild != null && firstChild.getNextSibling() == null;

    PsiElement newElement = newExpr.getFirstChild();
    assert newElement != null;
    firstChild.replace(newElement);

    return expr;
  }

  public TextRange getRangeInElement(final GrLiteral element) {
    final String text = element.getText();
    return getLiteralRange(text);
  }

  public static TextRange getLiteralRange(String text) {
    int start = 1;
    int fin = text.length();

    String begin = text.substring(0, 1);
    if (text.startsWith("\"\"\"") || text.startsWith("'''")) {
      start += 2;
      begin = text.substring(0, 3);
    }

    if (text.length() >= begin.length()*2 && text.endsWith(begin)) {
      fin -= begin.length();
    }
    return new TextRange(start, Math.max(1, fin));
  }
}