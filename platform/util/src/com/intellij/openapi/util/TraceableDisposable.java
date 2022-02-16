// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.ExceptionWithAttachments;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
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

  @Contract("_->fail")
  public void throwDisposalError(@NotNull @NonNls String msg) throws RuntimeException {
    throw new DisposalException(msg);
  }

  private final class DisposalException extends RuntimeException implements ExceptionWithAttachments {
    private DisposalException(@NotNull String message) {
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
    @NonNls PrintWriter out = new PrintWriter(s);
    if (CREATE_TRACE != null) {
      out.println("--------------Creation trace: ");
      CREATE_TRACE.printStackTrace(out);
    }
    if (KILL_TRACE != null) {
      out.println("--------------Kill trace: ");
      KILL_TRACE.printStackTrace(out);
    }
    out.println("-------------Own trace:");
    new DisposalException(String.valueOf(System.identityHashCode(this))).printStackTrace(out);
    out.flush();
    return s.toString();
  }
}
