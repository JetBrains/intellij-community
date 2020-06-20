// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.ExceptionWithAttachments;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Traces creation and disposal by storing corresponding stack traces.
 * In constructor it saves creation stacktrace
 * In kill() it saves disposal stacktrace
 */
public class TraceableDisposable {
  private final Throwable CREATE_TRACE;
  private Throwable KILL_TRACE;

  public TraceableDisposable(boolean debug) {
    CREATE_TRACE = debug ? ThrowableInterner.intern(new Throwable()) : null;
  }

  public void kill(@NonNls @Nullable String msg) {
    if (CREATE_TRACE != null) {
      KILL_TRACE = ThrowableInterner.intern(new Throwable(msg));
    }
  }

  public void killExceptionally(@NotNull Throwable throwable) {
    if (CREATE_TRACE != null) {
      KILL_TRACE = throwable;
    }
  }

  /**
   * Call when object is not disposed while it should
   */
  public void throwObjectNotDisposedError(@NonNls @NotNull final String msg) {
    throw new ObjectNotDisposedException(msg);
  }

  private final class ObjectNotDisposedException extends RuntimeException implements ExceptionWithAttachments {

    ObjectNotDisposedException(@Nullable @NonNls final String msg) {
      super(msg);
      KILL_TRACE = ThrowableInterner.intern(new Throwable(msg));
    }

    @Override
    public Attachment @NotNull [] getAttachments() {
      return new Attachment[]{new Attachment("kill", KILL_TRACE)};
    }

    @Override
    public void printStackTrace(@NotNull PrintStream s) {
      PrintWriter writer = new PrintWriter(s);
      printStackTrace(writer);
      writer.flush();
    }

    @SuppressWarnings("HardCodedStringLiteral")
    @Override
    public void printStackTrace(PrintWriter s) {
      final List<StackTraceElement> stack = new ArrayList<>(Arrays.asList(CREATE_TRACE.getStackTrace()));
      stack.remove(0); // this line is useless it stack
      s.write(ObjectNotDisposedException.class.getCanonicalName() +
              ": See stack trace responsible for creation of unreleased object below \n\tat " +
              StringUtil.join(stack, "\n\tat "));
    }
  }

  /**
   * in case of "object not disposed" use {@link #throwObjectNotDisposedError(String)} instead
   */
  public void throwDisposalError(@NonNls String msg) throws RuntimeException {
    throw new DisposalException(msg);
  }

  private final class DisposalException extends RuntimeException implements ExceptionWithAttachments {
    private DisposalException(String message) {
      super(message);
    }

    @Override
    public Attachment @NotNull [] getAttachments() {
      List<Attachment> answer = new SmartList<>();
      if (CREATE_TRACE != null) {
        answer.add(new Attachment("creation", CREATE_TRACE));
      }
      if (KILL_TRACE != null) {
        answer.add(new Attachment("kill", KILL_TRACE));
      }
      return answer.toArray(Attachment.EMPTY_ARRAY);
    }
  }

  @NotNull
  public String getStackTrace() {
    StringWriter s = new StringWriter();
    PrintWriter out = new PrintWriter(s);
    if (CREATE_TRACE != null) {
      out.println("--------------Creation trace: ");
      CREATE_TRACE.printStackTrace(out);
    }
    if (KILL_TRACE != null) {
      out.println("--------------Kill trace: ");
      KILL_TRACE.printStackTrace(out);
    }
    out.println("-------------Own trace:");
    new DisposalException("" + System.identityHashCode(this)).printStackTrace(out);
    out.flush();
    return s.toString();
  }
}
