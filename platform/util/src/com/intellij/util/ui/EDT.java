// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.concurrency.ThreadContext;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ExceptionUtilRt;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.*;
import java.awt.event.InvocationEvent;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

/**
 * <p>This class provides a static cache for a current Swing Event Dispatch thread. As {@code EventQueue.isDispatchThread()} calls
 * are expensive, this class provides a faster way to check whether the current thread is EDT or not.
 *
 * @implNote Note that EDT can change over time, this class tries to sort out all the changes, accompanied by IdeEventQueue.
 * See {@link #updateEdt()} usage for the details
 */
public final class EDT {
  private static volatile Thread ourThread;

  private static boolean disableEdtChecks = false;

  private EDT() { }

  @ApiStatus.Internal
  public static void disableEdtChecks() {
    disableEdtChecks = true;
  }

  @ApiStatus.Internal
  public static boolean isDisableEdtChecks() {
    return disableEdtChecks;
  }

  /**
   * Updates cached EDT thread.
   * <strong>Do not use it unless you know what you are doing!</strong>
   */
  @ApiStatus.Internal
  public static void updateEdt() {
    ourThread = Thread.currentThread();
  }

  @ApiStatus.Internal
  public static @NotNull Thread getEventDispatchThread() {
    return ourThread;
  }

  @ApiStatus.Internal
  public static @Nullable Thread getEventDispatchThreadOrNull() {
    return ourThread;
  }

  /**
   * Checks whether the current thread is EDT.
   *
   * @return {@code true} if the current thread is EDT, {@code false} otherwise
   * @implNote The {@link #ourThread} field is a "thread-local" storage for the current EDT.
   * The value is updated on each Swing event by {@link com.intellij.ide.IdeEventQueue}, so it should be actual value at any time.
   * A {@code null} value observed by any thread leads to honest slow {@code EventQueue.isDispatchThread()} check.
   * Non-null values can point either to the current EDT or one of the previous EDT.
   * Previous EDTs are dead, so they won't be equal to any living non-EDT thread, so the result will be correct even with stale caches.
   */
  public static boolean isCurrentThreadEdt() {
    // actually, this `if` is not required, but it makes the class work correctly before `IdeEventQueue` initialization
    Thread thread = ourThread;
    return thread != null ? Thread.currentThread() == thread :
           !disableEdtChecks && EventQueue.isDispatchThread();
  }

  public static void assertIsEdt() {
    if (!isCurrentThreadEdt() && !disableEdtChecks) {
      Logger.getInstance(EDT.class).error("Assert: must be called on EDT");
    }
  }

  private static MethodHandle dispatchEventMethod;

  /**
   * Dispatch all pending invocation events (if any) in the {@link com.intellij.ide.IdeEventQueue}, ignores and removes all other events from the queue.
   * Do not use outside tests because this method is messing with the EDT event queue which can be dangerous
   *
   * @see UIUtil#pump()
   * @see com.intellij.testFramework.PlatformTestUtil#dispatchAllInvocationEventsInIdeEventQueue()
   */
  @TestOnly
  public static void dispatchAllInvocationEvents() {
    ThreadContext.resetThreadContext(() -> {
      dispatchAllInvocationEventsImpl();
      return null;
    });
  }

  private static void dispatchAllInvocationEventsImpl() {
    assertIsEdt();

    EventQueue eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();

    MethodHandle dispatchEventMethod = EDT.dispatchEventMethod;
    if (dispatchEventMethod == null) {
      try {
        Method method = EventQueue.class.getDeclaredMethod("dispatchEvent", AWTEvent.class);
        method.setAccessible(true);
        dispatchEventMethod = MethodHandles.lookup().unreflect(method);
      }
      catch (NoSuchMethodException | IllegalAccessException e) {
        throw new RuntimeException();
      }

      EDT.dispatchEventMethod = dispatchEventMethod;
    }
    boolean threadsDumped = false;
    for (int i = 1; ; i++) {
      AWTEvent event = eventQueue.peekEvent();
      if (event == null) break;
      try {
        event = eventQueue.getNextEvent();
        if (event instanceof InvocationEvent) {
          dispatchEventMethod.bindTo(eventQueue).invoke(event);
        }
      }
      catch (Throwable e) {
        ExceptionUtilRt.rethrowUnchecked(e);
        throw new RuntimeException(e);
      }

      if (i % 10000 == 0) {
        //noinspection UseOfSystemOutOrSystemErr
        System.out.println("Suspiciously many (" + i + ") AWT events, last dispatched " + event);
        if (!threadsDumped) {
          threadsDumped = true;
          // todo temporary hack to diagnose hanging builds
          try {
            Object application =
              ReflectionUtil.getMethod(Class.forName("com.intellij.openapi.application.ApplicationManager"), "getApplication")
                .invoke(null);
            System.err.println("Application=" + application + "\n" + ThreadDumper.dumpThreadsToString());
          }
          catch (Exception e) {
            throw new RuntimeException(e);
          }
        }
      }
    }
  }
}
