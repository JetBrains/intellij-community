// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.structureView.impl;

import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

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

  public boolean isOutdated() {
    return false;
  }

  @Nullable
  public StructureView getSelectedStructureView() {
    StructureViewDescriptor descriptor = ArrayUtil.getFirstElement(myStructureViews);
    return descriptor == null ? null : descriptor.structureView;
  }

  @Override
  public FileEditor getFileEditor() {
    StructureView view = getSelectedStructureView();
    return view == null ? null : view.getFileEditor();
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
  @NotNull
  public StructureViewModel getTreeModel() {
    StructureView view = getSelectedStructureView();
    if (view != null) return view.getTreeModel();
    class M extends TextEditorBasedStructureViewModel implements StructureViewTreeElement, ItemPresentation {
      M() { super(null, null);}

      @NotNull @Override public StructureViewTreeElement getRoot() { return this;} 
      @Override public Object getValue() { return null;} 
      @NotNull @Override public ItemPresentation getPresentation() { return this;} 
      @Override public TreeElement @NotNull [] getChildren() { return EMPTY_ARRAY;} 
      @Nullable @Override public String getPresentableText() { return null;} 
      @Nullable @Override public String getLocationString() { return null;} 
      @Nullable @Override public Icon getIcon(boolean unused) { return null;} 
      @Override public void navigate(boolean requestFocus) {} 
      @Override public boolean canNavigate() { return false;} 
      @Override public boolean canNavigateToSource() { return false;}
    }
    return new M();
  }
}
