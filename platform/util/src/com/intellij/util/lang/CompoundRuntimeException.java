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
package com.intellij.util.lang;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.List;

/**
 * @author mike
 */
public class CompoundRuntimeException extends RuntimeException {
  private final List<Throwable> myThrowables;

  public CompoundRuntimeException(@NotNull List<Throwable> throwables) {
    //noinspection HardCodedStringLiteral
    super("Several Exceptions occurred", throwables.get(0));

    myThrowables = throwables;
  }

  @Override
  public void printStackTrace(PrintStream s) {
    for (Throwable throwable : myThrowables) {
      throwable.printStackTrace(s);
    }
  }


  @Override
  public void printStackTrace(PrintWriter s) {
    for (Throwable throwable : myThrowables) {
      throwable.printStackTrace(s);
    }
  }

  public static void doThrow(@Nullable List<Throwable> throwables) {
    if (ContainerUtil.isEmpty(throwables)) {
      return;
    }

    if (throwables.size() == 1) {
      Throwable throwable = throwables.get(0);
      if (throwable instanceof Error) {
        throw (Error)throwable;
      }
      else if (throwable instanceof RuntimeException) {
        throw (RuntimeException)throwable;
      }
      else {
        throw new RuntimeException(throwable);
      }
    }
    else {
      throw new CompoundRuntimeException(throwables);
    }
  }
}
