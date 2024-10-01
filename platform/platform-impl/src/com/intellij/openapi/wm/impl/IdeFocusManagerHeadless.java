// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.ExpirableRunnable;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

@ApiStatus.Internal
public final class IdeFocusManagerHeadless extends IdeFocusManager { // FIXME-ank: reverted final
  public static final IdeFocusManagerHeadless INSTANCE = new IdeFocusManagerHeadless();

  @Override
  public @NotNull ActionCallback requestFocus(final @NotNull Component c, final boolean forced) {
    return ActionCallback.DONE;
  }

  @Override
  public JComponent getFocusTargetFor(final @NotNull JComponent comp) {
    return null;
  }

  @Override
  public void doWhenFocusSettlesDown(final @NotNull Runnable runnable) {
    runnable.run();
  }

  @Override
  public void doWhenFocusSettlesDown(@NotNull Runnable runnable, @NotNull ModalityState modality) {
    runnable.run();
  }

  @Override
  public void doWhenFocusSettlesDown(@NotNull ExpirableRunnable runnable) {
    if (!runnable.isExpired()) {
      runnable.run();
    }
  }

  @Override
  public Component getFocusedDescendantFor(@NotNull Component c) {
    return null;
  }

  @Override
  public boolean isFocusTransferEnabled() {
    return true;
  }

  @Override
  public Component getFocusOwner() {
    return null;
  }

  @Override
  public void runOnOwnContext(@NotNull DataContext context, @NotNull Runnable runnable) {
    runnable.run();
  }

  @Override
  public Component getLastFocusedFor(@Nullable Window frame) {
    return null;
  }

  @Override
  public IdeFrame getLastFocusedFrame() {
    return null;
  }

  @Override
  public @Nullable Window getLastFocusedIdeWindow() {
    return null;
  }

  @Override
  public void toFront(JComponent c) {
  }
}
