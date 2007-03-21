package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCallExpression;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrCallExpressionImpl extends GroovyPsiElementImpl implements GrCallExpression {

  public GrCallExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Call expression";
  }
}