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
package com.intellij.xdebugger.impl.evaluate.quick.common;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.xdebugger.XDebuggerBundle;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;

/**
 * @author nik
 */
public abstract class AbstractValueHintTreeComponent<H> {
  private static final int HISTORY_SIZE = 11;
  private final ArrayList<H> myHistory = new ArrayList<H>();
  private int myCurrentIndex = -1;
  private final AbstractValueHint myValueHint;
  private final Tree myTree;
  private JPanel myMainPanel;

  protected AbstractValueHintTreeComponent(final AbstractValueHint valueHint, final Tree tree, final H initialItem) {
    myValueHint = valueHint;
    myTree = tree;
    myHistory.add(initialItem);
  }

  public JPanel getMainPanel() {
    if (myMainPanel == null) {
      myMainPanel = new JPanel(new BorderLayout());
      myMainPanel.add(ScrollPaneFactory.createScrollPane(myTree), BorderLayout.CENTER);
      myMainPanel.add(createToolbar(myMainPanel), BorderLayout.NORTH);
    }
    return myMainPanel;
  }

  private AnAction createGoForwardAction(){
    return new AnAction(CodeInsightBundle.message("quick.definition.forward"), null, AllIcons.Actions.Forward){
      public void actionPerformed(AnActionEvent e) {
        if (myHistory.size() > 1 && myCurrentIndex < myHistory.size() - 1){
          myCurrentIndex ++;
          updateHint();
        }
      }


      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(myHistory.size() > 1 && myCurrentIndex < myHistory.size() - 1);
      }
    };
  }

  private void updateHint() {
    myValueHint.shiftLocation();
    updateTree(myHistory.get(myCurrentIndex));
  }

  private AnAction createGoBackAction(){
    return new AnAction(CodeInsightBundle.message("quick.definition.back"), null, AllIcons.Actions.Back){
      public void actionPerformed(AnActionEvent e) {
        if (myHistory.size() > 1 && myCurrentIndex > 0) {
          myCurrentIndex--;
          updateHint();
        }
      }


      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(myHistory.size() > 1 && myCurrentIndex > 0);
      }
    };
  }

  protected abstract void updateTree(H selectedItem);

  protected void addToHistory(final H item) {
    if (myCurrentIndex < HISTORY_SIZE) {
      if (myCurrentIndex != -1) {
        myCurrentIndex += 1;
      } else {
        myCurrentIndex = 1;
      }
      myHistory.add(myCurrentIndex, item);
    }
  }

  private JComponent createToolbar(final JPanel parent) {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(createSetRoot());

    AnAction back = createGoBackAction();
    back.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, KeyEvent.ALT_MASK)), parent);
    group.add(back);

    AnAction forward = createGoForwardAction();
    forward.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, KeyEvent.ALT_MASK)), parent);
    group.add(forward);

    return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true).getComponent();
  }

  private AnAction createSetRoot() {
    final String title = XDebuggerBundle.message("xdebugger.popup.value.tree.set.root.action.tooltip");
    return new AnAction(title, title, AllIcons.Modules.UnmarkWebroot) {
      public void actionPerformed(AnActionEvent e) {
        final TreePath path = myTree.getSelectionPath();
        if (path == null) return;
        final Object node = path.getLastPathComponent();
        setNodeAsRoot(node);
      }
    };
  }

  protected abstract void setNodeAsRoot(Object node);
}
