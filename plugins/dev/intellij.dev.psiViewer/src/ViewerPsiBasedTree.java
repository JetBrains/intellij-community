// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.psiViewer;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionListener;

public interface ViewerPsiBasedTree extends Disposable {

  interface PsiTreeUpdater {
    void updatePsiTree(@NotNull PsiElement toSelect, @Nullable TextRange selectRangeInEditor);
  }

  void reloadTree(@Nullable PsiElement rootRootElement, @NotNull String text);

  void selectNodeFromPsi(@Nullable PsiElement element);

  default void selectNodeFromEditor(@Nullable PsiElement element) {
    selectNodeFromPsi(element);
  }

  @NotNull
  JComponent getComponent();

  boolean isFocusOwner();


  void focusTree();

  static void removeListenerOfClass(@NotNull Tree tree, @NotNull Class<?> listenerClass) {
    TreeSelectionListener[] listeners = tree.getTreeSelectionListeners();

    for (TreeSelectionListener listener : listeners) {
      if (listenerClass.isInstance(listener)) {
        tree.removeTreeSelectionListener(listener);
      }
    }
  }
}
