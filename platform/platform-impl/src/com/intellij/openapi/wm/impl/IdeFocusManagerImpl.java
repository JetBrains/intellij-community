// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.ExpirableRunnable;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Per-project focus manager implementation
 */

@ApiStatus.Internal
public final class IdeFocusManagerImpl extends IdeFocusManager {

  private final Project myProject;

  public IdeFocusManagerImpl(Project project) {
    myProject = project;
  }

  @Override
  @NotNull
  public ActionCallback requestFocus(@NotNull final Component c, final boolean forced) {
    if (ApplicationManager.getApplication().isActive() || !UIUtil.SUPPRESS_FOCUS_STEALING) {
      c.requestFocus();
    }
    else {
      c.requestFocusInWindow();
    }
    return ActionCallback.DONE;
  }

  @NotNull
  @Override
  public ActionCallback requestDefaultFocus(boolean forced) {

    IdeFrame frame = WindowManagerEx.getInstanceEx().findFrameFor(myProject);
    JComponent toFocus = frame .getComponent();

    if (toFocus == null) return ActionCallback.REJECTED;

    JComponent focusTargetForFrame = getFocusTargetFor(frame.getComponent());

    if (focusTargetForFrame == null ) return  ActionCallback.REJECTED;

    if (ApplicationManager.getApplication().isActive() || !UIUtil.SUPPRESS_FOCUS_STEALING) {
      focusTargetForFrame.requestFocus();
    }
    else {
      focusTargetForFrame.requestFocusInWindow();
    }

    return ActionCallback.DONE;
  }

  @Override
  public ActionCallback requestFocusInProject(@NotNull Component c, @Nullable Project project) {
    return getGlobalInstance().requestFocusInProject(c, project);
  }

  @Override
  public JComponent getFocusTargetFor(@NotNull final JComponent comp) {
    return getGlobalInstance().getFocusTargetFor(comp);
  }

  @Override
  public void doWhenFocusSettlesDown(@NotNull final Runnable runnable) {
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
  @Nullable
  public Component getFocusedDescendantFor(@NotNull final Component comp) {
    return getGlobalInstance().getFocusedDescendantFor(comp);
  }

  @Override
  public void typeAheadUntil(@NotNull ActionCallback callback, @NotNull String cause) {
    getGlobalInstance().typeAheadUntil(callback, cause);
  }

  @Override
  public boolean isFocusTransferEnabled() {
    return getGlobalInstance().isFocusTransferEnabled();
  }

  @Override
  public void setTypeaheadEnabled(boolean enabled) {
    getGlobalInstance().setTypeaheadEnabled(enabled);
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
  public Component getLastFocusedFor(IdeFrame frame) {
    return getGlobalInstance().getLastFocusedFor(frame);
  }

  @Override
  public IdeFrame getLastFocusedFrame() {
    return getGlobalInstance().getLastFocusedFrame();
  }

  @Override
  public void toFront(JComponent c) {
    getGlobalInstance().toFront(c);
  }
}
