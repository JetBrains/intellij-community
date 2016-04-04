/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.util;

import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.openapi.util.text.StringUtil;
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
 * Traces creation and disposal by storing corresponding stacktraces.
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

  private class ObjectNotDisposedException extends AbstractDisposalException {

    ObjectNotDisposedException(@Nullable @NonNls final String msg) {
      super(msg);
    }


    @SuppressWarnings("HardCodedStringLiteral")
    @Override
    public void printStackTrace(PrintWriter s) {
      final List<StackTraceElement> stack = new ArrayList<StackTraceElement>(Arrays.asList(CREATE_TRACE.getStackTrace()));
      stack.remove(0); // this line is useless it stack
     s.write(ObjectNotDisposedException.class.getCanonicalName() + ": See stack trace responsible for creation of unreleased object below \n\tat " + StringUtil.join(stack, "\n\tat "));
    }
  }

  /**
   * in case of "object not disposed" use {@link #throwObjectNotDisposedError(String)} instead
   */
  public void throwDisposalError(@NonNls String msg) throws RuntimeException {
    throw new DisposalException(msg);
  }

  private abstract class AbstractDisposalException extends RuntimeException {
    protected AbstractDisposalException(String message) {
      super(message);
    }

    @Override
    public void printStackTrace(@NotNull PrintStream s) {
      //noinspection IOResourceOpenedButNotSafelyClosed
      PrintWriter writer = new PrintWriter(s);
      printStackTrace(writer);
      writer.flush();
    }
  }

  private class DisposalException extends AbstractDisposalException {
    private DisposalException(String message) {
      super(message);
    }

    @SuppressWarnings("HardCodedStringLiteral")
    @Override
    public void printStackTrace(PrintWriter s) {
      if (CREATE_TRACE != null) {
        s.println("--------------Creation trace: ");
        CREATE_TRACE.printStackTrace(s);
      }
      if (KILL_TRACE != null) {
        s.println("--------------Kill trace: ");
        KILL_TRACE.printStackTrace(s);
      }
      s.println("-------------Own trace:");
      super.printStackTrace(s);
    }
  }

  @NotNull
  public String getStackTrace() {
    StringWriter out = new StringWriter();
    new DisposalException("").printStackTrace(new PrintWriter(out));
    return out.toString();
  }
}
