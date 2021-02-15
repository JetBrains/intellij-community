/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Please use {@link java.util.function.Function} instead
 *
 * @author max
 * @see Functions for some common implementations
 */
public interface Function<Param, Result> {
  Result fun(Param param);

  interface Mono<T> extends Function<T, T> {}

  final class InstanceOf<P, R extends P> implements NullableFunction<P, R> {

    private final Class<R> myResultClass;

    public InstanceOf(@NotNull Class<R> resultClass) {
      myResultClass = resultClass;
    }

    @Nullable
    public R fun(P p) {
      return myResultClass.isInstance(p) ? myResultClass.cast(p) : null;
    }
  }

  final class First<P> implements Function<P[], P> {
    public P fun(P[] ps) {
      return ps[0];
    }
  }

  final class FirstInCollection<P> implements Function<Collection<P>, P> {
    public P fun(Collection<P> ps) {
      return ps.iterator().next();
    }
  }
}
