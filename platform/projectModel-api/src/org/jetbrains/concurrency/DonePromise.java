/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.concurrency;

import com.intellij.openapi.util.Getter;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

import static org.jetbrains.concurrency.Promises.rejectedPromise;
import static org.jetbrains.concurrency.Promises.resolvedPromise;

class DonePromise<T> implements Getter<T>, Promise<T> {
  private final T result;

  public DonePromise(T result) {
    this.result = result;
  }

  @NotNull
  @Override
  public Promise<T> done(@NotNull Consumer<? super T> done) {
    if (!AsyncPromiseKt.isObsolete(done)) {
      done.consume(result);
    }
    return this;
  }

  @NotNull
  @Override
  public Promise<T> processed(@NotNull AsyncPromise<? super T> fulfilled) {
    fulfilled.setResult(result);
    return this;
  }

  @NotNull
  @Override
  public Promise<T> processed(@NotNull Consumer<? super T> processed) {
    done(processed);
    return this;
  }

  @NotNull
  @Override
  public Promise<T> rejected(@NotNull Consumer<Throwable> rejected) {
    return this;
  }

  @NotNull
  @Override
  public <SUB_RESULT> Promise<SUB_RESULT> then(@NotNull Function<? super T, ? extends SUB_RESULT> done) {
    if (done instanceof Obsolescent && ((Obsolescent)done).isObsolete()) {
      return rejectedPromise("obsolete");
    }
    else {
      return resolvedPromise(done.fun(result));
    }
  }

  @NotNull
  @Override
  public <SUB_RESULT> Promise<SUB_RESULT> thenAsync(@NotNull Function<? super T, Promise<SUB_RESULT>> done) {
    return done.fun(result);
  }

  @NotNull
  @Override
  public State getState() {
    return State.FULFILLED;
  }

  @Nullable
  @Override
  public T blockingGet(int timeout, @NotNull TimeUnit timeUnit) {
    return result;
  }

  @Override
  public T get() {
    return result;
  }

  @Override
  public void notify(@NotNull AsyncPromise<? super T> child) {
    child.setResult(result);
  }
}