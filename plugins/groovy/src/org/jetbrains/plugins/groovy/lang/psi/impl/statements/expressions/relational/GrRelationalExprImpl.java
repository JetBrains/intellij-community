package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.relational;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrRelationalExpression;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrRelationalExprImpl extends GroovyPsiElementImpl implements GrRelationalExpression {

  public GrRelationalExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Relational expression";
  }

}