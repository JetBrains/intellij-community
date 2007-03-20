package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCommandArguments;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrExpressionImpl extends GroovyPsiElementImpl implements GrExpression {

  public GrExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Expression with arguments";
  }
}