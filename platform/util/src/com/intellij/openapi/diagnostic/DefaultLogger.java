// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diagnostic;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ExceptionUtil;
import org.apache.log4j.Level;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  public void debug(@NonNls String message, Throwable t) { }

  @Override
  public void info(String message) { }

  @Override
  public void info(String message, Throwable t) { }

  @Override
  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public void warn(@NonNls String message, @Nullable Throwable t) {
    t = checkException(t);
    System.err.println("WARN: " + message);
    if (t != null) t.printStackTrace(System.err);
  }

  @Override
  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public void error(String message, @Nullable Throwable t, @NotNull String... details) {
    t = checkException(t);
    message += attachmentsToString(t);
    if (shouldDumpExceptionToStderr()) {
      System.err.println("ERROR: " + message);
      if (t != null) t.printStackTrace(System.err);
      if (details.length > 0) {
        System.err.println("details: ");
        for (String detail : details) {
          System.err.println(detail);
        }
      }
    }

    AssertionError error = new AssertionError(message);
    error.initCause(t);
    throw error;
  }

  @Override
  public void setLevel(Level level) { }

  public static String attachmentsToString(@Nullable Throwable t) {
    //noinspection ThrowableResultOfMethodCallIgnored
    Throwable rootCause = t == null ? null : ExceptionUtil.getRootCause(t);
    if (rootCause instanceof ExceptionWithAttachments) {
      return "\n\nAttachments:\n" + StringUtil.join(((ExceptionWithAttachments)rootCause).getAttachments(), ATTACHMENT_TO_STRING, "\n----\n");
    }
    return "";
  }

  public static boolean shouldDumpExceptionToStderr() {
    return ourMirrorToStderr;
  }

  public static void disableStderrDumping(@NotNull Disposable parentDisposable) {
    final boolean prev = ourMirrorToStderr;
    ourMirrorToStderr = false;
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        ourMirrorToStderr = prev;
      }
    });
  }

}
