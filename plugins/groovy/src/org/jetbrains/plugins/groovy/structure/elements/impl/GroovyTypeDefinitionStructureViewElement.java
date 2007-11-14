package org.jetbrains.plugins.groovy.structure.elements.impl;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiMethod;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.structure.elements.GroovyStructureViewElement;
import org.jetbrains.plugins.groovy.structure.elements.GroovyStructureElementUtils;
import org.jetbrains.plugins.groovy.structure.itemsPresentations.impl.GroovyTypeDefinitionItemPresentation;

import java.util.*;

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
    GrMembersDeclaration[] declarations = typeDefinition.getMemberDeclarations();
    final List<PsiMethod> allMethods = new ArrayList<PsiMethod>(Arrays.asList(typeDefinition.getAllMethods()));

    for (GrMembersDeclaration declaration : declarations) {
      if (declaration instanceof GrVariableDeclaration) {
        GroovyStructureElementUtils.addVariables(children, (GrVariableDeclaration) declaration);
      } else if (declaration instanceof GrMethod) {
        children.add(new GroovyMethodStructureViewElement(((GrMethod) declaration), false));
        allMethods.remove(declaration);
      }
    }

    //adding super types
    for (PsiMethod superMethod : allMethods) {
      children.add(new GroovyMethodStructureViewElement(superMethod, true));
    }

    return children.toArray(new GroovyStructureViewElement[children.size()]);
  }
}

