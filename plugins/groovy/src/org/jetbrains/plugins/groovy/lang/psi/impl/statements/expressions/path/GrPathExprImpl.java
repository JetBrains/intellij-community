package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrPathSelector;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrPathExpression;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrPathExprImpl extends GroovyPsiElementImpl implements GrPathExpression {

  public GrPathExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Path expression";
  }
}
