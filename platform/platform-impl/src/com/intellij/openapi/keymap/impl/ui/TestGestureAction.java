// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionEventVisitor;
import com.intellij.openapi.actionSystem.KeyboardGestureAction;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

public class TestGestureAction extends AnAction implements KeyboardGestureAction, DumbAware {
  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    e.accept(new AnActionEventVisitor() {
      @Override
      public void visitGestureInitEvent(@NotNull final AnActionEvent e) {
        System.out.println("TestGestureAction.visitGestureInitEvent");
      }

      @Override
      public void visitGesturePerformedEvent(@NotNull final AnActionEvent e) {
        System.out.println("TestGestureAction.visitGesturePerformedEvent");
      }

      @Override
      public void visitGestureFinishEvent(@NotNull final AnActionEvent e) {
        System.out.println("TestGestureAction.visitGestureFinishEvent");
      }

      @Override
      public void visitEvent(@NotNull final AnActionEvent e) {
        System.out.println("TestGestureAction.visitEvent");
      }
    });
  }
}