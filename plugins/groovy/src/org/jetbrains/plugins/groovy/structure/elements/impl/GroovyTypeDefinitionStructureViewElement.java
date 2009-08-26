package org.jetbrains.plugins.groovy.structure.elements.impl;

import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrSyntheticMethod;
import org.jetbrains.plugins.groovy.structure.elements.GroovyStructureViewElement;
import org.jetbrains.plugins.groovy.structure.itemsPresentations.impl.GroovyTypeDefinitionItemPresentation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GroovyTypeDefinitionStructureViewElement extends GroovyStructureViewElement {
  public GroovyTypeDefinitionStructureViewElement(GrTypeDefinition typeDefinition) {
    super(typeDefinition);
  }

  public ItemPresentation getPresentation() {
    return new GroovyTypeDefinitionItemPresentation(((GrTypeDefinition)myElement));
  }

  public TreeElement[] getChildren() {
    List<GroovyStructureViewElement> children = new ArrayList<GroovyStructureViewElement>();

    //adding statements for type definition
    final GrTypeDefinition typeDefinition = (GrTypeDefinition)myElement;
    for (GrField field : typeDefinition.getFields()) {
      children.add(new GroovyVariableStructureViewElement(field));
    }

    List<PsiMethod> methods = Arrays.asList(typeDefinition.getMethods());
    for (PsiMethod method : typeDefinition.getAllMethods()) {
      if (method instanceof GrSyntheticMethod) continue;

      if (methods.contains(method)) {
        children.add(new GroovyMethodStructureViewElement(method, false));
      }
      else {
        children.add(new GroovyMethodStructureViewElement(method, true));
      }
    }

    return children.toArray(new GroovyStructureViewElement[children.size()]);
  }
}

