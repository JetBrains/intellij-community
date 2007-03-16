package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.logical;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.logical.GrInclusiveOrExpression;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrInclusiveOrExprImpl extends GroovyPsiElementImpl implements GrInclusiveOrExpression {

  public GrInclusiveOrExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Inclusive OR expression";
  }
}
