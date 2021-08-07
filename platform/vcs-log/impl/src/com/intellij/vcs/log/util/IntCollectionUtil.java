// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.util;

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.ThrowableConsumer;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class IntCollectionUtil {

  @Nullable
  public static IntSet intersect(IntSet @NotNull ... sets) {
    Arrays.sort(sets, (set1, set2) -> {
      if (set1 == null) return -1;
      if (set2 == null) return 1;
      return set1.size() - set2.size();
    });
    IntSet result = null;
    for (IntSet set : sets) {
      result = intersect(result, set);
    }

    return result;
  }

  public static boolean intersects(@NotNull IntSet set1, @NotNull IntSet set2) {
    if (set1.size() <= set2.size()) {
      IntIterator it = set1.intIterator();
      while (it.hasNext()) {
        int value = it.nextInt();
        if (set2.contains(value)) return true;
      }
      return false;
    }
    return intersects(set2, set1);
  }

  @Contract("null, null -> null; !null, _ -> !null; _, !null -> !null")
  @Nullable
  public static IntSet intersect(@Nullable IntSet set1, @Nullable IntSet set2) {
    if (set1 == null) return set2;
    if (set2 == null) return set1;

    IntSet result = new IntOpenHashSet();
    if (set1.size() < set2.size()) {
      intersectTo(set1, set2, result);
    }
    else {
      intersectTo(set2, set1, result);
    }

    return result;
  }

  private static void intersectTo(@NotNull IntSet small, @NotNull IntSet big, @NotNull IntSet result) {
    for (IntIterator iterator = small.iterator(); iterator.hasNext(); ) {
      int value = iterator.nextInt();
      if (big.contains(value)) {
        result.add(value);
      }
    }
  }

  @NotNull
  public static IntSet union(@NotNull Collection<? extends IntSet> sets) {
    IntSet result = new IntOpenHashSet();
    for (IntSet set : sets) {
      result.addAll(set);
    }
    return result;
  }

  @NotNull
  public static <T> List<T> map2List(@NotNull IntSet set, @NotNull IntFunction<? extends T> function) {
    return set.intStream().mapToObj(function).collect(Collectors.toList());
  }

  @NotNull
  public static <T> IntSet map2IntSet(@NotNull Collection<? extends T> collection, @NotNull ToIntFunction<? super T> function) {
    IntOpenHashSet result = new IntOpenHashSet();
    for (T t : collection) {
      result.add(function.applyAsInt(t));
    }
    return result;
  }

  @NotNull
  public static <T> Map<T, IntSet> groupByAsIntSet(@NotNull IntCollection collection, @NotNull IntFunction<? extends T> function) {
    Map<T, IntSet> result = new HashMap<>();
    collection.forEach((IntConsumer)(it) -> {
      T key = function.apply(it);
      IntSet values = result.computeIfAbsent(key, __ -> new IntOpenHashSet());
      values.add(it);
    });
    return result;
  }

  public static void processBatches(@NotNull IntStream stream,
                                    int batchSize,
                                    @NotNull ThrowableConsumer<? super IntSet, ? extends VcsException> consumer)
    throws VcsException {
    Ref<IntSet> batch = new Ref<>(new IntOpenHashSet());
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
          batch.set(new IntOpenHashSet());
        }
      }
    });

    if (!batch.get().isEmpty()) {
      consumer.consume(batch.get());
    }

    if (!exception.isNull()) throw exception.get();
  }

  public static <T> void add(@NotNull Map<? super T, IntSet> targetMap, @NotNull T key, int value) {
    IntSet set = targetMap.computeIfAbsent(key, __ -> new IntOpenHashSet());
    set.add(value);
  }
}
