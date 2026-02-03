// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac.screenmenu;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.Arrays;
import java.util.Objects;

public final class MenuBar extends Menu {
  private static long[] ourLastMenuBarPeers;

  private @Nullable Window myFrame;
  private final WindowListener myListener = new WindowAdapter() {
    @Override
    public void windowActivated(WindowEvent e) {
      refillIfNeeded();
    }
  };

  public MenuBar(@Nullable String title, @NotNull JFrame frame) {
    super(title);
    setFrame(frame);
  }

  public synchronized void setFrame(@Nullable Window frame) {
    Objects.requireNonNull(myListener);
    if (myFrame != null) {
      myFrame.removeWindowListener(myListener);
    }
    myFrame = frame;
    if (myFrame != null) {
      myFrame.addWindowListener(myListener);
    }
  }

  /** @noinspection NonPrivateFieldAccessedInSynchronizedContext*/
  private synchronized void refillIfNeeded() {
    if (Arrays.equals(myCachedPeers, ourLastMenuBarPeers)) {
      return;
    }
    refillImpl(true);
  }

  /** @noinspection NonPrivateFieldAccessedInSynchronizedContext, AssignmentToStaticFieldFromInstanceMethod */
  @Override
  synchronized void refillImpl(boolean onAppKit) {
    if (myCachedPeers != null && myFrame != null && myFrame.isActive()) {
      ourLastMenuBarPeers = myCachedPeers;
      nativeRefill(0, myCachedPeers, onAppKit);
    }
  }

  @Override
  public synchronized void dispose() {
    setFrame(null);
    super.dispose();
  }
}
