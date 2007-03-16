package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.logical;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.logical.GrExclusiveOrExpression;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrExclusiveOrExprImpl extends GroovyPsiElementImpl implements GrExclusiveOrExpression {

  public GrExclusiveOrExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Exclusive OR expression";
  }
}