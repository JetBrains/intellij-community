// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Encapsulates EDT-related checks and processing. The general idea is that intellij threading model is tightly bound with EDT
 * (e.g. write access is allowed from EDT only and any task executed from EDT is implicitly granted read access). That makes
 * a huge bottleneck in non-intellij environments like upsource - every vcs revision there is represented as a separate ide project
 * object, hence, global shared write lock and single EDT become a problem.
 * <p/>
 * That's why it should be possible to change that model in non-intellij environment - that involves either custom read/write locks
 * processing or custom EDT processing as well. This interface covers EDT part.
 */
public abstract class EdtInvocationManager {
  private static final AtomicReference<EdtInvocationManager> ourInstance = new AtomicReference<>();

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

  @SuppressWarnings("unused") // Used in upsource
  public static void setEdtInvocationManager(@NotNull EdtInvocationManager edtInvocationManager) {
    ourInstance.set(edtInvocationManager);
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
  public void invokeAndWaitIfNeeded(@NotNull Runnable runnable) {
    if (isEventDispatchThread()) {
      runnable.run();
    }
    else {
      try {
        invokeAndWait(runnable);
      }
      catch (Exception e) {
        Logger.getInstance("#com.intellij.util.ui.EdtInvocationManager").error(e);
      }
    }
  }

  public static void executeWithCustomManager(@NotNull EdtInvocationManager manager, @NotNull Runnable runnable) {
    EdtInvocationManager old = null;
    try {
      old = ourInstance.getAndSet(manager);
      runnable.run();
    }
    finally {
      ourInstance.compareAndSet(manager, old);
    }
  }

  /**
   * The default {@link EdtInvocationManager} implementation which works with the EDT via SwingUtilities.
   */
  public static class SwingEdtInvocationManager extends EdtInvocationManager {
    @Override
    public boolean isEventDispatchThread() {
      return EventQueue.isDispatchThread();
    }

    @Override
    public void invokeLater(@NotNull Runnable task) {
      //noinspection SSBasedInspection
      EventQueue.invokeLater(task);
    }

    @Override
    public void invokeAndWait(@NotNull Runnable task) throws InvocationTargetException, InterruptedException {
      EventQueue.invokeAndWait(task);
    }
  }
}
