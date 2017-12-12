// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.tree;

import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

class TreeModelWithDelay extends TreeModelWrapper {
  private final long delay;

  TreeModelWithDelay(@NotNull TreeModel model, long delay) {
    super(model);
    this.delay = delay;
  }

  void pause() {
    if (delay > 0) {
      try {
        Thread.sleep(delay);
      }
      catch (InterruptedException ignored) {
      }
    }
  }

  @Override
  public Object getRoot() {
    pause();
    return super.getRoot();
  }

  @Override
  public Object getChild(Object parent, int index) {
    pause();
    return super.getChild(parent, index);
  }

  @Override
  public int getChildCount(Object parent) {
    pause();
    return super.getChildCount(parent);
  }

  @Override
  public int getIndexOfChild(Object parent, Object child) {
    pause();
    return super.getIndexOfChild(parent, child);
  }

  @Override
  public boolean isLeaf(Object child) {
    pause();
    return super.isLeaf(child);
  }

  @Override
  public void valueForPathChanged(TreePath path, Object value) {
    pause();
    super.valueForPathChanged(path, value);
  }
}
