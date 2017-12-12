// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.tree;

import org.jetbrains.annotations.NotNull;

import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

class TreeModelWrapper implements TreeModel {
  private final TreeModel model;

  TreeModelWrapper(@NotNull TreeModel model) {
    this.model = model;
  }

  @Override
  public Object getRoot() {
    return model.getRoot();
  }

  @Override
  public Object getChild(Object parent, int index) {
    return model.getChild(parent, index);
  }

  @Override
  public int getChildCount(Object parent) {
    return model.getChildCount(parent);
  }

  @Override
  public int getIndexOfChild(Object parent, Object child) {
    return model.getIndexOfChild(parent, child);
  }

  @Override
  public boolean isLeaf(Object child) {
    return model.isLeaf(child);
  }

  @Override
  public void valueForPathChanged(TreePath path, Object value) {
    model.valueForPathChanged(path, value);
  }

  @Override
  public void addTreeModelListener(TreeModelListener listener) {
    model.addTreeModelListener(listener);
  }

  @Override
  public void removeTreeModelListener(TreeModelListener listener) {
    model.removeTreeModelListener(listener);
  }
}
