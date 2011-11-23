package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.resolve.GroovyStringLiteralManipulator;


public abstract class GrAbstractLiteral extends GrExpressionImpl implements GrLiteral {

  public GrAbstractLiteral(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void setStringValue(String value) {
    new GroovyStringLiteralManipulator().handleContentChange(this, value);

  }
}
