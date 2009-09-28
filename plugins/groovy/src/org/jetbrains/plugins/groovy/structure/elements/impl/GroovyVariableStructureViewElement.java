package org.jetbrains.plugins.groovy.structure.elements.impl;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.Iconable;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.structure.elements.GroovyStructureViewElement;
import org.jetbrains.plugins.groovy.structure.itemsPresentations.impl.GroovyVariableItemPresentation;

import javax.swing.*;

/**
 * User: Dmitry.Krasilschikov
* Date: 30.10.2007
*/
public class GroovyVariableStructureViewElement extends GroovyStructureViewElement {
  final Icon icon;
  private final GrVariable myVariable;

  public GroovyVariableStructureViewElement(GrVariable variable) {
    super(variable);
    myVariable = variable;
    icon = myVariable.getIcon(Iconable.ICON_FLAG_OPEN);
  }

  public ItemPresentation getPresentation() {
    return new GroovyVariableItemPresentation(myVariable);
  }

  public TreeElement[] getChildren() {
    return GroovyStructureViewElement.EMPTY_ARRAY;
  }

}
