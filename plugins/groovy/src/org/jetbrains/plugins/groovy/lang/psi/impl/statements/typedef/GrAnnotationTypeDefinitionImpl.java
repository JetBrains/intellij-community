package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnnotationTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 18.03.2007
 */
public class GrAnnotationTypeDefinitionImpl extends GroovyPsiElementImpl implements GrAnnotationTypeDefinition {
  public GrAnnotationTypeDefinitionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Annotation definition";
  }
}
