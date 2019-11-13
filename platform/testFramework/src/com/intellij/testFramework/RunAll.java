// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.util.SmartList;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.ThrowablePairConsumer;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.CompoundRuntimeException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Runs all given runnables and throws all the caught exceptions at the end.
 *
 * @author peter
 */
public class RunAll implements Runnable {
  private final List<? extends ThrowableRunnable<?>> myActions;

  @SafeVarargs
  public RunAll(@NotNull ThrowableRunnable<Throwable>... actions) {
    this(ContainerUtil.newArrayList(actions));
  }

  private RunAll(@NotNull List<? extends ThrowableRunnable<?>> actions) {
    myActions = actions;
  }

  @SafeVarargs
  @Contract(pure = true)
  public final RunAll append(@NotNull ThrowableRunnable<Throwable>... actions) {
    return new RunAll(ContainerUtil.concat(
      myActions,
      actions.length == 1 ? Collections.singletonList(actions[0]) : ContainerUtil.newArrayList(actions)
    ));
  }

  @Override
  public void run() {
    run(Collections.emptyList());
  }

  public void run(@NotNull List<? extends Throwable> suppressedExceptions) {
    List<Throwable> throwables = collectExceptions();
    throwables.addAll(0, suppressedExceptions);
    CompoundRuntimeException.throwIfNotEmpty(throwables);
  }

  @NotNull
  private List<Throwable> collectExceptions() {
    List<Throwable> errors = new SmartList<>();
    for (ThrowableRunnable<?> action : myActions) {
      try {
        action.run();
      }
      catch (CompoundRuntimeException e) {
        errors.addAll(e.getExceptions());
      }
      catch (Throwable e) {
        errors.add(e);
      }
    }
    return errors;
  }

  @NotNull
  public static <T, E extends Throwable> RunAll runAll(@NotNull Collection<? extends T> input,
                                                       @NotNull ThrowableConsumer<? super T, ? extends E> action) {
    return new RunAll(ContainerUtil.map(input, it -> (ThrowableRunnable<E>)() -> action.consume(it)));
  }

  @NotNull
  public static <K, V, E extends Throwable> RunAll runAll(@NotNull Map<? extends K, ? extends V> input,
                                                          @NotNull ThrowablePairConsumer<? super K, ? super V, ? extends E> action) {
    return runAll(input.entrySet(), e -> action.consume(e.getKey(), e.getValue()));
  }
}
