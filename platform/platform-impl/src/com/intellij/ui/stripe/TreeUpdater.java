// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.stripe;

import com.intellij.openapi.project.DumbAwareAction;

import javax.swing.*;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.beans.EventHandler;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * @author Sergey.Malenkov
 */
public class TreeUpdater<Painter extends ErrorStripePainter> extends Updater<Painter> {
  private final JTree myTree;

  private final TreeModelListener myTreeModelListener = EventHandler.create(TreeModelListener.class, this, "update");
  private final PropertyChangeListener myPropertyChangeListener = new PropertyChangeListener() {
    @Override
    public void propertyChange(PropertyChangeEvent event) {
      Object oldValue = event.getOldValue();
      if (oldValue instanceof TreeModel) ((TreeModel)oldValue).removeTreeModelListener(myTreeModelListener);
      Object newValue = event.getNewValue();
      if (newValue instanceof TreeModel) ((TreeModel)newValue).addTreeModelListener(myTreeModelListener);
    }
  };

  public TreeUpdater(Painter painter, JScrollPane pane, JTree tree) {
    super(painter, pane);
    myTree = tree;
    myTree.addPropertyChangeListener(JTree.TREE_MODEL_PROPERTY, myPropertyChangeListener);
    TreeModel model = myTree.getModel();
    if (model != null) model.addTreeModelListener(myTreeModelListener);
    DumbAwareAction.create(e -> selectNext(myTree.getMaxSelectionRow()))
      .registerCustomShortcutSet(getNextErrorShortcut(), myTree, this);
    DumbAwareAction.create(e -> selectPrevious(myTree.getMinSelectionRow()))
      .registerCustomShortcutSet(getPreviousErrorShortcut(), myTree, this);
  }

  @Override
  public void dispose() {
    myTree.removePropertyChangeListener(JTree.TREE_MODEL_PROPERTY, myPropertyChangeListener);
    TreeModel model = myTree.getModel();
    if (model != null) model.removeTreeModelListener(myTreeModelListener);
    super.dispose();
  }

  @Override
  protected void onSelect(Painter painter, int index) {
    if (0 <= index) {
      myTree.setSelectionRow(index);
      myTree.scrollRowToVisible(index);
    }
  }

  @Override
  protected void update(Painter painter) {
    update(painter, myTree);
  }

  protected void update(Painter painter, JTree tree) {
    int count = tree.getRowCount();
    painter.setErrorStripeCount(count);
    for (int index = 0; index < count; index++) {
      TreePath path = tree.getPathForRow(index);
      update(painter, index, path == null ? null : path.getLastPathComponent());
    }
  }
}
