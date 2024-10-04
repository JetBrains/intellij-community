// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ClickListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

public final class TrackRunningTestUtil {
  private TrackRunningTestUtil() { }

  public static void installStopListeners(JTree tree, Disposable parentDisposable, Consumer<? super AbstractTestProxy> setSelection) {
    final ClickListener userSelectionListener = new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        setSelection.accept(getUserSelection(tree));
        return true;
      }
    };
    userSelectionListener.installOn(tree);
    final KeyAdapter keyAdapter = new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent e) {
        final int keyCode = e.getKeyCode();
        if (keyCode == KeyEvent.VK_DOWN || keyCode == KeyEvent.VK_UP ||
            keyCode == KeyEvent.VK_LEFT || keyCode == KeyEvent.VK_RIGHT ||
            keyCode == KeyEvent.VK_PAGE_DOWN || keyCode == KeyEvent.VK_PAGE_UP) {
          setSelection.accept(getUserSelection(tree));
        }
      }
    };
    tree.addKeyListener(keyAdapter);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        userSelectionListener.uninstall(tree);
        tree.removeKeyListener(keyAdapter);
      }
    });
  }

  @Nullable
  private static AbstractTestProxy getUserSelection(JTree tree) {
    TreePath treePath = tree.getSelectionPath();
    if (treePath != null) {
      final Object component = treePath.getLastPathComponent();
      if (component instanceof DefaultMutableTreeNode) {
        final Object userObject = ((DefaultMutableTreeNode)component).getUserObject();
        if (userObject instanceof NodeDescriptor) {
          return (AbstractTestProxy)((NodeDescriptor<?>)userObject).getElement();
        }
      }
    }
    return null;
  }
}