/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.ide.dnd.DnDAction;
import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.ide.dnd.DnDSource;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * @author nik
 */
public class XDebuggerTreePanel implements DnDSource {
  private final XDebuggerTree myTree;
  private final JPanel myMainPanel;
  private final PopupHandler myPopupHandler;

  public XDebuggerTreePanel(final @NotNull XDebugSession session, final @NotNull XDebuggerEditorsProvider editorsProvider, final @Nullable XSourcePosition sourcePosition,
                            @NotNull @NonNls final String popupActionGroupId) {
    myTree = new XDebuggerTree(session, editorsProvider, sourcePosition);
    myMainPanel = new JPanel(new BorderLayout());
    myMainPanel.add(ScrollPaneFactory.createScrollPane(myTree), BorderLayout.CENTER);
    final ActionManager actionManager = ActionManager.getInstance();
    myPopupHandler = new PopupHandler() {
      public void invokePopup(final Component comp, final int x, final int y) {
        final ActionGroup group = (ActionGroup)actionManager.getAction(popupActionGroupId);
        ActionPopupMenu popupMenu = actionManager.createActionPopupMenu(ActionPlaces.UNKNOWN, group);
        popupMenu.getComponent().show(comp, x, y);
      }
    };
    actionManager.getAction(XDebuggerActions.SET_VALUE).registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0)), myTree);
    actionManager.getAction(XDebuggerActions.COPY_VALUE).registerCustomShortcutSet(CommonShortcuts.getCopy(), myTree);
    actionManager.getAction(XDebuggerActions.JUMP_TO_SOURCE).registerCustomShortcutSet(CommonShortcuts.getEditSource(), myTree);

    myTree.addMouseListener(myPopupHandler);
  }

  public void dispose() {
    ActionManager actionManager = ActionManager.getInstance();
    actionManager.getAction(XDebuggerActions.SET_VALUE).unregisterCustomShortcutSet(myTree);
    actionManager.getAction(XDebuggerActions.JUMP_TO_SOURCE).unregisterCustomShortcutSet(myTree);
    myTree.removeMouseListener(myPopupHandler);
  }

  public XDebuggerTree getTree() {
    return myTree;
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }

  public boolean canStartDragging(final DnDAction action, final Point dragOrigin) {
    return getNodesToDrag().length > 0;
  }

  private XValueNodeImpl[] getNodesToDrag() {
    return myTree.getSelectedNodes(XValueNodeImpl.class, new Tree.NodeFilter<XValueNodeImpl>() {
      public boolean accept(final XValueNodeImpl node) {
        return node.getValueContainer().getEvaluationExpression() != null;
      }
    });
  }

  public DnDDragStartBean startDragging(final DnDAction action, final Point dragOrigin) {
    return new DnDDragStartBean(getNodesToDrag());
  }

  public Pair<Image, Point> createDraggedImage(final DnDAction action, final Point dragOrigin) {
    XValueNodeImpl[] nodes = getNodesToDrag();
    if (nodes.length == 1) {
      return DnDAwareTree.getDragImage(myTree, nodes[0].getPath(), dragOrigin);
    }
    return DnDAwareTree.getDragImage(myTree, XDebuggerBundle.message("xdebugger.drag.text.0.elements", nodes.length), dragOrigin);
  }

  public void dragDropEnd() {
  }

  public void dropActionChanged(final int gestureModifiers) {
  }
}
