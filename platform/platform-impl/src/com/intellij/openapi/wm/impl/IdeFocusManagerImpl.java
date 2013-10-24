/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Expirable;
import com.intellij.openapi.util.ExpirableRunnable;
import com.intellij.openapi.wm.FocusCommand;
import com.intellij.openapi.wm.FocusRequestor;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

public class IdeFocusManagerImpl extends IdeFocusManager {
  private final ToolWindowManagerImpl myToolWindowManager;

  public IdeFocusManagerImpl(ToolWindowManagerImpl twManager) {
    myToolWindowManager = twManager;
  }

  @Override
  @NotNull
  public ActionCallback requestFocus(@NotNull final Component c, final boolean forced) {
    return getGlobalInstance().requestFocus(c, forced);
  }

  @Override
  @NotNull
  public ActionCallback requestFocus(@NotNull final FocusCommand command, final boolean forced) {
    return getGlobalInstance().requestFocus(command, forced);
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
  public void doWhenFocusSettlesDown(@NotNull ExpirableRunnable runnable) {
    getGlobalInstance().doWhenFocusSettlesDown(runnable);
  }

  @Override
  @Nullable
  public Component getFocusedDescendantFor(@NotNull final Component comp) {
    return getGlobalInstance().getFocusedDescendantFor(comp);
  }

  @Override
  public boolean dispatch(@NotNull KeyEvent e) {
    return getGlobalInstance().dispatch(e);
  }

  @Override
  public void typeAheadUntil(ActionCallback done) {
    getGlobalInstance().typeAheadUntil(done);
  }


  @NotNull
  @Override
  public ActionCallback requestDefaultFocus(boolean forced) {
    return myToolWindowManager.requestDefaultFocus(forced);
  }

  @Override
  public boolean isFocusTransferEnabled() {
    return getGlobalInstance().isFocusTransferEnabled();
  }

  @NotNull
  @Override
  public Expirable getTimestamp(boolean trackOnlyForcedCommands) {
    return getGlobalInstance().getTimestamp(trackOnlyForcedCommands);
  }

  @NotNull
  @Override
  public FocusRequestor getFurtherRequestor() {
    return getGlobalInstance().getFurtherRequestor();
  }

  @Override
  public void revalidateFocus(@NotNull ExpirableRunnable runnable) {
    getGlobalInstance().revalidateFocus(runnable);
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

  @Override
  public boolean isFocusBeingTransferred() {
    return getGlobalInstance().isFocusBeingTransferred();
  }

  @Override
  public void dispose() {
  }
}
