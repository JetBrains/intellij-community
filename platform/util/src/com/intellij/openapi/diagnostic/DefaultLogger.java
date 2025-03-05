// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DefaultLogger extends Logger {
  private static boolean mirrorToStderr = true;

  private final String category;
  private LogLevel level = LogLevel.WARNING;

  public DefaultLogger(String category) {
    this.category = category;
  }

  @Override
  public boolean isDebugEnabled() {
    return level.compareTo(LogLevel.DEBUG) >= 0;
  }

  @Override
  public boolean isTraceEnabled() {
    return level.compareTo(LogLevel.TRACE) >= 0;
  }

  @Override
  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public void trace(String message) {
    if (isTraceEnabled()) {
      System.out.println("TRACE[" + category + "]: " + message);
    }
  }

  @Override
  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public void trace(@Nullable Throwable t) {
    if (t != null && isTraceEnabled()) {
      System.out.print("TRACE[" + category + "]: ");
      t.printStackTrace(System.out);
    }
  }

  @Override
  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public void debug(String message, @Nullable Throwable t) {
    if (isDebugEnabled()) {
      System.out.println("DEBUG[" + category + "]: " + message);
      if (t != null) t.printStackTrace(System.out);
    }
  }

  @Override
  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public void info(String message, Throwable t) {
    if (level.compareTo(LogLevel.INFO) >= 0) {
      System.out.println("INFO[" + category + "]: " + message);
      if (t != null) {
        t.printStackTrace(System.out);
      }
    }
  }

  @Override
  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public void warn(String message, @Nullable Throwable t) {
    t = ensureNotControlFlow(t);
    System.err.println("WARN: " + message);
    if (t != null) {
      t.printStackTrace(System.err);
    }
  }

  @Override
  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public void error(String message, @Nullable Throwable t, String @NotNull ... details) {
    t = ensureNotControlFlow(t);
    if (shouldDumpExceptionToStderr()) {
      System.err.println("ERROR: " + message + detailsToString(details) + attachmentsToString(t));
      if (t != null) {
        t.printStackTrace(System.err);
      }
    }
    throw new AssertionError(message, t);
  }

  @Override
  public void setLevel(@NotNull LogLevel level) {
    this.level = level;
  }

  public static @NotNull String detailsToString(String @NotNull ... details) {
    return details.length > 0 ? "\nDetails:\n" + String.join("\n", details) : "";
  }

  public static @NotNull String attachmentsToString(@Nullable Throwable t) {
    if (t != null) {
      String prefix = "\n\nAttachments:\n";
      String attachments = ExceptionUtil.causeAndSuppressed(t, ExceptionWithAttachments.class)
        .flatMap(e -> Stream.of(e.getAttachments()))
        .map(ATTACHMENT_TO_STRING)
        .collect(Collectors.joining("\n----\n", prefix, ""));
      if (!attachments.equals(prefix)) {
        return attachments;
      }
    }
    return "";
  }

  public static boolean shouldDumpExceptionToStderr() {
    return mirrorToStderr;
  }

  public static void disableStderrDumping(@NotNull Disposable parentDisposable) {
    boolean prev = mirrorToStderr;
    mirrorToStderr = false;
    Disposer.register(parentDisposable, () -> {
      mirrorToStderr = prev;
    });
  }
}
