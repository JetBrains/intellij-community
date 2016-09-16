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

import com.intellij.util.Consumer;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

public interface Promise<T> {
  Promise<Void> DONE = new DonePromise<>(null);
  Promise<Void> REJECTED = PromiseKt.getREJECTED();

  enum State {
    PENDING, FULFILLED, REJECTED
  }

  @NotNull
  static <T> Promise<T> resolve(T result) {
    if (result == null) {
      //noinspection unchecked
      return (Promise<T>)DONE;
    }
    else {
      return new DonePromise<>(result);
    }
  }

  @NotNull
  Promise<T> done(@NotNull Consumer<? super T> done);

  @NotNull
  Promise<T> processed(@NotNull AsyncPromise<? super T> fulfilled);

  @NotNull
  Promise<T> rejected(@NotNull Consumer<Throwable> rejected);

  Promise<T> processed(@NotNull Consumer<? super T> processed);

  @NotNull
  <SUB_RESULT> Promise<SUB_RESULT> then(@NotNull Function<? super T, ? extends SUB_RESULT> done);

  @NotNull
  <SUB_RESULT> Promise<SUB_RESULT> thenAsync(@NotNull Function<? super T, Promise<SUB_RESULT>> done);

  @NotNull
  State getState();

  @Nullable
  T blockingGet(int timeout, @NotNull TimeUnit timeUnit);

  void notify(@NotNull AsyncPromise<? super T> child);
}