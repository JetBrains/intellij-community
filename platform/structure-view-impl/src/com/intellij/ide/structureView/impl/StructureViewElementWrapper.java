/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.ide.structureView.impl;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.Language;
import com.intellij.navigation.ItemPresentation;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author cdr
 */
public class StructureViewElementWrapper<V extends PsiElement> implements StructureViewTreeElement {
  private final StructureViewTreeElement myTreeElement;
  private final PsiFile myMainFile;

  public StructureViewElementWrapper(@NotNull StructureViewTreeElement treeElement, @NotNull PsiFile mainFile) {
    myTreeElement = treeElement;
    myMainFile = mainFile;
  }

  public StructureViewTreeElement getWrappedElement() {
    return myTreeElement;
  }

  @Override
  public V getValue() {
    return (V)myTreeElement.getValue();
  }

  @NotNull
  @Override
  public StructureViewTreeElement[] getChildren() {
    TreeElement[] baseChildren = myTreeElement.getChildren();
    List<StructureViewTreeElement> result = new ArrayList<>();
    for (TreeElement element : baseChildren) {
      StructureViewTreeElement wrapper = new StructureViewElementWrapper((StructureViewTreeElement)element, myMainFile);

      result.add(wrapper);
    }
    return result.toArray(new StructureViewTreeElement[result.size()]);
  }

  @NotNull
  @Override
  public ItemPresentation getPresentation() {
    return myTreeElement.getPresentation();
  }

  @Override
  public void navigate(final boolean requestFocus) {
    Navigatable navigatable = getNavigatableInTemplateLanguageFile();
    if (navigatable != null) {
      navigatable.navigate(requestFocus);
    }
  }

  @Nullable
  private Navigatable getNavigatableInTemplateLanguageFile() {
    PsiElement element = (PsiElement)myTreeElement.getValue();
    if (element == null) return null;

    int offset = element.getTextRange().getStartOffset();
    final Language dataLanguage = ((TemplateLanguageFileViewProvider)myMainFile.getViewProvider()).getTemplateDataLanguage();
    final PsiFile dataFile = myMainFile.getViewProvider().getPsi(dataLanguage);
    if (dataFile == null) return null;

    PsiElement tlElement = dataFile.findElementAt(offset);
    while(true) {
      if (tlElement == null || tlElement.getTextRange().getStartOffset() != offset) break;
      if (tlElement instanceof Navigatable) {
        return (Navigatable)tlElement;
      }
      tlElement = tlElement.getParent();
    }
    return null;
  }

  @Override
  public boolean canNavigate() {
    return getNavigatableInTemplateLanguageFile() != null;
  }

  @Override
  public boolean canNavigateToSource() {
    return canNavigate();
  }
}
