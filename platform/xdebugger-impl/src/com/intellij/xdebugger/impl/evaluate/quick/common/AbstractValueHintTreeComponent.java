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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.InputEvent;
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

  protected AbstractValueHintTreeComponent(@Nullable AbstractValueHint valueHint, @NotNull Tree tree, @NotNull H initialItem) {
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
      @Override
      public void actionPerformed(AnActionEvent e) {
        if (myHistory.size() > 1 && myCurrentIndex < myHistory.size() - 1){
          myCurrentIndex ++;
          updateHint();
        }
      }

      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(myHistory.size() > 1 && myCurrentIndex < myHistory.size() - 1);
      }
    };
  }

  private void updateHint() {
    if (myValueHint != null) {
      myValueHint.shiftLocation();
    }
    updateTree(myHistory.get(myCurrentIndex));
  }

  private AnAction createGoBackAction(){
    return new AnAction(CodeInsightBundle.message("quick.definition.back"), null, AllIcons.Actions.Back){
      @Override
      public void actionPerformed(AnActionEvent e) {
        if (myHistory.size() > 1 && myCurrentIndex > 0) {
          myCurrentIndex--;
          updateHint();
        }
      }


      @Override
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

  private JComponent createToolbar(JPanel parent) {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new AnAction(XDebuggerBundle.message("xdebugger.popup.value.tree.set.root.action.tooltip"),
                           XDebuggerBundle.message("xdebugger.popup.value.tree.set.root.action.tooltip"), AllIcons.Modules.UnmarkWebroot) {
      @Override
      public void update(AnActionEvent e) {
        TreePath path = myTree.getSelectionPath();
        e.getPresentation().setEnabled(path != null && path.getPathCount() > 1);
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        TreePath path = myTree.getSelectionPath();
        if (path != null) {
          setNodeAsRoot(path.getLastPathComponent());
        }
      }
    });

    AnAction back = createGoBackAction();
    back.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_MASK)), parent);
    group.add(back);

    AnAction forward = createGoForwardAction();
    forward.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.ALT_MASK)), parent);
    group.add(forward);

    return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true).getComponent();
  }

  protected abstract void setNodeAsRoot(Object node);
}
