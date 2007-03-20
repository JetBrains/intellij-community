package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCommandArguments;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrCommandArgsImpl extends GroovyPsiElementImpl implements GrCommandArguments {

  public GrCommandArgsImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Command arguments";
  }
}
