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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

public class GroovyStringLiteralManipulator extends AbstractElementManipulator<GrLiteral> {
  public GrLiteral handleContentChange(GrLiteral expr, TextRange range, String newContent) throws IncorrectOperationException {
    if (!(expr.getValue() instanceof String)) throw new IncorrectOperationException("cannot handle content change");
    String oldText = expr.getText();
    newContent = StringUtil.escapeStringCharacters(newContent);
    String newText = oldText.substring(0, range.getStartOffset()) + newContent + oldText.substring(range.getEndOffset());
    final GrExpression newExpr = GroovyPsiElementFactory.getInstance(expr.getProject()).createExpressionFromText(newText);
    return (GrLiteral)expr.replace(newExpr);
  }

  public TextRange getRangeInElement(final GrLiteral element) {
    final String text = element.getText();
    return getLiteralRange(text);
  }

  public static TextRange getLiteralRange(String text) {
    if (text.length() > 6 && text.startsWith("\"\"\"") && text.endsWith("\"\"\"")) {
      return new TextRange(3, text.length() - 3);
    }
    return new TextRange(1, Math.max(1, text.length() - 1));
  }
}