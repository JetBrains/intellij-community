// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.structureView.impl;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.Language;
import com.intellij.navigation.ItemPresentation;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public class StructureViewElementWrapper<V extends PsiElement> implements StructureViewTreeElement {
  private final StructureViewTreeElement myTreeElement;
  private final PsiFile myMainFile;

  StructureViewElementWrapper(@NotNull StructureViewTreeElement treeElement, @NotNull PsiFile mainFile) {
    myTreeElement = treeElement;
    myMainFile = mainFile;
  }

  public @NotNull StructureViewTreeElement getWrappedElement() {
    return myTreeElement;
  }

  @Override
  public V getValue() {
    return (V)myTreeElement.getValue();
  }

  @Override
  public StructureViewTreeElement @NotNull [] getChildren() {
    TreeElement[] baseChildren = myTreeElement.getChildren();
    List<StructureViewTreeElement> result = new ArrayList<>();
    for (TreeElement element : baseChildren) {
      StructureViewTreeElement wrapper = new StructureViewElementWrapper((StructureViewTreeElement)element, myMainFile);

      result.add(wrapper);
    }
    return result.toArray(StructureViewTreeElement.EMPTY_ARRAY);
  }

  @Override
  public @NotNull ItemPresentation getPresentation() {
    return myTreeElement.getPresentation();
  }

  @Override
  public void navigate(final boolean requestFocus) {
    Navigatable navigatable = getNavigatableInTemplateLanguageFile();
    if (navigatable != null) {
      navigatable.navigate(requestFocus);
    }
  }

  private @Nullable Navigatable getNavigatableInTemplateLanguageFile() {
    PsiElement element = (PsiElement)myTreeElement.getValue();
    if (element == null) return null;

    int offset = element.getTextRange().getStartOffset();
    final Language dataLanguage = ((TemplateLanguageFileViewProvider)myMainFile.getViewProvider()).getTemplateDataLanguage();
    final PsiFile dataFile = myMainFile.getViewProvider().getPsi(dataLanguage);
    if (dataFile == null) return null;

    PsiElement tlElement = dataFile.findElementAt(offset);
    while (tlElement != null && tlElement.getTextRange().getStartOffset() == offset) {
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
