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
