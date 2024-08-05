// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import com.intellij.concurrency.ThreadContext;
import com.intellij.openapi.application.AccessToken;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.Async;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

final class ContextCallable<V> implements Callable<V> {

  /**
   * Whether this callable is expected to be at the bottom of the stacktrace.
   */
  private final boolean myRoot;
  private final @NotNull ChildContext myChildContext;
  private final @NotNull Callable<? extends V> myCallable;

  static class RunResult<V, E extends Exception> {
    Object result;
    boolean isSuccess;

    RunResult(V result) {
      this.result = result;
      isSuccess = true;
    }

    RunResult(E error) {
      result = error;
      isSuccess = false;
    }

    @SuppressWarnings("unchecked")
    V get() throws E {
      if (isSuccess) {
        return (V)result;
      }
      else {
        throw (E)result;
      }
    }
  }

  @Async.Schedule
  ContextCallable(boolean root, @NotNull ChildContext context, @NotNull Callable<? extends V> callable) {
    myRoot = root;
    myChildContext = context;
    myCallable = callable;
  }

  @Async.Execute
  @Override
  public V call() throws Exception {
    RunResult<V, Exception> result;
    if (myRoot) {
      result = myChildContext.runInChildContext(true, () -> {
        try {
          return new RunResult<>(myCallable.call());
        }
        catch (Exception e) {
          return new RunResult<>(e);
        }
      });
    }
    else {
      Supplier<RunResult<V, Exception>> temp = () -> {
        try (AccessToken ignored = ThreadContext.installThreadContext(myChildContext.getContext(), true);
             AccessToken ignored2 = myChildContext.applyContextActions(false)) {
          try {
            return new RunResult<>(myCallable.call());
          }
          catch (Exception e) {
            return new RunResult<>(e);
          }
        }
      };
      Continuation<Unit> continuation = myChildContext.getContinuation();
      if (continuation == null) {
        result = temp.get();
      }
      else {
        result = Propagation.runAsCoroutine(continuation, true, temp::get);
      }
    }
    return result.get();
  }
}
