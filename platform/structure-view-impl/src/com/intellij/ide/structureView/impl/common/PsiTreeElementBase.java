// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.structureView.impl.common;

import com.intellij.ide.structureView.StructureViewExtension;
import com.intellij.ide.structureView.StructureViewFactoryEx;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.customRegions.CustomRegionStructureUtil;
import com.intellij.ide.util.treeView.AbstractTreeUi;
import com.intellij.ide.util.treeView.NodeDescriptorProvidingKey;
import com.intellij.ide.util.treeView.TreeAnchorizer;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.Iconable;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public abstract class PsiTreeElementBase <T extends PsiElement> implements StructureViewTreeElement, ItemPresentation, NodeDescriptorProvidingKey {
  private final Object myValue;

  protected PsiTreeElementBase(T psiElement) {
    myValue = psiElement == null ? null : TreeAnchorizer.getService().createAnchor(psiElement);
  }

  @Override
  public @NotNull ItemPresentation getPresentation() {
    return this;
  }

  @Override
  public @NotNull Object getKey() {
    return String.valueOf(getElement());
  }

  public final @Nullable T getElement() {
    //noinspection unchecked
    return myValue == null ? null : (T)TreeAnchorizer.getService().retrieveElement(myValue);
  }

  @Override
  public Icon getIcon(boolean open) {
    final PsiElement element = getElement();
    if (element != null) {
      int flags = Iconable.ICON_FLAG_READ_STATUS;
      if (!(element instanceof PsiFile) || !element.isWritable()) flags |= Iconable.ICON_FLAG_VISIBILITY;
      return element.getIcon(flags);
    }
    else {
      return null;
    }
  }

  @Override
  public T getValue() {
    return getElement();
  }

  public boolean isSearchInLocationString() {
    return false;
  }

  public String toString() {
    final T element = getElement();
    return element != null ? element.toString() : "";
  }

  @Override
  public final StructureViewTreeElement @NotNull [] getChildren() {
    List<StructureViewTreeElement> list = AbstractTreeUi.calculateYieldingToWriteAction(() -> doGetChildren(true));
    return list.isEmpty() ? EMPTY_ARRAY : list.toArray(EMPTY_ARRAY);
  }

  public final @NotNull List<StructureViewTreeElement> getChildrenWithoutCustomRegions() {
    return AbstractTreeUi.calculateYieldingToWriteAction(() -> doGetChildren(false));
  }

  private @NotNull List<StructureViewTreeElement> doGetChildren(boolean withCustomRegions) {
    T element = getElement();
    return element == null ? Collections.emptyList() : mergeWithExtensions(element, getChildrenBase(), withCustomRegions);
  }

  @Override
  public void navigate(boolean requestFocus) {
    T element = getElement();
    if (element != null) {
      ((Navigatable)element).navigate(requestFocus);
    }
  }

  @Override
  public boolean canNavigate() {
    final T element = getElement();
    return element instanceof Navigatable && ((Navigatable)element).canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    return canNavigate();
  }

  public abstract @NotNull Collection<StructureViewTreeElement> getChildrenBase();

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final PsiTreeElementBase that = (PsiTreeElementBase)o;

    T value = getValue();
    return value == null ? that.getValue() == null : value.equals(that.getValue());
  }

  public int hashCode() {
    T value = getValue();
    return value == null ? 0 : value.hashCode();
  }

  public boolean isValid() {
    return getElement() != null;
  }

  /** @return element base children merged with children provided by extensions */
  public static @NotNull List<StructureViewTreeElement> mergeWithExtensions(@NotNull PsiElement element,
                                                                            @NotNull Collection<StructureViewTreeElement> baseChildren,
                                                                            boolean withCustomRegions) {
    List<StructureViewTreeElement> result = new ArrayList<>(withCustomRegions ? CustomRegionStructureUtil.groupByCustomRegions(element, baseChildren) : baseChildren);
    StructureViewFactoryEx structureViewFactory = StructureViewFactoryEx.getInstanceEx(element.getProject());
    Class<? extends PsiElement> aClass = element.getClass();
    for (StructureViewExtension extension : structureViewFactory.getAllExtensions(aClass)) {
      StructureViewTreeElement[] children = extension.getChildren(element);
      if (children != null) {
        ContainerUtil.addAll(result, children);
      }
      extension.filterChildren(result, children == null || children.length == 0 ? Collections.emptyList() : Arrays.asList(children));
    }
    return result;
  }
}
