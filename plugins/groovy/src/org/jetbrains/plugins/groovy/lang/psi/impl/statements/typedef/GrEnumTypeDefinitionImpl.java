package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumTypeDefinition;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 18.03.2007
 */
public class GrEnumTypeDefinitionImpl extends GroovyPsiElementImpl implements GrEnumTypeDefinition {
  public GrEnumTypeDefinitionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "enumeration definition";
  }
}
