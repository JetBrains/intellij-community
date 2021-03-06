// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.structureView.customRegions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.folding.CustomFoldingProvider;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Rustam Vishnyakov
 */
public class CustomRegionTreeElement implements StructureViewTreeElement {

  private final PsiElement myStartElement;
  private int myEndOffset = Integer.MAX_VALUE;
  private final Collection<StructureViewTreeElement> myChildElements = new ArrayList<>();
  private final CustomFoldingProvider myProvider;
  private final CustomRegionTreeElement myParent;
  private List<CustomRegionTreeElement> mySubRegions;

  public CustomRegionTreeElement(@NotNull PsiElement startElement,
                                 @NotNull CustomFoldingProvider provider,
                                 @Nullable CustomRegionTreeElement parent) {
    myStartElement = startElement;
    myProvider = provider;
    myParent = parent;
  }

  public CustomRegionTreeElement(@NotNull PsiElement startElement,
                                 @NotNull CustomFoldingProvider provider) {
    this(startElement, provider, null);
  }

  @Override
  public Object getValue() {
    return this;
  }

  @Override
  public void navigate(boolean requestFocus) {
    ((Navigatable)myStartElement).navigate(requestFocus);
  }

  @Override
  public boolean canNavigate() {
    return myStartElement instanceof Navigatable && ((Navigatable)myStartElement).canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    return canNavigate();
  }

  @NotNull
  @Override
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      @Nullable
      @Override
      public String getPresentableText() {
        return myProvider.getPlaceholderText(myStartElement.getText());
      }

      @Override
      public @NotNull Icon getIcon(boolean unused) {
        return AllIcons.Nodes.CustomRegion;
      }
    };
  }

  public void addChild(@NotNull StructureViewTreeElement childElement) {
    if (mySubRegions != null) {
      for (CustomRegionTreeElement subRegion : mySubRegions) {
        if (subRegion.containsElement(childElement)) {
          subRegion.addChild(childElement);
          return;
        }
      }
    }
    myChildElements.add(childElement);
  }

  @Override
  public TreeElement @NotNull [] getChildren() {
    if (mySubRegions == null || mySubRegions.isEmpty()) {
      return myChildElements.toArray(StructureViewTreeElement.EMPTY_ARRAY);
    }
    StructureViewTreeElement[] allElements = new StructureViewTreeElement[myChildElements.size() + mySubRegions.size()];
    int index = 0;
    for (StructureViewTreeElement child : myChildElements) {
      allElements[index++] = child;
    }
    for (StructureViewTreeElement subRegion : mySubRegions) {
      allElements[index++] = subRegion;
    }
    return allElements;
  }

  public boolean containsElement(StructureViewTreeElement element) {
    Object o = element.getValue();
    if (o instanceof PsiElement) {
      TextRange elementRange = ((PsiElement)o).getTextRange();
      if(elementRange.getStartOffset() >= myStartElement.getTextRange().getStartOffset() && elementRange.getEndOffset() <= myEndOffset) {
        return true;
      }
    }
    return false;
  }

  public CustomRegionTreeElement createNestedRegion(@NotNull PsiElement element) {
    if (mySubRegions == null) mySubRegions = new ArrayList<>();
    CustomRegionTreeElement currSubRegion = new CustomRegionTreeElement(element, myProvider, this);
    mySubRegions.add(currSubRegion);
    return currSubRegion;
  }

  public CustomRegionTreeElement endRegion(@NotNull PsiElement element) {
    myEndOffset = element.getTextRange().getEndOffset();
    return myParent;
  }

  @Override
  public String toString() {
    return "Region '" + myProvider.getPlaceholderText(myStartElement.getText()) + "'";
  }
}
