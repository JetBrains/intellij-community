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
package com.intellij.vcs.log.util;

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
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

  @NotNull
  public static Set<Integer> intersect(@NotNull TIntHashSet... sets) {
    TIntHashSet result = null;

    Arrays.sort(sets, (set1, set2) -> {
      if (set1 == null) return -1;
      if (set2 == null) return 1;
      return set1.size() - set2.size();
    });
    for (TIntHashSet set : sets) {
      result = intersect(result, set);
    }

    if (result == null) return ContainerUtil.newHashSet();
    return createJavaSet(result);
  }

  public static boolean intersects(@NotNull TIntHashSet set1, @NotNull TIntHashSet set2) {
    if (set1.size() <= set2.size()) {
      return !set1.forEach(value -> {
        if (set2.contains(value)) {
          return false;
        }
        return true;
      });
    }
    return intersects(set2, set1);
  }

  @Nullable
  private static TIntHashSet intersect(@Nullable TIntHashSet set1, @Nullable TIntHashSet set2) {
    if (set1 == null) return set2;
    if (set2 == null) return set1;

    TIntHashSet result = new TIntHashSet();

    if (set1.size() < set2.size()) {
      intersectTo(set1, set2, result);
    }
    else {
      intersectTo(set2, set1, result);
    }

    return result;
  }

  private static void intersectTo(@NotNull TIntHashSet small, @NotNull TIntHashSet big, @NotNull TIntHashSet result) {
    small.forEach(value -> {
      if (big.contains(value)) {
        result.add(value);
      }
      return true;
    });
  }

  @NotNull
  private static Set<Integer> createJavaSet(@NotNull TIntHashSet set) {
    Set<Integer> result = ContainerUtil.newHashSet(set.size());
    set.forEach(value -> {
      result.add(value);
      return true;
    });
    return result;
  }

  public static void addAll(@NotNull TIntHashSet where, @NotNull TIntHashSet what) {
    what.forEach(value -> {
      where.add(value);
      return true;
    });
  }

  public static <V> void putAll(@NotNull TIntObjectHashMap<V> where, @NotNull TIntObjectHashMap<V> what) {
    what.forEachEntry((index, value) -> {
      where.put(index, value);
      return true;
    });
  }

  @NotNull
  public static IntStream stream(@NotNull TIntHashSet set) {
    TIntIterator it = set.iterator();
    return IntStream.generate(it::next).limit(set.size());
  }

  @NotNull
  public static <T> List<T> map(@NotNull TIntHashSet set, @NotNull IntFunction<T> function) {
    return stream(set).mapToObj(function).collect(Collectors.toList());
  }

  @NotNull
  public static <T> TIntObjectHashMap<T> map2MapNotNull(@NotNull TIntHashSet set, @NotNull IntFunction<T> function) {
    TIntObjectHashMap<T> result = new TIntObjectHashMap<>();
    set.forEach(it -> {
      T value = function.apply(it);
      if (value != null) {
        result.put(it, value);
      }
      return true;
    });
    return result;
  }

  @NotNull
  public static <T> TIntHashSet map2IntSet(@NotNull Collection<T> set, @NotNull ToIntFunction<T> function) {
    TIntHashSet result = new TIntHashSet();
    for (T t : set) {
      result.add(function.applyAsInt(t));
    }
    return result;
  }

  public static void processBatches(@NotNull IntStream stream,
                                    int batchSize,
                                    @NotNull ThrowableConsumer<TIntHashSet, VcsException> consumer)
    throws VcsException {
    Ref<TIntHashSet> batch = new Ref<>(new TIntHashSet());
    Ref<VcsException> exception = new Ref<>();
    stream.forEach(commit -> {
      batch.get().add(commit);
      if (batch.get().size() >= batchSize) {
        try {
          consumer.consume(batch.get());
        }
        catch (VcsException e) {
          exception.set(e);
        }
        finally {
          batch.set(new TIntHashSet());
        }
      }
    });

    if (!batch.get().isEmpty()) {
      consumer.consume(batch.get());
    }

    if (!exception.isNull()) throw exception.get();
  }

  @NotNull
  public static TIntHashSet collect(@NotNull IntStream stream) {
    TIntHashSet result = new TIntHashSet();
    stream.forEach(result::add);
    return result;
  }

  @NotNull
  public static TIntHashSet singleton(@NotNull Integer elements) {
    TIntHashSet commits = new TIntHashSet();
    commits.add(elements);
    return commits;
  }

  public static <T> void add(@NotNull Map<T, TIntHashSet> targetMap, @NotNull T key, int value) {
    TIntHashSet set = targetMap.get(key);
    if (set == null) {
      set = new TIntHashSet();
      targetMap.put(key, set);
    }
    set.add(value);
  }
}
