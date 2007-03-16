package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.logical;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.logical.GrLogicalOrExpression;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrLogicalOrExprImpl extends GroovyPsiElementImpl implements GrLogicalOrExpression {

  public GrLogicalOrExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Logical OR expression";
  }
}
