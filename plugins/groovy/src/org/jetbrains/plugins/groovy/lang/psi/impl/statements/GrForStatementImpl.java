package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrForStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 18.03.2007
 */
public class GrForStatementImpl extends GroovyPsiElementImpl implements GrForStatement {
  public GrForStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "For statement";
  }
}
