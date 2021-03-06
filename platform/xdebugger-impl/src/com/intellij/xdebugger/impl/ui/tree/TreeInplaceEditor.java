// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.xdebugger.impl.ui.InplaceEditor;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;

public abstract class TreeInplaceEditor extends InplaceEditor {

  protected abstract TreePath getNodePath();

  protected abstract JTree getTree();

  @Override
  protected JComponent getHostComponent() {
    return getTree();
  }

  @Override
  protected void beforeShow() {
    getTree().scrollPathToVisible(getNodePath());
  }

  @Override
  @Nullable
  protected Rectangle getEditorBounds() {
    final JTree tree = getTree();
    Rectangle bounds = tree.getVisibleRect();
    Rectangle nodeBounds = tree.getPathBounds(getNodePath());
    if (bounds == null || nodeBounds == null) {
      return null;
    }
    if (bounds.y > nodeBounds.y || bounds.y + bounds.height < nodeBounds.y + nodeBounds.height) {
      return null;
    }
    bounds.y = nodeBounds.y;
    bounds.height = nodeBounds.height;

    if(nodeBounds.x > bounds.x) {
      bounds.width = bounds.width - nodeBounds.x + bounds.x;
      bounds.x = nodeBounds.x;
    }
    return bounds;
  }
}
