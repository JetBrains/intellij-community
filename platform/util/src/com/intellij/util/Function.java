/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import java.util.Collection;

/**
 * @author max
 * @author Konstantin Bulenkov
 */
@SuppressWarnings({"unchecked"})
public interface Function<Param, Result> {
  Result fun(Param param);

  Function ID = new Function() {
    public Object fun(final Object o) {
      return o;
    }
  };
  Function NULL = NullableFunction.NULL;

  final class Self<P, R> implements Function<P, R> {
    @Override
    public R fun(P p) {
      return (R)p;
    }
  }

  final class First<P, R extends P> implements Function<P[], R> {
    @Override
    public R fun(P[] ps) {
      return (R)ps[0];
    }
  }

  final class FirstInCollection<P, R extends P> implements Function<Collection<P>, R> {
    @Override
    public R fun(Collection<P> ps) {
      return (R)ps.iterator().next();
    }
  }
}
