// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.util.Consumer;
import com.intellij.util.EmptyConsumer;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public final class CompoundRuntimeException extends RuntimeException {
  private final List<? extends Throwable> myExceptions;

  public CompoundRuntimeException(@NotNull List<? extends Throwable> throwables) {
    myExceptions = throwables;
  }

  @Override
  public synchronized Throwable getCause() {
    return ContainerUtil.getFirstItem(myExceptions);
  }

  public List<Throwable> getExceptions() {
    return new ArrayList<>(myExceptions);
  }

  @Override
  public String getMessage() {
    return processAll(Throwable::getMessage, EmptyConsumer.getInstance());
  }

  @Override
  public String getLocalizedMessage() {
    return processAll(Throwable::getLocalizedMessage, EmptyConsumer.getInstance());
  }

  @Override
  public String toString() {
    return processAll(Throwable::toString, EmptyConsumer.getInstance());
  }

  @Override
  public void printStackTrace(final PrintStream s) {
    processAll(throwable -> {
      throwable.printStackTrace(s);
      return "";
    }, s::print);
  }

  @Override
  public void printStackTrace(final PrintWriter s) {
    processAll(throwable -> {
      throwable.printStackTrace(s);
      return "";
    }, s::print);
  }

  private String processAll(@NotNull Function<? super Throwable, String> exceptionProcessor, @NotNull Consumer<? super String> stringProcessor) {
    if (myExceptions.size() == 1) {
      Throwable throwable = myExceptions.get(0);
      String s = exceptionProcessor.fun(throwable);
      stringProcessor.consume(s);
      return s;
    }

    StringBuilder sb = new StringBuilder();
    String line = "CompositeException (" + myExceptions.size() + " nested):\n------------------------------\n";
    stringProcessor.consume(line);
    sb.append(line);

    for (int i = 0; i < myExceptions.size(); i++) {
      Throwable exception = myExceptions.get(i);

      line = "[" + i + "]: ";
      stringProcessor.consume(line);
      sb.append(line);

      line = exceptionProcessor.fun(exception);
      if (line == null) {
        line = "null\n";
      }
      else if (!line.endsWith("\n")) line += '\n';
      stringProcessor.consume(line);
      sb.append(line);
    }

    line = "------------------------------\n";
    stringProcessor.consume(line);
    sb.append(line);

    return sb.toString();
  }

  public static void throwIfNotEmpty(@Nullable List<? extends Throwable> throwables) {
    if (ContainerUtil.isEmpty(throwables)) {
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
