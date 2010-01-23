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

import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Expirable;
import com.intellij.openapi.wm.FocusCommand;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.ui.FocusTrackback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

public class IdeFocusManagerImpl extends IdeFocusManager {
  private final ToolWindowManagerImpl myToolWindowManager;

  public IdeFocusManagerImpl(ToolWindowManagerImpl toolWindowManager) {
    myToolWindowManager = toolWindowManager;
  }

  @NotNull
  public ActionCallback requestFocus(@NotNull final Component c, final boolean forced) {
    return myToolWindowManager.requestFocus(c, forced);
  }

  @NotNull
  public ActionCallback requestFocus(@NotNull final FocusCommand command, final boolean forced) {
    return myToolWindowManager.requestFocus(command, forced);
  }

  public JComponent getFocusTargetFor(@NotNull final JComponent comp) {
    return IdeFocusTraversalPolicy.getPreferredFocusedComponent(comp);
  }

  public void doWhenFocusSettlesDown(@NotNull final Runnable runnable) {
    myToolWindowManager.doWhenFocusSettlesDown(runnable);
  }

  @Nullable
  public Component getFocusedDescendantFor(@NotNull final Component comp) {
    final Component focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (focused == null) return null;

    if (focused == comp || SwingUtilities.isDescendingFrom(focused, comp)) return focused;

    java.util.List<JBPopup> popups = FocusTrackback.getChildPopups(comp);
    for (JBPopup each : popups) {
      if (each.isFocused()) return focused;
    }

    return null;
  }

  public boolean dispatch(KeyEvent e) {
    return myToolWindowManager.dispatch(e);
  }

  @Override
  public void suspendKeyProcessingUntil(@NotNull ActionCallback done) {
    myToolWindowManager.suspendKeyProcessingUntil(done);
  }


  public ActionCallback requestDefaultFocus(boolean forced) {
    return myToolWindowManager.requestDefaultFocus(forced);
  }

  @Override
  public Expirable getTimestamp(boolean trackOnlyForcedCommands) {
    return myToolWindowManager.getTimestamp(trackOnlyForcedCommands);
  }

  public boolean isFocusBeingTransferred() {
    return !myToolWindowManager.isFocusTransferReady();
  }
}
