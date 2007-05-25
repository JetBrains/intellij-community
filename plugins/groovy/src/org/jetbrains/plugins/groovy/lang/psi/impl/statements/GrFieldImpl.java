package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrCatchClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * User: Dmitry.Krasilschikov
 * Date: 25.05.2007
 */
public class GrFieldImpl extends GroovyPsiElementImpl /*implements GrField*/ {
  public GrFieldImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Field";
  }
}
