package org.jetbrains.plugins.groovy.structure.elements.impl;

import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.structure.elements.GroovyStructureViewElement;
import org.jetbrains.plugins.groovy.structure.itemsPresentations.impl.GroovyTypeDefinitionItemPresentation;

import java.util.ArrayList;
import java.util.List;

public class GroovyTypeDefinitionStructureViewElement extends GroovyStructureViewElement {
  public GroovyTypeDefinitionStructureViewElement(GroovyPsiElement element) {
       super(element);
     }

  public ItemPresentation getPresentation() {
       return new GroovyTypeDefinitionItemPresentation(((GrTypeDefinition) myElement));
     }

  public TreeElement[] getChildren() {
    List<GroovyStructureViewElement> children = new ArrayList<GroovyStructureViewElement>();

    //adding statements for type definition
    final GrTypeDefinition typeDefinition = (GrTypeDefinition) myElement;
    for (GrField field : typeDefinition.getFields()) {
      children.add(new GroovyVariableStructureViewElement(field));
    }

    for (GrMethod method : typeDefinition.getGroovyMethods()) {
      children.add(new GroovyMethodStructureViewElement(method, false));
    }

    return children.toArray(new GroovyStructureViewElement[children.size()]);
  }
}

