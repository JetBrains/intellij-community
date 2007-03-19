package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrConditionalExprImpl extends GroovyPsiElementImpl implements GrConditionalExpression {

  public GrConditionalExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Conditional expression";
  }
}