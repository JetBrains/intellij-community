/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ui.stripe;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;

import java.beans.EventHandler;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

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
    new DumbAwareAction() {
      @Override
      public void actionPerformed(AnActionEvent event) {
        selectNext(myTree.getMaxSelectionRow());
      }
    }.registerCustomShortcutSet(getNextErrorShortcut(), myTree, this);
    new DumbAwareAction() {
      @Override
      public void actionPerformed(AnActionEvent event) {
        selectPrevious(myTree.getMinSelectionRow());
      }
    }.registerCustomShortcutSet(getPreviousErrorShortcut(), myTree, this);
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
