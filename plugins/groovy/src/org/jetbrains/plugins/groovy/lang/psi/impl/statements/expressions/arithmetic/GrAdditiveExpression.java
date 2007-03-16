package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrAdditiveExpression extends GroovyPsiElementImpl {

  public GrAdditiveExpression(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Additive expression";
  }
}
