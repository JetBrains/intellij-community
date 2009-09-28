package org.jetbrains.plugins.groovy.structure.itemsPresentations.impl;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.structure.GroovyElementPresentation;
import org.jetbrains.plugins.groovy.structure.itemsPresentations.GroovyItemPresentation;

/**
 * User: Dmitry.Krasilschikov
  * Date: 30.10.2007
  */
 public class GroovyTypeDefinitionItemPresentation extends GroovyItemPresentation {
  public GroovyTypeDefinitionItemPresentation(GrTypeDefinition myElement) {
    super(myElement);
  }

  public String getPresentableText() {
    return GroovyElementPresentation.getTypeDefinitionPresentableText((GrTypeDefinition) myElement);
  }
}
