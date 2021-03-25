// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.CompoundRuntimeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Runs all given runnables and throws all the caught exceptions at the end.
 *
 * @author peter
 */
public final class RunAll implements Runnable {

  @SafeVarargs
  public static void runAll(ThrowableRunnable<Throwable> @NotNull ... actions) {
    new RunAll(actions).run();
  }

  public static <T> void runAll(@NotNull Collection<? extends T> input,
                                @NotNull ThrowableConsumer<? super T, Throwable> action) {
    new RunAll(ContainerUtil.map(input, it -> () -> action.consume(it))).run();
  }

  public static <K, V> void runAll(@NotNull Map<? extends K, ? extends V> input,
                                   @NotNull ThrowablePairConsumer<? super K, ? super V, Throwable> action) {
    runAll(input.entrySet(), e -> action.consume(e.getKey(), e.getValue()));
  }

  private final List<? extends ThrowableRunnable<?>> myActions;

  @SafeVarargs
  public RunAll(ThrowableRunnable<Throwable> @NotNull ... actions) {
    this(Arrays.asList(actions));
  }

  public RunAll(@NotNull List<? extends ThrowableRunnable<?>> actions) {
    myActions = actions;
  }

  @Override
  public void run() {
    run(null);
  }

  public void run(@Nullable List<Throwable> earlierExceptions) {
    List<Throwable> exceptions = earlierExceptions != null ? new SmartList<>(earlierExceptions) : new SmartList<>();

    for (ThrowableRunnable<?> action : myActions) {
      try {
        action.run();
      }
      catch (CompoundRuntimeException e) {
        exceptions.addAll(e.getExceptions());
      }
      catch (Throwable e) {
        exceptions.add(e);
      }
    }

    if (exceptions.size() == 1) {
      ExceptionUtil.rethrow(exceptions.get(0));
    }
    else if (exceptions.size() > 0) {
      throw new CompoundRuntimeException(exceptions);
    }
  }
}
