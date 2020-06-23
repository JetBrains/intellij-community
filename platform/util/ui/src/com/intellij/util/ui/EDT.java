// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * <p>This class provides a static cache for a current Swing Event Dispatch thread. As {@code EventQueue.isDispatchThread()} calls
 * are expensive, this class provides a faster way to check whether the current thread is EDT or not.
 *
 * @implNote Note that EDT can change over time, this class tries to sort out all the changes, accompanied by IdeEventQueue.
 * See {@link #updateEdt()} usage for the details
 */
public final class EDT {
  private static Thread myEventDispatchThread = null;

  private EDT() {
  }

  /**
   * Do not use it unless you know what you are doing. Updates cached EDT thread.
   */
  @ApiStatus.Internal
  public static void updateEdt() {
    myEventDispatchThread = Thread.currentThread();
  }

  @ApiStatus.Internal
  public static boolean isEdt(@NotNull Thread thread) {
    return thread == myEventDispatchThread;
  }

  /**
   * Checks whether the current thread is EDT.
   *
   * @return {@code true} if the current thread is EDT, {@code false} otherwise
   *
   * @implNote The {@code myEventDispatchThread} field is a "thread-local" storage
   *  for the current EDT. EDT thread is being updated on each swing event by IdeEventQueue so EDT memory should have the actual value
   *  at all times. {@code null} values observed by any thread leads to honest slow {@code EventQueue.isDispatchThread()} check.
   *  Non-null values can point either to current EDT or one of the previous EDT. Previous EDTs are dead so they won't be equal to
   *  any living non-EDT thread so the check result will be correct even with stale caches.
   */
  public static boolean isCurrentThreadEdt() {
    // Actually, this `if` is not required, but it makes this class work correctly before IdeEventQueue initialization
    if (myEventDispatchThread == null) {
      return EventQueue.isDispatchThread();
    }
    return isEdt(Thread.currentThread());
  }

  public static void assertIsEdt() {
    if (!isCurrentThreadEdt()) {
      Logger.getInstance(EDT.class).error("Assert: must be called on EDT");
    }
  }
}
