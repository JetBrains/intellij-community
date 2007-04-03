package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary;

import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrThrowClause;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 03.04.2007
 */
public class GrThrowClauseImpl extends GroovyPsiElementImpl implements GrThrowClause {
  public GrThrowClauseImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Throw clause";
  }
}
