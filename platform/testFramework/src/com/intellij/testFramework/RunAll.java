// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.util.ErrorKt;
import com.intellij.util.SmartList;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.CompoundRuntimeException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Runs all given runnables and throws all the caught exceptions at the end.
 *
 * @author peter
 */
public final class RunAll implements Runnable {
  private final List<? extends ThrowableRunnable<?>> myActions;

  @SafeVarargs
  public RunAll(ThrowableRunnable<Throwable> @NotNull ... actions) {
    this(Arrays.asList(actions));
  }

  public RunAll(@NotNull List<? extends ThrowableRunnable<?>> actions) {
    myActions = actions;
  }

  @SafeVarargs
  public static void runAll(ThrowableRunnable<Throwable> @NotNull ... actions) {
    ErrorKt.throwIfNotEmpty(collectExceptions(Arrays.asList(actions)));
  }

  @SafeVarargs
  @Contract(pure=true)
  public final RunAll append(ThrowableRunnable<Throwable> @NotNull ... actions) {
    return new RunAll(ContainerUtil.concat(myActions, actions.length == 1 ? Collections.singletonList(actions[0]) : Arrays.asList(actions)));
  }

  @Override
  public void run() {
    run(Collections.emptyList());
  }

  public void run(@NotNull List<? extends Throwable> suppressedExceptions) {
    ErrorKt.throwIfNotEmpty(ContainerUtil.concat(suppressedExceptions, collectExceptions(myActions)));
  }

  private static @NotNull List<Throwable> collectExceptions(@NotNull List<? extends ThrowableRunnable<?>> actions) {
    List<Throwable> result = null;
    for (ThrowableRunnable<?> action : actions) {
      try {
        action.run();
      }
      catch (CompoundRuntimeException e) {
        if (result == null) {
          result = new ArrayList<>();
        }
        result.addAll(e.getExceptions());
      }
      catch (Throwable e) {
        if (result == null) {
          result = new SmartList<>();
        }
        result.add(e);
      }
    }
    return ContainerUtil.notNullize(result);
  }
}
