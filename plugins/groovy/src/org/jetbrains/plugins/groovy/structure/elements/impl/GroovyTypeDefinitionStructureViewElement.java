/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.structure.elements.impl;

import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.structure.elements.GroovyStructureViewElement;
import org.jetbrains.plugins.groovy.structure.itemsPresentations.GroovyItemPresentation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GroovyTypeDefinitionStructureViewElement extends GroovyStructureViewElement {
  public GroovyTypeDefinitionStructureViewElement(GrTypeDefinition typeDefinition) {
    super(typeDefinition);
  }

  public ItemPresentation getPresentation() {
    return new GroovyItemPresentation(myElement) {
      public String getPresentableText() {
        return ((GrTypeDefinition) myElement).getNameIdentifierGroovy().getText();
      }
    };
  }

  public TreeElement[] getChildren() {
    List<GroovyStructureViewElement> children = new ArrayList<GroovyStructureViewElement>();

    //adding statements for type definition
    final GrTypeDefinition typeDefinition = (GrTypeDefinition)myElement;
    for (PsiClass innerClass : typeDefinition.getInnerClasses()) {
      if (innerClass instanceof GrTypeDefinition) {
        children.add(new GroovyTypeDefinitionStructureViewElement((GrTypeDefinition)innerClass));
      }
    }

    for (GrField field : typeDefinition.getFields()) {
      children.add(new GroovyVariableStructureViewElement(field));
    }

    List<PsiMethod> methods = Arrays.asList(typeDefinition.getMethods());
    for (PsiMethod method : typeDefinition.getAllMethods()) {
      if (!method.isPhysical()) continue;

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

