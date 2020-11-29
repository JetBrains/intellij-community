// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diagnostic;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ExceptionUtil;
import org.apache.log4j.Level;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
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
  public void debug(@NonNls String message, Throwable t) { }

  @Override
  public void info(String message) { }

  @Override
  public void info(String message, Throwable t) { }

  @Override
  @SuppressWarnings({"UseOfSystemOutOrSystemErr", "HardCodedStringLiteral"})
  public void warn(@NonNls String message, @Nullable Throwable t) {
    t = checkException(t);
    System.err.println("WARN: " + message);
    if (t != null) t.printStackTrace(System.err);
  }

  @Override
  public void error(String message, @Nullable Throwable t, String @NotNull ... details) {
    t = checkException(t);
    message += attachmentsToString(t);
    dumpExceptionsToStderr(message, t, details);

    throw new AssertionError(message, t);
  }

  @SuppressWarnings({"UseOfSystemOutOrSystemErr", "HardCodedStringLiteral"})
  public static void dumpExceptionsToStderr(String message,
                                            @Nullable Throwable t,
                                            String @NotNull ... details) {
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
  }

  @Override
  public void setLevel(@NotNull Level level) { }

  public static @NonNls String attachmentsToString(@Nullable Throwable t) {
    if (t != null) {
      List<Attachment> attachments = ExceptionUtil
        .findCauseAndSuppressed(t, ExceptionWithAttachments.class)
        .stream()
        .flatMap(e -> Stream.of(e.getAttachments()))
        .collect(Collectors.toList());
      if (!attachments.isEmpty()) {
        return "\n\nAttachments:\n" + StringUtil.join(attachments, ATTACHMENT_TO_STRING::apply, "\n----\n");
      }
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
