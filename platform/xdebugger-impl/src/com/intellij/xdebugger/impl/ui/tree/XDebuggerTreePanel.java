/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author nik
 */
public class XDebuggerTreePanel implements DnDSource {
  private final XDebuggerTree myTree;
  private final JPanel myMainPanel;

  public XDebuggerTreePanel(final @NotNull Project project, final @NotNull XDebuggerEditorsProvider editorsProvider,
                            @NotNull Disposable parentDisposable, final @Nullable XSourcePosition sourcePosition,
                            @NotNull @NonNls final String popupActionGroupId, @Nullable XValueMarkers<?, ?> markers) {
    myTree = new XDebuggerTree(project, editorsProvider, sourcePosition, popupActionGroupId, markers);
    myMainPanel = new JPanel(new BorderLayout());
    myMainPanel.add(ScrollPaneFactory.createScrollPane(myTree), BorderLayout.CENTER);
    Disposer.register(parentDisposable, myTree);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        myMainPanel.removeAll();
      }
    });
  }

  public XDebuggerTree getTree() {
    return myTree;
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }

  @Override
  public boolean canStartDragging(final DnDAction action, final Point dragOrigin) {
    return getNodesToDrag().length > 0;
  }

  private XValueNodeImpl[] getNodesToDrag() {
    return myTree.getSelectedNodes(XValueNodeImpl.class, node -> DebuggerUIUtil.hasEvaluationExpression(node.getValueContainer()));
  }

  @Override
  public DnDDragStartBean startDragging(final DnDAction action, final Point dragOrigin) {
    return new DnDDragStartBean(getNodesToDrag());
  }

  @Override
  public Pair<Image, Point> createDraggedImage(final DnDAction action, final Point dragOrigin) {
    XValueNodeImpl[] nodes = getNodesToDrag();
    if (nodes.length == 1) {
      return DnDAwareTree.getDragImage(myTree, nodes[0].getPath(), dragOrigin);
    }
    return DnDAwareTree.getDragImage(myTree, XDebuggerBundle.message("xdebugger.drag.text.0.elements", nodes.length), dragOrigin);
  }

  @Override
  public void dragDropEnd() {
  }

  @Override
  public void dropActionChanged(final int gestureModifiers) {
  }
}
