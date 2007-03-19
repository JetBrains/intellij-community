package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrUnaryExprImpl extends GroovyPsiElementImpl implements GrUnaryExpression {

  public GrUnaryExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Unary expression";
  }
}