// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.structureView.impl;

import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@ApiStatus.Internal
public class StructureViewComposite implements StructureView {
  
  private final StructureViewDescriptor[] myStructureViews;

  public static class StructureViewDescriptor {
    public final String title;
    public final StructureViewModel structureModel;
    public final StructureView structureView;
    public final Icon icon;

    public StructureViewDescriptor(String title, @NotNull StructureView structureView, Icon icon) {
      this.title = title;
      this.structureModel = structureView.getTreeModel();
      this.structureView = structureView;
      this.icon = icon;
    }

    public StructureViewDescriptor(String title, @NotNull StructureViewModel structureModel, Icon icon) {
      this.title = title;
      this.structureModel = structureModel;
      this.structureView = null;
      this.icon = icon;
    }
  }

  public StructureViewComposite(StructureViewDescriptor @NotNull ... views) {
    myStructureViews = views;
    for (StructureViewDescriptor descriptor : views) {
      Disposer.register(this, descriptor.structureView);
    }
  }

  @RequiresBackgroundThread
  public boolean isOutdated() {
    return false;
  }

  public @Nullable StructureView getSelectedStructureView() {
    StructureViewDescriptor descriptor = ArrayUtil.getFirstElement(myStructureViews);
    return descriptor == null ? null : descriptor.structureView;
  }

  @Override
  public boolean navigateToSelectedElement(final boolean requestFocus) {
    StructureView view = getSelectedStructureView();
    return view != null && view.navigateToSelectedElement(requestFocus);
  }

  @Override
  public JComponent getComponent() {
    StructureView view = getSelectedStructureView();
    return view == null ? null : view.getComponent();
  }

  @Override
  public void dispose() {
  }

  @Override
  public void centerSelectedRow() {
    StructureView view = getSelectedStructureView();
    if (view != null) view.centerSelectedRow();
  }

  @Override
  public void restoreState() {
    for (StructureViewDescriptor descriptor : myStructureViews) {
      descriptor.structureView.restoreState();
    }
  }

  @Override
  public void storeState() {
    for (StructureViewDescriptor descriptor : myStructureViews) {
      descriptor.structureView.storeState();
    }
  }

  @Override
  public void disableStoreState() {
    for (StructureViewDescriptor descriptor : myStructureViews) {
      descriptor.structureView.disableStoreState();
    }
  }

  public StructureViewDescriptor @NotNull [] getStructureViews() {
    return myStructureViews;
  }

  @Override
  public @NotNull StructureViewModel getTreeModel() {
    StructureView view = getSelectedStructureView();
    if (view != null) return view.getTreeModel();
    class M extends TextEditorBasedStructureViewModel implements StructureViewTreeElement, ItemPresentation {
      M() { super(null, null);}

      @Override
      public @NotNull StructureViewTreeElement getRoot() { return this;}
      @Override public Object getValue() { return null;} 
      @Override
      public @NotNull ItemPresentation getPresentation() { return this;}
      @Override public TreeElement @NotNull [] getChildren() { return EMPTY_ARRAY;} 
      @Override
      public @Nullable String getPresentableText() { return null;}
      @Override
      public @Nullable Icon getIcon(boolean unused) { return null;}
    }
    return new M();
  }
}
