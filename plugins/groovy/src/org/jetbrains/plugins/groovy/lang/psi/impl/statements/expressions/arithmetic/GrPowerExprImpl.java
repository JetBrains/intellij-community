package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrPowerExprImpl extends GroovyPsiElementImpl implements GrBinaryExpression {

  public GrPowerExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Power expression";
  }
}

