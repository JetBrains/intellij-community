package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.03.2007
 */
public class GrImplementsClauseImpl extends GroovyPsiElementImpl implements GrImplementsClause {
  public GrImplementsClauseImpl (@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Implements clause";
  }
}
