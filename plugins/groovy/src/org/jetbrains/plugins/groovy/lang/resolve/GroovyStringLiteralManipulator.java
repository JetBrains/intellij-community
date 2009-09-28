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
    if (text.length() > 6 && text.startsWith("\"\"\"") && text.endsWith("\"\"\"")) {
      return new TextRange(3, element.getTextLength() - 3);
    }
    return new TextRange(1, Math.max(1, element.getTextLength() - 1));
  }
}