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
package org.jetbrains.plugins.gradle.execution;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.externalSystem.model.task.event.OperationDescriptor;
import com.intellij.openapi.externalSystem.model.task.event.TestOperationDescriptor;
import com.intellij.pom.Navigatable;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Vladislav.Soroka
 * @since 12/17/2015
 */
public class TaskExecutionTreeTable extends TreeTable {
  private static final ExecutionNode NULL_NODE = new ExecutionNode(null, "");

  public TaskExecutionTreeTable(ListTreeTableModelOnColumns model) {
    super(model);
    addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER && hasSingleSelection()) {
          handleDoubleClickOrEnter(getTree().getSelectionPath(), e);
        }
        if (e.getKeyCode() == KeyEvent.VK_F2 && e.getModifiers() == 0) {
          e.consume(); // ignore start editing by F2
        }
      }
    });
    addMouseListener(new MouseAdapter() {
      public void mousePressed(MouseEvent e) {
        if (!e.isPopupTrigger() && SimpleTree.isDoubleClick(e)) {
          handleDoubleClickOrEnter(getTree().getClosestPathForLocation(e.getX(), e.getY()), e);
        }
      }
    });

    final ActionManager actionManager = ActionManager.getInstance();
    addMouseListener(new PopupHandler() {
      public void invokePopup(final Component comp, final int x, final int y) {
        final String id = getMenuId(getSelectedNodes());
        if (id != null) {
          final ActionGroup actionGroup = (ActionGroup)actionManager.getAction(id);
          if (actionGroup != null) {
            actionManager.createActionPopupMenu("", actionGroup).getComponent().show(comp, x, y);
          }
        }
      }

      @Nullable
      private String getMenuId(Collection<? extends ExecutionNode> nodes) {
        String id = null;
        for (ExecutionNode node : nodes) {
          String menuId = node.getMenuId();
          if (menuId == null) {
            return null;
          }
          if (id == null) {
            id = menuId;
          }
          else if (!id.equals(menuId)) {
            return null;
          }
        }
        return id;
      }
    });
  }

  private Collection<? extends ExecutionNode> getSelectedNodes() {
    final TreePath[] selectionPaths = getTree().getSelectionPaths();
    if (selectionPaths != null) {
      return ContainerUtil.map(selectionPaths, new Function<TreePath, ExecutionNode>() {
        @Override
        public ExecutionNode fun(TreePath path) {
          return getNodeFor(path);
        }
      });
    }
    return Collections.emptyList();
  }

  public boolean isSelectionEmpty() {
    final TreePath selection = getTree().getSelectionPath();
    return selection == null || getNodeFor(selection) == NULL_NODE;
  }

  public ExecutionNode getNodeFor(TreePath aPath) {
    if (aPath == null) {
      return NULL_NODE;
    }
    DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)aPath.getLastPathComponent();
    if (treeNode == null) {
      return NULL_NODE;
    }
    final Object userObject = treeNode.getUserObject();
    return userObject instanceof ExecutionNode ? (ExecutionNode)userObject : NULL_NODE;
  }


  @Override
  public TableCellRenderer getCellRenderer(int row, int column) {
    if (column == 1) {
      return new DefaultTableCellRenderer() {
        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {
          super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
          setHorizontalAlignment(SwingConstants.RIGHT);
          final Color fg = isSelected ? UIUtil.getTreeSelectionForeground() : SimpleTextAttributes.GRAY_ATTRIBUTES.getFgColor();
          setForeground(fg);
          return this;
        }
      };
    }
    return super.getCellRenderer(row, column);
  }

  private void handleDoubleClickOrEnter(final TreePath treePath, final InputEvent e) {
    Runnable runnable = new Runnable() {
      public void run() {
        final ExecutionNode executionNode = getNodeFor(treePath);
        if (executionNode != NULL_NODE) {
          handleDoubleClickOrEnter(executionNode, e);
        }
      }
    };
    ApplicationManager.getApplication().invokeLater(runnable, ModalityState.stateForComponent(this));
  }

  private static void handleDoubleClickOrEnter(ExecutionNode executionNode, InputEvent e) {
    final OperationDescriptor descriptor = executionNode.getInfo().getDescriptor();
    if (descriptor instanceof TestOperationDescriptor && executionNode.getProject() != null) {
      final Object openFileDescriptor =
        GradleRunnerUtil.getData(executionNode.getProject(), CommonDataKeys.NAVIGATABLE.getName(), executionNode.getInfo());
      if (openFileDescriptor instanceof Navigatable) {
        boolean isKeyEnterEvent = e instanceof KeyEvent && ((KeyEvent)e).getKeyCode() == KeyEvent.VK_ENTER;
        if (isKeyEnterEvent || executionNode.getChildren().length == 0) {
          ((Navigatable)openFileDescriptor).navigate(!isKeyEnterEvent);
        }
      }
    }
  }

  private boolean hasSingleSelection() {
    if (!isSelectionEmpty()) {
      final TreePath[] selectionPaths = getTree().getSelectionPaths();
      return selectionPaths != null && selectionPaths.length == 1;
    }
    else {
      return false;
    }
  }
}
