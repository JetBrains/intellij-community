// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class TestGestureAction extends AnAction implements KeyboardGestureAction, DumbAware {
  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    e.accept(new AnActionEventVisitor() {
      @Override
      public void visitGestureInitEvent(final @NotNull AnActionEvent e) {
        System.out.println("TestGestureAction.visitGestureInitEvent");
      }

      @Override
      public void visitGesturePerformedEvent(final @NotNull AnActionEvent e) {
        System.out.println("TestGestureAction.visitGesturePerformedEvent");
      }

      @Override
      public void visitGestureFinishEvent(final @NotNull AnActionEvent e) {
        System.out.println("TestGestureAction.visitGestureFinishEvent");
      }

      @Override
      public void visitEvent(final @NotNull AnActionEvent e) {
        System.out.println("TestGestureAction.visitEvent");
      }
    });
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}