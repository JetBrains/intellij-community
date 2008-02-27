package org.jetbrains.plugins.groovy.structure.itemsPresentations.impl;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.structure.itemsPresentations.GroovyItemPresentation;
import com.intellij.psi.PsiType;

/**
 * User: Dmitry.Krasilschikov
 * Date: 31.10.2007
 */
public class GroovyVariableItemPresentation extends GroovyItemPresentation {
  private final GrVariable myVariable;

  public GroovyVariableItemPresentation(GrVariable variable) {
    super(variable);

    myVariable = variable;
  }

  public String getPresentableText() {
    StringBuffer presentableText = new StringBuffer();

    presentableText.append(myVariable.getName());
    GrTypeElement varTypeElement = myVariable.getTypeElementGroovy();

    if (varTypeElement != null) {
      PsiType varType = varTypeElement.getType();
      presentableText.append(":");
      presentableText.append(varType.getPresentableText());
    }
    return presentableText.toString();
  }
}
