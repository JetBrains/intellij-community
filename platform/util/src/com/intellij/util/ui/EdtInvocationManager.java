// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ExceptionUtilRt;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InvocationEvent;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <p>Encapsulates EDT-related checks and processing. The general idea is that IntelliJ threading model is tightly bound with EDT
 * (e.g. write access is allowed from EDT only and any task executed from EDT is implicitly granted read access). That makes
 * a huge bottleneck in non-IntelliJ environments like Upsource - every vcs revision there is represented as a separate ide project
 * object, hence, global shared write lock and single EDT become a problem.</p>
 *
 * <p>That's why it should be possible to change that model in non-IntelliJ environment - that involves either custom read/write locks
 * processing or custom EDT processing as well. This interface covers EDT part.</p>
 */
public abstract class EdtInvocationManager {
  private static final AtomicReference<EdtInvocationManager> ourInstance = new AtomicReference<>();

  private static MethodHandle dispatchEventMethod;

  /**
   * Dispatch all pending invocation events (if any) in the {@link com.intellij.ide.IdeEventQueue}, ignores and removes all other events from the queue.
   * In tests, consider using {@link com.intellij.testFramework.PlatformTestUtil#dispatchAllInvocationEventsInIdeEventQueue()}
   * @see UIUtil#pump()
   */
  @TestOnly
  public static void dispatchAllInvocationEvents() {
    assert getInstance().isEventDispatchThread() : Thread.currentThread() + "; EDT: " + getEventQueueThread();
    EventQueue eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();

    MethodHandle dispatchEventMethod = EdtInvocationManager.dispatchEventMethod;
    if (dispatchEventMethod == null) {
      try {
        Method method = EventQueue.class.getDeclaredMethod("dispatchEvent", AWTEvent.class);
        method.setAccessible(true);
        dispatchEventMethod = MethodHandles.lookup().unreflect(method);
      }
      catch (NoSuchMethodException | IllegalAccessException e) {
        throw new RuntimeException();
      }

      EdtInvocationManager.dispatchEventMethod = dispatchEventMethod;
    }

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
      }
    }
  }

  private static @NotNull Thread getEventQueueThread() {
    EventQueue eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
    try {
      Method method = ReflectionUtil.getDeclaredMethod(EventQueue.class, "getDispatchThread");
      //noinspection ConstantConditions
      return (Thread)method.invoke(eventQueue);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Please use Application.invokeLater() with a modality state (or GuiUtils, or TransactionGuard methods), unless you work with Swings internals
   * and 'runnable' deals with Swings components only and doesn't access any PSI, VirtualFiles, project/module model or other project settings. For those, use GuiUtils, application.invoke* or TransactionGuard methods.<p/>
   *
   * On AWT thread, invoked runnable immediately, otherwise do {@link SwingUtilities#invokeLater(Runnable)} on it.
   */
  public static void invokeLaterIfNeeded(@NotNull Runnable runnable) {
    EdtInvocationManager edtInvocationManager = getInstance();
    if (edtInvocationManager.isEventDispatchThread()) {
      runnable.run();
    }
    else {
      edtInvocationManager.invokeLater(runnable);
    }
  }

  public abstract boolean isEventDispatchThread();

  public abstract void invokeLater(@NotNull Runnable task);

  public abstract void invokeAndWait(@NotNull Runnable task) throws InvocationTargetException, InterruptedException;

  @NotNull
  public static EdtInvocationManager getInstance() {
    EdtInvocationManager result = ourInstance.get();
    if (result == null) {
      result = new SwingEdtInvocationManager();
      if (!ourInstance.compareAndSet(null, result)) {
        result = ourInstance.get();
      }
    }
    return result;
  }

  @SuppressWarnings("unused") // Used in Upsource
  public static @Nullable EdtInvocationManager setEdtInvocationManager(@NotNull EdtInvocationManager edtInvocationManager) {
    return ourInstance.getAndSet(edtInvocationManager);
  }

  @ApiStatus.Internal
  public static void restoreEdtInvocationManager(@NotNull EdtInvocationManager current, @Nullable EdtInvocationManager old) {
    ourInstance.compareAndSet(current, old);
  }

  /**
   * Please use Application.invokeAndWait() with a modality state (or GuiUtils, or TransactionGuard methods), unless you work with Swings internals
   * and 'runnable' deals with Swings components only and doesn't access any PSI, VirtualFiles, project/module model or other project settings.<p/>
   *
   * Invoke and wait in the event dispatch thread
   * or in the current thread if the current thread
   * is event queue thread.
   * DO NOT INVOKE THIS METHOD FROM UNDER READ ACTION.
   */
  public static void invokeAndWaitIfNeeded(@NotNull Runnable runnable) {
    EdtInvocationManager manager = getInstance();
    if (manager.isEventDispatchThread()) {
      runnable.run();
    }
    else {
      try {
        manager.invokeAndWait(runnable);
      }
      catch (Exception e) {
        Logger.getInstance(EdtInvocationManager.class).error(e);
      }
    }
  }

  /**
   * The default {@link EdtInvocationManager} implementation that uses {@link EventQueue}.
   */
  public static class SwingEdtInvocationManager extends EdtInvocationManager {
    @Override
    public boolean isEventDispatchThread() {
      return EventQueue.isDispatchThread();
    }

    @Override
    public void invokeLater(@NotNull Runnable task) {
      EventQueue.invokeLater(task);
    }

    @Override
    public void invokeAndWait(@NotNull Runnable task) throws InvocationTargetException, InterruptedException {
      EventQueue.invokeAndWait(task);
    }
  }
}