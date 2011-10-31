/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.structure.elements.GroovyStructureViewElement;
import org.jetbrains.plugins.groovy.structure.itemsPresentations.GroovyItemPresentation;

import java.util.LinkedHashSet;
import java.util.Set;

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
    if (!myElement.isValid()) {
      return EMPTY_ARRAY;
    }

    Set<GroovyStructureViewElement> children = new LinkedHashSet<GroovyStructureViewElement>();

    //adding statements for type definition
    final GrTypeDefinition typeDefinition = (GrTypeDefinition)myElement;
    for (PsiClass innerClass : typeDefinition.getInnerClasses()) {
      if (innerClass instanceof GrTypeDefinition) {
        children.add(new GroovyTypeDefinitionStructureViewElement((GrTypeDefinition)innerClass));
      }
    }

    Set<PsiField> fields = new HashSet<PsiField>();
    ContainerUtil.addAll(fields, typeDefinition.getFields());

    for (PsiField field : typeDefinition.getAllFields()) {
      final boolean contains = fields.contains(field);
      if (contains || !field.hasModifierProperty(PsiModifier.PRIVATE)) {
        children.add(new GroovyVariableStructureViewElement(field, !contains));
      }
    }

    Set<PsiMethod> methods = new HashSet<PsiMethod>();
    ContainerUtil.addAll(methods, typeDefinition.getGroovyMethods());

    for (PsiMethod method : typeDefinition.getAllMethods()) {
      if (!method.isPhysical()) continue;

      if (method instanceof GrReflectedMethod) {
        method = ((GrReflectedMethod)method).getBaseMethod();
      }
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

