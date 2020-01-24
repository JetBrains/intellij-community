// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree.ui;

import com.intellij.openapi.diagnostic.Logger;

import static java.awt.EventQueue.isDispatchThread;

/**
 * The Swing library is not thread-safe and all methods must be invoked from the event dispatch thread
 * (<a href="http://docs.oracle.com/javase/tutorial/uiswing/concurrency/dispatch.html">EDT</a>).
 */
final class DispatchThreadValidator {
  private static final Logger LOG = Logger.getInstance(DispatchThreadValidator.class);
  private volatile Thread background = getBackgroundThread();

  private static Thread getBackgroundThread() {
    return isDispatchThread() ? null : Thread.currentThread();
  }

  public boolean isValidThread() {
    Thread thread = getBackgroundThread();
    if (thread == null) {
      background = null; // the background thread is not allowed after the first access from the EDT
      return true; // the EDT is always allowed
    }
    if (thread == background) {
      return true; // the background thread is allowed only before the first access from the EDT
    }
    LOG.debug(new IllegalStateException("unexpected thread: " + thread));
    return false; // a background thread is not allowed to handle Swing components
  }
}
