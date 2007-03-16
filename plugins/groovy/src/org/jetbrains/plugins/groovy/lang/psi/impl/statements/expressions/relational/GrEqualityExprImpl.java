package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.relational;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.relational.GrEqualityExpression;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrEqualityExprImpl extends GroovyPsiElementImpl implements GrEqualityExpression {

  public GrEqualityExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Equality expression";
  }

}