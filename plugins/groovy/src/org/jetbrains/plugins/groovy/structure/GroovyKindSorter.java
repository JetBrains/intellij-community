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
package org.jetbrains.plugins.groovy.structure;

import com.intellij.ide.structureView.impl.java.PropertyGroup;
import com.intellij.ide.structureView.impl.java.SuperTypeGroup;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.structure.elements.impl.GroovyMethodStructureViewElement;
import org.jetbrains.plugins.groovy.structure.elements.impl.GroovyTypeDefinitionStructureViewElement;
import org.jetbrains.plugins.groovy.structure.elements.impl.GroovyVariableStructureViewElement;

import java.util.Comparator;

public class GroovyKindSorter implements Sorter {
  public static final Sorter INSTANCE = new GroovyKindSorter();

  @NonNls public static final String ID = "KIND";
  private static final Comparator COMPARATOR = new Comparator() {
    public int compare(final Object o1, final Object o2) {
      return getWeight(o1) - getWeight(o2);
    }

    private int getWeight(final Object value) {
      if (value instanceof GroovyTypeDefinitionStructureViewElement) {
        return 10;
      }
      if (value instanceof SuperTypeGroup) {
        return 20;
      }
      if (value instanceof GroovyMethodStructureViewElement) {
        final GroovyMethodStructureViewElement methodTreeElement = (GroovyMethodStructureViewElement)value;
        final PsiMethod method = (PsiMethod)methodTreeElement.getValue();

        return method.isConstructor() ? 30 : 35;
      }
      if (value instanceof PropertyGroup) {
        return 40;
      }
      if (value instanceof GroovyVariableStructureViewElement) {
        return 50;
      }
      return 60;
    }
  };

  public Comparator getComparator() {
    return COMPARATOR;
  }

  public boolean isVisible() {
    return false;
  }

  @NotNull
  public ActionPresentation getPresentation() {
    throw new IllegalStateException();
  }

  @NotNull
  public String getName() {
    return ID;
  }
}
