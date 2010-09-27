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

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.structure.elements.impl.GroovyFileStructureViewElement;

/**
 * User: Dmitry.Krasilschikov
 * Date: 21.05.2007
 */

public class GroovyStructureViewModel extends TextEditorBasedStructureViewModel {
  private final GroovyFileBase myRootElement;

  private static final Class[] SUITABLE_CLASSES =
    new Class[]{GroovyFileBase.class, GrTypeDefinition.class, GrMethod.class, GrVariable.class};

  public GroovyStructureViewModel(GroovyFileBase rootElement) {
    super(rootElement);
    myRootElement = rootElement;
  }

  protected PsiFile getPsiFile() {
    return myRootElement;
  }

  @NotNull
  public StructureViewTreeElement getRoot() {
    return new GroovyFileStructureViewElement(myRootElement);
  }

  @NotNull
  public Sorter[] getSorters() {
    return new Sorter[]{GroovyKindSorter.INSTANCE, Sorter.ALPHA_SORTER};
  }

  @NotNull
  public Filter[] getFilters() {
    return new Filter[]{new GroovyInheritFilter()};
  }

  @Override
  public boolean shouldEnterElement(Object element) {
    return element instanceof GrTypeDefinition;
  }

  @NotNull
  protected Class[] getSuitableClasses() {
    return SUITABLE_CLASSES;
  }

  @Nullable
  protected Object findAcceptableElement(PsiElement element) {
    while (element != null && !(element instanceof PsiDirectory)) {
      if (isSuitable(element)) {
        if (element instanceof GroovyFileBase && ((GroovyFileBase)element).getTypeDefinitions().length == 1) {
          return ((GroovyFileBase)element).getTypeDefinitions()[0];
        }
        return element;
      }
      element = element.getParent();
    }
    return null;
  }

  @Override
  protected boolean isSuitable(final PsiElement element) {
    if (super.isSuitable(element)) {
      if (element instanceof GrMethod) {
        GrMethod method = (GrMethod)element;
        PsiElement parent = method.getParent().getParent();
        if (parent instanceof GrTypeDefinition) {
          return ((GrTypeDefinition)parent).getQualifiedName() != null;
        }
      }
      else if (element instanceof GrVariable) {
        GrVariable field = (GrVariable)element;
        PsiElement parent = field.getParent().getParent().getParent();
        if (parent instanceof GrTypeDefinition) {
          return ((GrTypeDefinition)parent).getQualifiedName() != null;
        }
      }
      else if (element instanceof GrTypeDefinition) {
        return ((GrTypeDefinition)element).getQualifiedName() != null;
      } else if (element instanceof GroovyFileBase) {
        return true;
      }
    }
    return false;
  }
}