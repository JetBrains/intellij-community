package com.intellij.openapi.wm.impl;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Expirable;
import com.intellij.openapi.wm.FocusCommand;
import com.intellij.openapi.wm.IdeFocusManager;
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

  public Component getFocusedDescendantFor(final Component c) {
    return null;
  }

  public boolean dispatch(KeyEvent e) {
    return false;
  }

  @Override
  public void suspendKeyProcessingUntil(@NotNull ActionCallback done) {
  }

  @Override
  public boolean isFocusBeingTransferred() {
    return false;
  }

  public ActionCallback requestDefaultFocus(boolean forced) {
    return new ActionCallback.Done();
  }

  @Override
  public Expirable getTimestamp(boolean trackOnlyForcedCommands) {
    return new Expirable() {
      public boolean isExpired() {
        return false;
      }
    };
  }
}
