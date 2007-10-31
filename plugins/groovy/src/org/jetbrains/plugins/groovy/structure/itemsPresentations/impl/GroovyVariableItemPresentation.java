package org.jetbrains.plugins.groovy.structure.itemsPresentations.impl;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.structure.GroovyElementPresentation;
import org.jetbrains.plugins.groovy.structure.itemsPresentations.GroovyItemPresentation;

/**
 * User: Dmitry.Krasilschikov
 * Date: 31.10.2007
 */
public class GroovyVariableItemPresentation extends GroovyItemPresentation {
  private final GrVariable myVariable;
  private final GrVariableDeclaration myVariableDeclaration;

  public GroovyVariableItemPresentation(GrVariable variable, GrVariableDeclaration myVariableDeclaration) {
    super(variable);

    myVariable = variable;
    this.myVariableDeclaration = myVariableDeclaration;
  }

  public String getPresentableText() {
    return GroovyElementPresentation.getVariablePresentableText(myVariableDeclaration, myVariable.getName());
  }
}
