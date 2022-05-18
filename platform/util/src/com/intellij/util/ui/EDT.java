// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  private static Thread myEventDispatchThread;

  private EDT() { }

  /**
   * Updates cached EDT thread.
   * <strong>Do not use it unless you know what you are doing!</strong>
   */
  @ApiStatus.Internal
  public static void updateEdt() {
    if (myEventDispatchThread != Thread.currentThread()) {
      myEventDispatchThread = Thread.currentThread();
    }
  }

  @ApiStatus.Internal
  public static boolean isEdt(@NotNull Thread thread) {
    return thread == myEventDispatchThread;
  }

  @ApiStatus.Internal
  public static @NotNull Thread getEventDispatchThread() {
    return myEventDispatchThread;
  }

  /**
   * Checks whether the current thread is EDT.
   *
   * @return {@code true} if the current thread is EDT, {@code false} otherwise
   *
   * @implNote The {@link #myEventDispatchThread} field is a "thread-local" storage for the current EDT.
   * The value is updated on each Swing event by {@link com.intellij.ide.IdeEventQueue}, so it should be actual value at any time.
   * A {@code null} value observed by any thread leads to honest slow {@code EventQueue.isDispatchThread()} check.
   * Non-null values can point either to the current EDT or one of the previous EDT.
   * Previous EDTs are dead, so they won't be equal to any living non-EDT thread, so the result will be correct even with stale caches.
   */
  public static boolean isCurrentThreadEdt() {
    // actually, this `if` is not required, but it makes the class work correctly before `IdeEventQueue` initialization
    return myEventDispatchThread == null ? EventQueue.isDispatchThread() : isEdt(Thread.currentThread());
  }

  public static void assertIsEdt() {
    if (!isCurrentThreadEdt()) {
      Logger.getInstance(EDT.class).error("Assert: must be called on EDT");
    }
  }
}
