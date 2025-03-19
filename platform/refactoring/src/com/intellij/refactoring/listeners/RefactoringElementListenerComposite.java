// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.listeners;

import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class RefactoringElementListenerComposite implements RefactoringElementListener, UndoRefactoringElementListener {
  private final List<RefactoringElementListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public void addListener(final RefactoringElementListener listener){
    myListeners.add(listener);
  }

  @Override
  public void elementMoved(final @NotNull PsiElement newElement){
    for (RefactoringElementListener myListener : myListeners) {
      myListener.elementMoved(newElement);
    }
  }

  @Override
  public void elementRenamed(final @NotNull PsiElement newElement){
    for (RefactoringElementListener myListener : myListeners) {
      myListener.elementRenamed(newElement);
    }
  }

  @Override
  public void undoElementMovedOrRenamed(@NotNull PsiElement newElement, @NotNull String oldQualifiedName) {
    for (RefactoringElementListener listener : myListeners) {
      if (listener instanceof UndoRefactoringElementListener) {
        ((UndoRefactoringElementListener)listener).undoElementMovedOrRenamed(newElement, oldQualifiedName);
      }
    }
  }
}
