package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrInterfaceDefinition;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */
public class GrInterfaceDefinitionImpl extends GroovyPsiElementImpl implements GrInterfaceDefinition {
  public GrInterfaceDefinitionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Interface definition";
  }
}