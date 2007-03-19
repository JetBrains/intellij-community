package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic;

import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrSimplePowerExprImpl extends GrPowerExprImpl{

  public GrSimplePowerExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Simple power expression";
  }
}
