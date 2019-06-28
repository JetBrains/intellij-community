// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;

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

  @NotNull private static EdtInvocationManager ourInstance = new SwingEdtInvocationManager();

  public abstract boolean isEventDispatchThread();

  public abstract void invokeLater(@NotNull Runnable task);

  public abstract void invokeAndWait(@NotNull Runnable task) throws InvocationTargetException, InterruptedException;

  @NotNull
  public static EdtInvocationManager getInstance() {
    return ourInstance;
  }

  @SuppressWarnings("unused") // Used in upsource
  public static void setEdtInvocationManager(@NotNull EdtInvocationManager edtInvocationManager) {
    ourInstance = edtInvocationManager;
  }

  /**
   * The default {@link EdtInvocationManager} implementation which works with the EDT via SwingUtilities.
   */
  private static class SwingEdtInvocationManager extends EdtInvocationManager {
    @Override
    public boolean isEventDispatchThread() {
      return SwingUtilities.isEventDispatchThread();
    }

    @Override
    public void invokeLater(@NotNull Runnable task) {
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(task);
    }

    @Override
    public void invokeAndWait(@NotNull Runnable task) throws InvocationTargetException, InterruptedException {
      SwingUtilities.invokeAndWait(task);
    }
  }
}
