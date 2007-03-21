package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrReferenceExprImpl extends GroovyPsiElementImpl implements GrReferenceExpression {

  public GrReferenceExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Reference expression";
  }
}