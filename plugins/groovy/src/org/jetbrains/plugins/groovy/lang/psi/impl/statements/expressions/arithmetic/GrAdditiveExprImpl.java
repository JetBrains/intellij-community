package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrAdditiveExprImpl extends GroovyPsiElementImpl implements GrBinaryExpression {

  public GrAdditiveExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Additive expression";
  }
}
