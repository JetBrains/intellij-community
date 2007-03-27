package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpr;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrParenthesizedExprImpl extends GroovyPsiElementImpl implements GrParenthesizedExpr {

  public GrParenthesizedExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Parenthesized expression";
  }
}