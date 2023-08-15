// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

@ApiStatus.Internal
public abstract class AbstractEditor extends JPanel implements Disposable {
  volatile boolean myDisposed;

  AbstractEditor(@NotNull Disposable parent) {
    super(new BorderLayout());

    Disposer.register(parent, this);
  }

  @Override
  public final void dispose() {
    if (!myDisposed) {
      myDisposed = true;
      disposeOnce();
    }
  }

  abstract void disposeOnce();

  abstract Action getApplyAction();

  abstract Action getResetAction();

  abstract @NonNls String getHelpTopic();

  abstract boolean apply();

  boolean cancel(AWTEvent source) {
    return true;
  }

  abstract JComponent getPreferredFocusedComponent();
}
