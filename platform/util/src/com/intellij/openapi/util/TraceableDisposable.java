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

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Traces creation and disposal by storing corresponding stacktraces.
 * In constructor it saves creation stacktrace
 * In kill() it saves disposal stacktrace
 */
public class TraceableDisposable {
  private final Throwable CREATE_TRACE;
  private Throwable KILL_TRACE;

  public TraceableDisposable(@Nullable("null means do not trace") Throwable creation) {
    CREATE_TRACE = creation;
  }

  public void kill(@NonNls @Nullable String msg) {
    if (CREATE_TRACE != null) {
      KILL_TRACE = new Throwable(msg);
    }
  }

  public void killExceptionally(@NotNull Throwable throwable) {
    if (CREATE_TRACE != null) {
      KILL_TRACE = throwable;
    }
  }

  public void throwDisposalError(@NonNls String msg) throws RuntimeException {
    throw new DisposalException(msg);
  }

  private class DisposalException extends RuntimeException {
    private DisposalException(String message) {
      super(message);
    }

    @Override
    public void printStackTrace(@NotNull PrintStream s) {
      //noinspection IOResourceOpenedButNotSafelyClosed
      PrintWriter writer = new PrintWriter(s);
      printStackTrace(writer);
      writer.flush();
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
