// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.ide.dnd.DnDAction;
import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.ide.dnd.DnDSource;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.ide.ui.AntiFlickeringPanel;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public final class XDebuggerTreePanel implements DnDSource {
  private final XDebuggerTree myTree;
  private final JPanel myMainPanel;

  public XDebuggerTreePanel(final @NotNull Project project, final @NotNull XDebuggerEditorsProvider editorsProvider,
                            @NotNull Disposable parentDisposable, final @Nullable XSourcePosition sourcePosition,
                            @NotNull @NonNls final String popupActionGroupId, @Nullable XValueMarkers<?, ?> markers) {
    myTree = new XDebuggerTree(project, editorsProvider, sourcePosition, popupActionGroupId, markers);
    myMainPanel = new JPanel(new BorderLayout());
    Component content;
    if (!ApplicationManager.getApplication().isUnitTestMode() && Registry.intValue("debugger.anti.flickering.delay", 0) > 0) {
      AntiFlickeringPanel antiFlickeringPanel = new AntiFlickeringPanel(new BorderLayout());
      antiFlickeringPanel.add(myTree);
      content = antiFlickeringPanel;
    }
    else {
      content = myTree;
    }
    myMainPanel.add(ScrollPaneFactory.createScrollPane(content), BorderLayout.CENTER);
    Disposer.register(parentDisposable, myTree);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        myMainPanel.removeAll();
      }
    });
  }

  @NotNull
  public XDebuggerTree getTree() {
    return myTree;
  }

  @NotNull
  public JPanel getMainPanel() {
    return myMainPanel;
  }

  @Override
  public boolean canStartDragging(final DnDAction action, final @NotNull Point dragOrigin) {
    return getNodesToDrag().length > 0;
  }

  private XValueNodeImpl[] getNodesToDrag() {
    return myTree.getSelectedNodes(XValueNodeImpl.class, null);
  }

  @Override
  public DnDDragStartBean startDragging(final DnDAction action, final @NotNull Point dragOrigin) {
    return new DnDDragStartBean(getNodesToDrag());
  }

  @Override
  public Pair<Image, Point> createDraggedImage(DnDAction action, Point dragOrigin, @NotNull DnDDragStartBean bean) {
    XValueNodeImpl[] nodes = getNodesToDrag();
    if (nodes.length == 1) {
      return DnDAwareTree.getDragImage(myTree, nodes[0].getPath(), dragOrigin);
    }
    return DnDAwareTree.getDragImage(myTree, XDebuggerBundle.message("xdebugger.drag.text.0.elements", nodes.length), dragOrigin);
  }
}
