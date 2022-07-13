// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import sun.awt.AWTAutoShutdown;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;

public class UITestUtil {
  /**
   * Sets "java.awt.headless" system property
   */
  public static void setHeadlessProperty(boolean isHeadless) {
    System.setProperty("java.awt.headless", Boolean.toString(isHeadless));
  }

  /**
   * Executes {@code runnable} with the specified "java.awt.headless" system property and restore its value afterwards
   */
  public static <E extends Throwable> void runWithHeadlessProperty(boolean propertyValue, @NotNull ThrowableRunnable<E> runnable) throws E {
    boolean old = Boolean.getBoolean("java.awt.headless");
    setHeadlessProperty(propertyValue);
    try {
      runnable.run();
    }
    finally {
      setHeadlessProperty(old);
    }
  }

  public static void replaceIdeEventQueueSafely() {
    if (Toolkit.getDefaultToolkit().getSystemEventQueue() instanceof IdeEventQueue) {
      return;
    }

    if (SwingUtilities.isEventDispatchThread()) {
      throw new IllegalStateException("must not be called on EDT");
    }

    AWTAutoShutdown.getInstance().notifyThreadBusy(Thread.currentThread());

    // in JDK 1.6, `EventQueue#push` causes slow painful death of current EDT, so we have to wait through its agony to termination
    UIUtil.pump();
    try {
      //noinspection ResultOfMethodCallIgnored
      EventQueue.invokeAndWait(IdeEventQueue::getInstance);
      EventQueue.invokeAndWait(EmptyRunnable.getInstance());
      EventQueue.invokeAndWait(EmptyRunnable.getInstance());
    }
    catch (InterruptedException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }
}
