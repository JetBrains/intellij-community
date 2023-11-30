// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.ExpirableRunnable;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @deprecated Use {@link IdeFocusManager}'s methods directly.
 */
@Deprecated
public final class IdeFocusManagerImpl extends IdeFocusManager {
  @Override
  public @NotNull ActionCallback requestFocus(final @NotNull Component c, final boolean forced) {
    return getGlobalInstance().requestFocus(c, forced);
  }

  @Override
  public ActionCallback requestFocusInProject(@NotNull Component c, @Nullable Project project) {
    return getGlobalInstance().requestFocusInProject(c, project);
  }

  @Override
  public JComponent getFocusTargetFor(final @NotNull JComponent comp) {
    return getGlobalInstance().getFocusTargetFor(comp);
  }

  @Override
  public void doWhenFocusSettlesDown(final @NotNull Runnable runnable) {
    getGlobalInstance().doWhenFocusSettlesDown(runnable);
  }

  @Override
  public void doWhenFocusSettlesDown(@NotNull Runnable runnable, @NotNull ModalityState modality) {
    getGlobalInstance().doWhenFocusSettlesDown(runnable, modality);
  }

  @Override
  public void doWhenFocusSettlesDown(@NotNull ExpirableRunnable runnable) {
    getGlobalInstance().doWhenFocusSettlesDown(runnable);
  }

  @Override
  public @Nullable Component getFocusedDescendantFor(final @NotNull Component comp) {
    return getGlobalInstance().getFocusedDescendantFor(comp);
  }

  @Override
  public boolean isFocusTransferEnabled() {
    return getGlobalInstance().isFocusTransferEnabled();
  }

  @Override
  public Component getFocusOwner() {
    return getGlobalInstance().getFocusOwner();
  }

  @Override
  public void runOnOwnContext(@NotNull DataContext context, @NotNull Runnable runnable) {
    getGlobalInstance().runOnOwnContext(context, runnable);
  }

  @Override
  public Component getLastFocusedFor(@Nullable Window frame) {
    return getGlobalInstance().getLastFocusedFor(frame);
  }

  @Override
  public IdeFrame getLastFocusedFrame() {
    return getGlobalInstance().getLastFocusedFrame();
  }

  @Override
  public @Nullable Window getLastFocusedIdeWindow() {
    return getGlobalInstance().getLastFocusedIdeWindow();
  }

  @Override
  public void toFront(JComponent c) {
    getGlobalInstance().toFront(c);
  }
}
