// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
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

  /**
   * Please use Application.invokeLater() with a modality state (or GuiUtils, or TransactionGuard methods), unless you work with Swings internals
   * and 'runnable' deals with Swings components only and doesn't access any PSI, VirtualFiles, project/module model or other project settings. For those, use GuiUtils, application.invoke* or TransactionGuard methods.<p/>
   *
   * On AWT thread, invoked runnable immediately, otherwise do {@link SwingUtilities#invokeLater(Runnable)} on it.
   */
  public static void invokeLaterIfNeeded(@NotNull Runnable runnable) {
    if (EDT.isCurrentThreadEdt()) {
      runnable.run();
    }
    else {
      getInstance().invokeLater(runnable);
    }
  }

  /**
   * @deprecated Use {@link EDT#isCurrentThreadEdt()}
   */
  @SuppressWarnings("MethodMayBeStatic")
  @Deprecated
  public final boolean isEventDispatchThread() {
    return EventQueue.isDispatchThread();
  }

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

  public static @Nullable EdtInvocationManager setEdtInvocationManager(@NotNull EdtInvocationManager edtInvocationManager) {
    return ourInstance.getAndSet(edtInvocationManager);
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
    if (EDT.isCurrentThreadEdt()) {
      runnable.run();
    }
    else {
      try {
        getInstance().invokeAndWait(runnable);
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
    public void invokeLater(@NotNull Runnable task) {
      EventQueue.invokeLater(task);
    }

    @Override
    public void invokeAndWait(@NotNull Runnable task) throws InvocationTargetException, InterruptedException {
      EventQueue.invokeAndWait(task);
    }
  }
}