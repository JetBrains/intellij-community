package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.logical;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.logical.GrLogicalAndExpression;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrLogicalAndExprImpl extends GroovyPsiElementImpl implements GrLogicalAndExpression {

  public GrLogicalAndExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Logical AND expression";
  }

}