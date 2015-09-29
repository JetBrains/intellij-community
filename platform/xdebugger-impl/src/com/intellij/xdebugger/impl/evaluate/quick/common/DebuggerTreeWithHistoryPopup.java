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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.BooleanFunction;
import com.intellij.util.ui.tree.TreeModelAdapter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * @author nik
 */
class DebuggerTreeWithHistoryPopup<D> extends DebuggerTreeWithHistoryContainer<D> {
  @NonNls private final static String DIMENSION_SERVICE_KEY = "DebuggerActiveHint";
  private JBPopup myPopup;
  private final Editor myEditor;
  private final Point myPoint;
  @Nullable private final Runnable myHideRunnable;

  private DebuggerTreeWithHistoryPopup(@NotNull D initialItem,
                                       @NotNull DebuggerTreeCreator<D> creator,
                                       @NotNull Editor editor,
                                       @NotNull Point point,
                                       @NotNull Project project,
                                       @Nullable Runnable hideRunnable) {
    super(initialItem, creator, project);
    myEditor = editor;
    myPoint = point;
    myHideRunnable = hideRunnable;
  }

  public static <D> void showTreePopup(@NotNull DebuggerTreeCreator<D> creator, @NotNull D initialItem, @NotNull Editor editor,
                                       @NotNull Point point, @NotNull Project project, Runnable hideRunnable) {
    new DebuggerTreeWithHistoryPopup<D>(initialItem, creator, editor, point, project, hideRunnable).updateTree(initialItem);
  }

  private TreeModelListener createTreeListener(final Tree tree) {
    return new TreeModelAdapter() {
      @Override
      public void treeStructureChanged(TreeModelEvent e) {
        resize(e.getTreePath(), tree);
      }
    };
  }

  @Override
  protected void updateContainer(final Tree tree, String title) {
    if (myPopup != null) {
      myPopup.cancel();
    }
    tree.getModel().addTreeModelListener(createTreeListener(tree));
    myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(createMainPanel(tree), tree)
      .setRequestFocus(true)
      .setTitle(title)
      .setResizable(true)
      .setMovable(true)
      .setDimensionServiceKey(myProject, DIMENSION_SERVICE_KEY, false)
      .setMayBeParent(true)
      .setKeyEventHandler(new BooleanFunction<KeyEvent>() {
        @Override
        public boolean fun(KeyEvent event) {
          if (AbstractPopup.isCloseRequest(event)) {
            // Do not process a close request if the tree shows a speed search popup
            SpeedSearchSupply supply = SpeedSearchSupply.getSupply(tree);
            return supply != null && StringUtil.isEmpty(supply.getEnteredPrefix());
          }
          return false;
        }
      })
      .addListener(new JBPopupAdapter() {
        @Override
        public void onClosed(LightweightWindowEvent event) {
          if (myHideRunnable != null) {
            myHideRunnable.run();
          }
        }
      })
      .setCancelCallback(new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          Window parent = SwingUtilities.getWindowAncestor(tree);
          if (parent != null) {
            for (Window child : parent.getOwnedWindows()) {
              if (child.isShowing()) {
                return false;
              }
            }
          }
          return true;
        }
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
    if (myPopup == null || !myPopup.isVisible()) return;
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
