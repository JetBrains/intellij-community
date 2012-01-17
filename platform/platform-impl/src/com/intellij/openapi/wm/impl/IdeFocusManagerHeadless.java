/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

public class IdeFocusManagerHeadless extends IdeFocusManager {

  public static final IdeFocusManagerHeadless INSTANCE = new IdeFocusManagerHeadless();

  @NotNull
  public ActionCallback requestFocus(@NotNull final Component c, final boolean forced) {
    return new ActionCallback.Done();
  }

  @NotNull
  public ActionCallback requestFocus(@NotNull final FocusCommand command, final boolean forced) {
    return new ActionCallback.Done();
  }

  public JComponent getFocusTargetFor(@NotNull final JComponent comp) {
    return null;
  }

  public void doWhenFocusSettlesDown(@NotNull final Runnable runnable) {
    runnable.run();
  }

  @Override
  public void doWhenFocusSettlesDown(@NotNull ExpirableRunnable runnable) {
    if (!runnable.isExpired()) {
      runnable.run();
    }
  }

  public Component getFocusedDescendantFor(final Component c) {
    return null;
  }

  public boolean dispatch(KeyEvent e) {
    return false;
  }

  @Override
  public void typeAheadUntil(ActionCallback done) {
  }

  @Override
  public boolean isFocusBeingTransferred() {
    return false;
  }

  public ActionCallback requestDefaultFocus(boolean forced) {
    return new ActionCallback.Done();
  }

  @Override
  public boolean isFocusTransferEnabled() {
    return true;
  }

  @Override
  public Expirable getTimestamp(boolean trackOnlyForcedCommands) {
    return new Expirable() {
      public boolean isExpired() {
        return false;
      }
    };
  }

  @Override
  public FocusRequestor getFurtherRequestor() {
    return this;
  }

  @Override
  public void revalidateFocus(@NotNull ExpirableRunnable runnable) {
  }

  @Override
  public void setTypeaheadEnabled(boolean enabled) {
  }

  @Override
  public Component getFocusOwner() {
    return null;
  }

  @Override
  public void runOnOwnContext(DataContext context, Runnable runnable) {
    runnable.run();
  }

  @Override
  public Component getLastFocusedFor(IdeFrame frame) {
    return null;
  }

  @Override
  public IdeFrame getLastFocusedFrame() {
    return null;
  }

  @Override
  public void toFront(JComponent c) {
  }

  @Override
  public void dispose() {
  }
}
