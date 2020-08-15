// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.inline;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.util.ui.tree.TreeModelAdapter;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.evaluate.quick.common.DebuggerTreeCreator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreePath;
import java.awt.*;

public class XDebuggerTreeInlayPopup<D> {
  private static final Logger LOG = Logger.getInstance(XDebuggerTreeInlayPopup.class);
  protected final DebuggerTreeCreator<D> myTreeCreator;
  @NotNull protected final Project myProject;
  @NonNls private final static String DIMENSION_SERVICE_KEY = "DebuggerActiveHint";
  private JBPopup myPopup;
  private final Editor myEditor;
  private final Point myPoint;
  @Nullable private final Runnable myHideRunnable;
  private Inlay myInlay;

  private XDebuggerTreeInlayPopup(@NotNull DebuggerTreeCreator<D> creator,
                                  @NotNull Editor editor,
                                  @NotNull Point point,
                                  @NotNull Project project,
                                  @Nullable Runnable hideRunnable,
                                  @NotNull Inlay inlay) {
    myTreeCreator = creator;
    myProject = project;
    myEditor = editor;
    myPoint = point;
    myHideRunnable = hideRunnable;
    myInlay = inlay;
  }

  protected BorderLayoutPanel createMainPanel(Tree tree) {
    return fillMainPanel(JBUI.Panels.simplePanel(), tree);
  }

  protected BorderLayoutPanel fillMainPanel(BorderLayoutPanel mainPanel, Tree tree) {
    return mainPanel.addToCenter(ScrollPaneFactory.createScrollPane(tree)).addToBottom(createToolbar(mainPanel, tree));
  }

  protected void updateTree(@NotNull D selectedItem) {
    updateContainer(myTreeCreator.createTree(selectedItem));
  }

  private JComponent createToolbar(JPanel parent, Tree tree) {
    //ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction("Debugger.Representation");
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new ConfigureRenderer(tree));

    return ActionManager.getInstance().createActionToolbar("XDebuggerTreeInlayPopup", group, true).getComponent();
  }

  private class ConfigureRenderer extends AnAction {
    private final Tree myTree;

    ConfigureRenderer(Tree tree) {
      super(XDebuggerBundle.message("xdebugger.popup.inlay.configure.renderer.action.tooltip"),
            XDebuggerBundle.message("xdebugger.popup.inlay.configure.renderer.action.tooltip"), null);
      myTree = tree;
    }

    @Override
    public boolean displayTextInToolbar() {
      return true;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      AnAction action = ActionManager.getInstance().getAction("Debugger.CreateRenderer");

      e.getPresentation().setEnabledAndVisible(action != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      AnAction action = ActionManager.getInstance().getAction("Debugger.CreateRenderer");
      action.actionPerformed(e);
      myInlay.update();
    }
  }

  protected static void registerTreeDisposable(Disposable disposable, Tree tree) {
    if (tree instanceof Disposable) {
      Disposer.register(disposable, (Disposable)tree);
    }
  }

  public static <D> void showTreePopup(@NotNull DebuggerTreeCreator<D> creator, @NotNull D initialItem, @NotNull Inlay inlay, @NotNull Editor editor,
                                       @NotNull Point point, @NotNull Project project, Runnable hideRunnable) {
    new XDebuggerTreeInlayPopup<>(creator, editor, point, project, hideRunnable, inlay).updateTree(initialItem);
  }

  private TreeModelListener createTreeListener(final Tree tree) {
    return new TreeModelAdapter() {
      @Override
      public void treeStructureChanged(TreeModelEvent e) {
        resize(e.getTreePath(), tree);
      }
    };
  }

  protected void updateContainer(final Tree tree) {
    if (myPopup != null) {
      myPopup.cancel();
    }
    tree.getModel().addTreeModelListener(createTreeListener(tree));
    myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(createMainPanel(tree), tree)
      .setRequestFocus(true)
      .setResizable(true)
      .setMovable(true)
      .setDimensionServiceKey(myProject, DIMENSION_SERVICE_KEY, false)
      .setMayBeParent(true)
      .setCancelOnOtherWindowOpen(true)
      .setKeyEventHandler(event -> {
        if (AbstractPopup.isCloseRequest(event)) {
          // Do not process a close request if the tree shows a speed search popup
          SpeedSearchSupply supply = SpeedSearchSupply.getSupply(tree);
          return supply != null && StringUtil.isEmpty(supply.getEnteredPrefix());
        }
        return false;
      })
      .addListener(new JBPopupListener() {
        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          if (myHideRunnable != null) {
            myHideRunnable.run();
          }
        }
      })
      .setCancelCallback(() -> {
        Window parent = SwingUtilities.getWindowAncestor(tree);
        if (parent != null) {
          for (Window child : parent.getOwnedWindows()) {
            if (child.isShowing()) {
              return false;
            }
          }
        }
        return true;
      })
      .createPopup();

    registerTreeDisposable(myPopup, tree);

    //Editor may be disposed before later invokator process this action
    if (myEditor.getComponent().getRootPane() == null) {
      myPopup.cancel();
      return;
    }
    myPopup.show(new RelativePoint(myEditor.getContentComponent(), myPoint));

    updateInitialBounds(tree);
  }

  private void resize(final TreePath path, JTree tree) {
    if (myPopup == null || !myPopup.isVisible() || myPopup.isDisposed()) return;
    final Window popupWindow = SwingUtilities.windowForComponent(myPopup.getContent());
    if (popupWindow == null) return;
    final Dimension size = tree.getPreferredSize();
    final Point location = popupWindow.getLocation();
    final Rectangle windowBounds = popupWindow.getBounds();
    final Rectangle bounds = tree.getPathBounds(path);
    if (bounds == null) return;

    final Rectangle targetBounds = new Rectangle(location.x,
                                                 location.y,
                                                 Math.max(Math.max(size.width, bounds.width) + 20, windowBounds.width),
                                                 Math.max(tree.getRowCount() * bounds.height + 55, windowBounds.height));
    ScreenUtil.cropRectangleToFitTheScreen(targetBounds);
    popupWindow.setBounds(targetBounds);
    popupWindow.validate();
    popupWindow.repaint();
  }

  private void updateInitialBounds(final Tree tree) {
    final Window popupWindow = SwingUtilities.windowForComponent(myPopup.getContent());
    final Dimension size = tree.getPreferredSize();
    final Point location = popupWindow.getLocation();
    final Rectangle windowBounds = popupWindow.getBounds();
    final Rectangle targetBounds = new Rectangle(location.x,
                                                 location.y,
                                                 Math.max(size.width + 250, windowBounds.width),
                                                 Math.max(size.height, windowBounds.height));
    ScreenUtil.cropRectangleToFitTheScreen(targetBounds);
    popupWindow.setBounds(targetBounds);
    popupWindow.validate();
    popupWindow.repaint();
  }
}
