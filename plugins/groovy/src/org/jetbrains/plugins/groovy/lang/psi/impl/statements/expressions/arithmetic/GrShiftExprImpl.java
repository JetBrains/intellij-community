package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrShiftExprImpl extends GroovyPsiElementImpl implements GrBinaryExpression {

  public GrShiftExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Shift expression";
  }
}
