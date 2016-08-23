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
package com.intellij.vcs.log.data;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectIterator;
import org.jetbrains.annotations.NotNull;

import java.util.stream.IntStream;
import java.util.stream.Stream;

public class TroveUtil {
  @NotNull
  public static <T> Stream<T> streamValues(@NotNull TIntObjectHashMap<T> map) {
    TIntObjectIterator<T> it = map.iterator();
    return Stream.generate(() -> {
      it.advance();
      return it.value();
    }).limit(map.size());
  }

  @NotNull
  public static IntStream streamKeys(@NotNull TIntObjectHashMap<?> map) {
    TIntObjectIterator<?> it = map.iterator();
    return IntStream.generate(() -> {
      it.advance();
      return it.key();
    }).limit(map.size());
  }

  @NotNull
  public static IntStream stream(@NotNull TIntArrayList list) {
    if (list.isEmpty()) return IntStream.empty();
    return IntStream.range(0, list.size()).map(list::get);
  }
}
