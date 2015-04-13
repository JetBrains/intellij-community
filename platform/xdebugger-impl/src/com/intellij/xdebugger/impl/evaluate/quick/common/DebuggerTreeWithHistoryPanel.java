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
package com.intellij.xdebugger.impl.evaluate.quick.common;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author nik
 */
public class DebuggerTreeWithHistoryPanel<D> extends DebuggerTreeWithHistoryContainer<D> {
  private final JPanel myMainPanel;
  private final Disposable myDisposable;
  private XDebuggerTree myTree;

  public DebuggerTreeWithHistoryPanel(@NotNull D initialItem, @NotNull DebuggerTreeCreator<D> creator, @NotNull Project project, Disposable disposable) {
    super(initialItem, creator, project);
    myDisposable = disposable;
    myTree = (XDebuggerTree)myTreeCreator.createTree(initialItem);
    registerTreeDisposable(myDisposable, myTree);
    myMainPanel = createMainPanel(myTree);
  }

  @Override
  protected void updateContainer(Tree tree, String title) {
    myTree = (XDebuggerTree)tree;
    registerTreeDisposable(myDisposable, tree);
    Component component = ((BorderLayout)myMainPanel.getLayout()).getLayoutComponent(BorderLayout.CENTER);
    myMainPanel.remove(component);
    myMainPanel.add(BorderLayout.CENTER, ScrollPaneFactory.createScrollPane(tree));
    myMainPanel.revalidate();
    myMainPanel.repaint();
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }

  public void rebuild() {
    myTree.getLaterInvocator().offer(new Runnable() {
      @Override
      public void run() {
        myTree.rebuildAndRestore(XDebuggerTreeState.saveState(myTree));
      }
    });
  }

  public XDebuggerTree getTree() {
    return myTree;
  }
}
