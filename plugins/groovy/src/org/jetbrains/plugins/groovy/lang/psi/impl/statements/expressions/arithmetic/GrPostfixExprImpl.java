package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrPostfixExpression;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrPostfixExprImpl extends GroovyPsiElementImpl implements GrPostfixExpression {

  public GrPostfixExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Postfix expression";
  }
}