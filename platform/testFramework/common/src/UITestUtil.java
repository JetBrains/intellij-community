// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import sun.awt.AWTAutoShutdown;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;

@TestOnly
@Internal
public final class UITestUtil {

  private UITestUtil() { }

  public static boolean getAndSetHeadlessProperty() {
    if ("false".equals(System.getProperty("java.awt.headless"))) {
      return false;
    }
    else {
      setHeadlessProperty(true);
      return true;
    }
  }

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

  public static void setupEventQueue() {
    if (EventQueue.isDispatchThread()) {
      //noinspection ResultOfMethodCallIgnored
      IdeEventQueue.getInstance(); // replaces system event queue
    }
    else {
      replaceIdeEventQueueSafely();
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
    StartupUiUtil.INSTANCE.pump();
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
