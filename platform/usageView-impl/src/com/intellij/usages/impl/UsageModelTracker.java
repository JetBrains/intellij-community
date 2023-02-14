// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class UsageModelTracker implements Disposable {
  @FunctionalInterface
  public interface UsageModelTrackerListener {
    void modelChanged(boolean isPropertyChange);
  }

  private final List<UsageModelTrackerListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public UsageModelTracker(@NotNull Project project) {
    final PsiTreeChangeListener myPsiListener = new PsiTreeChangeAdapter() {
      @Override
      public void childAdded(@NotNull PsiTreeChangeEvent event) {
        doFire(event, false);
      }

      @Override
      public void childRemoved(@NotNull PsiTreeChangeEvent event) {
        doFire(event, false);
      }

      @Override
      public void childReplaced(@NotNull PsiTreeChangeEvent event) {
        doFire(event, false);
      }

      @Override
      public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
        doFire(event, false);
      }

      @Override
      public void childMoved(@NotNull PsiTreeChangeEvent event) {
        doFire(event, false);
      }

      @Override
      public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
        doFire(event, true);
      }
    };
    PsiManager.getInstance(project).addPsiTreeChangeListener(myPsiListener, this);
  }

  private void doFire(@NotNull PsiTreeChangeEvent event, boolean propertyChange) {
    if (!(event.getFile() instanceof PsiCodeFragment)) {
      for (UsageModelTrackerListener listener : myListeners) {
        listener.modelChanged(propertyChange);
      }
    }
  }

  @Override
  public void dispose() {
  }

  public void addListener(@NotNull UsageModelTrackerListener listener, @NotNull Disposable parent) {
    myListeners.add(listener);
    Disposer.register(parent, () -> myListeners.remove(listener));
  }
}
