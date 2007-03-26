package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.03.2007
 */
public class GrExtendsClauseImpl extends GroovyPsiElementImpl implements GrExtendsClause {
  public GrExtendsClauseImpl (@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Extends clause";
  }
}
