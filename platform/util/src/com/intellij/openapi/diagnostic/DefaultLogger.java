// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ExceptionUtil;
import org.apache.log4j.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DefaultLogger extends Logger {
  private static boolean ourMirrorToStderr = true;

  @SuppressWarnings("UnusedParameters")
  public DefaultLogger(String category) { }

  @Override
  public boolean isDebugEnabled() {
    return false;
  }

  @Override
  public void debug(String message) { }

  @Override
  public void debug(Throwable t) { }

  @Override
  public void debug(String message, Throwable t) { }

  @Override
  public void info(String message) { }

  @Override
  public void info(String message, Throwable t) { }

  @Override
  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public void warn(String message, @Nullable Throwable t) {
    t = ensureNotControlFlow(t);
    System.err.println("WARN: " + message);
    if (t != null) t.printStackTrace(System.err);
  }

  @Override
  public void error(String message, @Nullable Throwable t, String @NotNull ... details) {
    t = ensureNotControlFlow(t);
    message += attachmentsToString(t);
    dumpExceptionsToStderr(message, t, details);

    throw new AssertionError(message, t);
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public static void dumpExceptionsToStderr(String message, @Nullable Throwable t, String @NotNull ... details) {
    if (shouldDumpExceptionToStderr()) {
      System.err.println("ERROR: " + message + detailsToString(details));
      if (t != null) t.printStackTrace(System.err);
    }
  }

  @Override
  public void setLevel(@NotNull Level level) { }

  public static @NotNull String detailsToString(String @NotNull ... details) {
    return details.length > 0 ? "\nDetails:\n" + String.join("\n", details) : "";
  }

  public static @NotNull String attachmentsToString(@Nullable Throwable t) {
    if (t != null) {
      String prefix = "\n\nAttachments:\n";
      String attachments = ExceptionUtil.findCauseAndSuppressed(t, ExceptionWithAttachments.class).stream()
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
    return ourMirrorToStderr;
  }

  public static void disableStderrDumping(@NotNull Disposable parentDisposable) {
    boolean prev = ourMirrorToStderr;
    ourMirrorToStderr = false;
    Disposer.register(parentDisposable, () -> {
      ourMirrorToStderr = prev;
    });
  }
}
