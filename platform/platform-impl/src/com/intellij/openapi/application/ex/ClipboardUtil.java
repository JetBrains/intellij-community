// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.ex;

import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.DiskQueryRelay;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class ClipboardUtil {
  private static final Logger LOG = Logger.getInstance(ClipboardUtil.class);
  /**
   * We cannot guarantee that clipboard respond in reasonable time IDEA-326362.
   */
  private static final int WAIT_TIMEOUT_ON_MAIN_THREAD_MS = SystemProperties.getIntProperty("idea.clipboard.wait.timeout", 1000);

  public static <E> E handleClipboardSafely(@NotNull Supplier<? extends E> supplier, E defaultValue) {
    ThrowableComputable<E, RuntimeException> task = () -> {
      try {
        return supplier.get();
      }
      catch (IllegalStateException e) {
        if (SystemInfo.isWindows) {
          LOG.debug("Clipboard is busy");
        }
        else {
          LOG.warn(e);
        }
      }
      catch (NullPointerException e) {
        LOG.warn("Java bug #6322854", e);
      }
      catch (IllegalArgumentException e) {
        LOG.warn("Java bug #7173464", e);
      }
      return defaultValue;
    };
    return ApplicationManager.getApplication().isDispatchThread()
           ? (IdeEventQueue.getInstance().isDispatchingOnMainThread ? task.compute() : waitOnEdtWithTimeout(task, defaultValue))
           : DiskQueryRelay.compute(task);
  }

  private static <E> E waitOnEdtWithTimeout(@NotNull ThrowableComputable<E, RuntimeException> task, E defaultValue) {
    try {
      return ProcessIOExecutorService.INSTANCE.submit(task::compute).get(WAIT_TIMEOUT_ON_MAIN_THREAD_MS, TimeUnit.MILLISECONDS);
    }
    catch (ExecutionException e) {
      LOG.warn(e.getCause());
    }
    catch (Throwable ignored) {
    }
    return defaultValue;
  }

  public static @Nullable String getTextInClipboard() {
    return CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
  }
}