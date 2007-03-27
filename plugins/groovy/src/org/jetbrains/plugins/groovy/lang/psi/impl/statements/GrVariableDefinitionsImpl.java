package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrWhileStatement;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 27.03.2007
 */
public class GrVariableDefinitionsImpl extends GroovyPsiElementImpl implements GrWhileStatement {
  public GrVariableDefinitionsImpl(@NotNull ASTNode node) {
    super(node);
  }

   public String toString() {
    return "Variable definitions";
  }
}
