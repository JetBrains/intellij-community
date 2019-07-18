// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.util.ui.StartupUiUtil;
import sun.awt.AWTAutoShutdown;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public final class TestRunnerUtil {
  private TestRunnerUtil() {
  }

  public static void replaceIdeEventQueueSafely() {
    if (Toolkit.getDefaultToolkit().getSystemEventQueue() instanceof IdeEventQueue) {
      return;
    }
    if (SwingUtilities.isEventDispatchThread()) {
      throw new RuntimeException("must not call under EDT");
    }
    AWTAutoShutdown.getInstance().notifyThreadBusy(Thread.currentThread());
    // in JDK 1.6 java.awt.EventQueue.push() causes slow painful death of current EDT
    // so we have to wait through its agony to termination
    try {
      StartupUiUtil.pump();
      SwingUtilities.invokeAndWait(() -> IdeEventQueue.getInstance());
      SwingUtilities.invokeAndWait(EmptyRunnable.getInstance());
      SwingUtilities.invokeAndWait(EmptyRunnable.getInstance());
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
