package org.jetbrains.plugins.groovy.lang.psi.impl.statements.params;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.03.2007
 */
public class GrParameterImpl extends GroovyPsiElementImpl implements GrParameterList {
  public GrParameterImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "parameter";
  }
}
