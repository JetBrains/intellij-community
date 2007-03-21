package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrPathSelector;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrPathSelectorImpl extends GroovyPsiElementImpl implements GrPathSelector {

  public GrPathSelectorImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Path selector";
  }
}
