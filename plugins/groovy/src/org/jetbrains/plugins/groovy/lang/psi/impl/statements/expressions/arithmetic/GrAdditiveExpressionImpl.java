package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.arithmetic.GrAdditiveExpression;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrAdditiveExpressionImpl extends GroovyPsiElementImpl implements GrAdditiveExpression {

  public GrAdditiveExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Additive expression";
  }
}
