// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public final class CompoundRuntimeException extends RuntimeException {
  private final List<? extends Throwable> myExceptions;

  public CompoundRuntimeException(@NotNull List<? extends Throwable> throwables) {
    myExceptions = throwables;
  }

  @Override
  public synchronized Throwable getCause() {
    return myExceptions.isEmpty() ? null : myExceptions.get(0);
  }

  public List<Throwable> getExceptions() {
    return new ArrayList<>(myExceptions);
  }

  @Override
  public String getMessage() {
    return processAll(Throwable::getMessage, null).toString();
  }

  @Override
  public String getLocalizedMessage() {
    return processAll(Throwable::getLocalizedMessage, null).toString();
  }

  @Override
  public String toString() {
    return processAll(Throwable::toString, null).toString();
  }

  @Override
  public void printStackTrace(@NotNull PrintStream s) {
    processAll(throwable -> {
      throwable.printStackTrace(s);
      return "";
    }, s::print);
  }

  @Override
  public void printStackTrace(@NotNull PrintWriter s) {
    processAll(throwable -> {
      throwable.printStackTrace(s);
      return "";
    }, s::print);
  }

  private @NotNull CharSequence processAll(@NotNull Function<Throwable, String> exceptionProcessor, @Nullable Consumer<String> stringProcessor) {
    if (myExceptions.size() == 1) {
      Throwable throwable = myExceptions.get(0);
      String s = exceptionProcessor.apply(throwable);
      if (stringProcessor != null) {
        stringProcessor.accept(s);
      }
      return s;
    }

    StringBuilder sb = new StringBuilder();
    String line = "CompositeException (" + myExceptions.size() + " nested):\n------------------------------\n";
    if (stringProcessor != null) {
      stringProcessor.accept(line);
    }
    sb.append(line);

    for (int i = 0; i < myExceptions.size(); i++) {
      Throwable exception = myExceptions.get(i);

      line = "[" + i + "]: ";
      if (stringProcessor != null) {
        stringProcessor.accept(line);
      }
      sb.append(line);

      line = exceptionProcessor.apply(exception);
      if (line == null) {
        line = "null\n";
      }
      else if (!line.endsWith("\n")) {
        line += '\n';
      }
      if (stringProcessor != null) {
        stringProcessor.accept(line);
      }
      sb.append(line);
    }

    line = "------------------------------\n";
    if (stringProcessor != null) {
      stringProcessor.accept(line);
    }
    sb.append(line);

    return sb;
  }

  public static void throwIfNotEmpty(@Nullable List<? extends Throwable> throwables) {
    if (throwables == null || throwables.isEmpty()) {
      return;
    }

    if (throwables.size() == 1) {
      ExceptionUtil.rethrow(throwables.get(0));
    }
    else {
      throw new CompoundRuntimeException(throwables);
    }
  }
}
