// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.evaluate.quick.common;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.concurrency.ResultConsumer;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.WindowMoveListener;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public abstract class DebuggerTreeWithHistoryContainer<D> {
  private static final Logger LOG = Logger.getInstance(DebuggerTreeWithHistoryContainer.class);
  private static final int HISTORY_SIZE = 11;
  private final List<D> myHistory = new ArrayList<>();
  private int myCurrentIndex = -1;
  protected final DebuggerTreeCreator<D> myTreeCreator;
  protected final @NotNull Project myProject;

  protected DebuggerTreeWithHistoryContainer(@NotNull D initialItem, @NotNull DebuggerTreeCreator<D> creator, @NotNull Project project) {
    myTreeCreator = creator;
    myProject = project;
    myHistory.add(initialItem);
  }

  protected BorderLayoutPanel createMainPanel(Tree tree) {
    return fillMainPanel(JBUI.Panels.simplePanel(), tree);
  }

  protected BorderLayoutPanel fillMainPanel(BorderLayoutPanel mainPanel, Tree tree) {
    JComponent toolbar = createToolbar(mainPanel, tree);
    tree.setBackground(UIUtil.getToolTipBackground());
    toolbar.setBackground(UIUtil.getToolTipActionBackground());
    new WindowMoveListener(mainPanel).installTo(toolbar);
    return mainPanel.addToCenter(ScrollPaneFactory.createScrollPane(tree, true)).addToBottom(toolbar);
  }

  private void updateTree() {
    D item = myHistory.get(myCurrentIndex);
    updateTree(item);
  }

  protected void updateTree(@NotNull D selectedItem) {
    updateContainer(myTreeCreator.createTree(selectedItem), myTreeCreator.getTitle(selectedItem));
  }

  protected abstract void updateContainer(Tree tree, String title);

  protected void addToHistory(final D item) {
    if (myCurrentIndex < HISTORY_SIZE) {
      if (myCurrentIndex != -1) {
        myCurrentIndex += 1;
      } else {
        myCurrentIndex = 1;
      }
      myHistory.add(myCurrentIndex, item);
    }
  }

  protected JComponent createToolbar(JPanel parent, Tree tree) {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new SetAsRootAction(tree));

    AnAction back = new GoBackwardAction();
    back.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_MASK)), parent);
    group.add(back);

    AnAction forward = new GoForwardAction();
    forward.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.ALT_MASK)), parent);
    group.add(forward);

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("DebuggerTreeWithHistory", group, true);
    toolbar.setTargetComponent(tree);
    return toolbar.getComponent();
  }

  private class GoForwardAction extends AnAction {
    GoForwardAction() {
      super(CodeInsightBundle.messagePointer("quick.definition.forward"), AllIcons.Actions.Forward);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (myHistory.size() > 1 && myCurrentIndex < myHistory.size() - 1){
        myCurrentIndex ++;
        updateTree();
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(myHistory.size() > 1 && myCurrentIndex < myHistory.size() - 1);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
  }

  private class GoBackwardAction extends AnAction {
    GoBackwardAction() {
      super(CodeInsightBundle.messagePointer("quick.definition.back"), AllIcons.Actions.Back);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (myHistory.size() > 1 && myCurrentIndex > 0) {
        myCurrentIndex--;
        updateTree();
      }
    }


    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(myHistory.size() > 1 && myCurrentIndex > 0);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
  }

  private class SetAsRootAction extends AnAction {
    private final Tree myTree;

    SetAsRootAction(Tree tree) {
      super(XDebuggerBundle.message("xdebugger.popup.value.tree.set.root.action.tooltip"),
            XDebuggerBundle.message("xdebugger.popup.value.tree.set.root.action.tooltip"), AllIcons.Modules.UnmarkWebroot);
      myTree = tree;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      TreePath path = myTree.getSelectionPath();
      boolean enabled = path != null && path.getPathCount() > (myTree.isRootVisible() ? 1 : 2);
      Object component = myTree.getLastSelectedPathComponent();
      if (enabled && component instanceof XValueNodeImpl) {
        enabled = !((XValueNodeImpl)component).isLeaf();
      }
      e.getPresentation().setEnabled(enabled);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      TreePath path = myTree.getSelectionPath();
      if (path != null) {
        Object node = path.getLastPathComponent();
        myTreeCreator.createDescriptorByNode(node, new ResultConsumer<>() {
          @Override
          public void onSuccess(final D value) {
            if (value != null) {
              ApplicationManager.getApplication().invokeLater(() -> {
                addToHistory(value);
                updateTree(value);
              });
            }
          }

          @Override
          public void onFailure(@NotNull Throwable t) {
            LOG.debug(t);
          }
        });
      }
    }
  }

  protected static void registerTreeDisposable(Disposable disposable, Tree tree) {
    if (tree instanceof Disposable) {
      Disposer.register(disposable, (Disposable)tree);
    }
  }
}
