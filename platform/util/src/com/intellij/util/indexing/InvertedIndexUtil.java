// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.util.io.IOCancellationCallbackHolder;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.IntPredicate;

public final class InvertedIndexUtil {
  public static @NotNull <K, V, I> IntSet collectInputIdsContainingAllKeys(@NotNull InvertedIndex<? super K, V, I> index,
                                                                           @NotNull Collection<? extends K> dataKeys,
                                                                           @Nullable Condition<? super V> valueChecker,
                                                                           @Nullable IntPredicate idChecker)
    throws StorageException {

    Ref<IntSet> mainIntersectionRef = new Ref<>(null);
    for (K dataKey : dataKeys) {
      IOCancellationCallbackHolder.checkCancelled();

      IntSet copy = new IntOpenHashSet();
      index.withData(dataKey, container -> {
        for (ValueContainer.ValueIterator<V> valueIt = container.getValueIterator(); valueIt.hasNext(); ) {
          final V value = valueIt.next();
          if (valueChecker != null && !valueChecker.value(value)) {
            continue;
          }
          IOCancellationCallbackHolder.checkCancelled();

          ValueContainer.IntIterator iterator = valueIt.getInputIdsIterator();

          final IntPredicate predicate;
          if (mainIntersectionRef.isNull() ||
              iterator.size() < mainIntersectionRef.get().size() ||
              (predicate = valueIt.getValueAssociationPredicate()) == null) {
            while (iterator.hasNext()) {
              final int id = iterator.next();
              if (mainIntersectionRef.isNull() && (idChecker == null || idChecker.test(id))
                  || !mainIntersectionRef.isNull() && mainIntersectionRef.get().contains(id)) {
                copy.add(id);
              }
            }
          }
          else {
            for (IntIterator intIterator = mainIntersectionRef.get().iterator(); intIterator.hasNext(); ) {
              int id = intIterator.nextInt();
              if (predicate.test(id) && (idChecker == null || idChecker.test(id))) {
                copy.add(id);
              }
            }
          }
        }
        return true;
      });

      mainIntersectionRef.set( copy );
      if (mainIntersectionRef.get().isEmpty()) {
        return IntSets.EMPTY_SET;
      }
    }

    return mainIntersectionRef.isNull()? IntSets.EMPTY_SET : mainIntersectionRef.get();
  }

  public static @NotNull <K, V, I> IntSet collectInputIdsContainingAnyKey(@NotNull InvertedIndex<? super K, V, I> index,
                                                                          @NotNull Collection<? extends K> dataKeys,
                                                                          @Nullable Condition<? super V> valueChecker,
                                                                          @Nullable IntPredicate idChecker) throws StorageException {
    Ref<IntSet> resultRef = new Ref<>(null);
    for (K dataKey : dataKeys) {
      IOCancellationCallbackHolder.checkCancelled();
      index.withData(dataKey, container -> {
        for (ValueContainer.ValueIterator<V> valueIt = container.getValueIterator(); valueIt.hasNext(); ) {
          V value = valueIt.next();
          if (valueChecker != null && !valueChecker.value(value)) {
            continue;
          }
          IOCancellationCallbackHolder.checkCancelled();
          ValueContainer.IntIterator iterator = valueIt.getInputIdsIterator();
          while (iterator.hasNext()) {
            int id = iterator.next();
            if (idChecker != null && !idChecker.test(id)) continue;
            if (resultRef.isNull()) resultRef.set(new IntOpenHashSet());
            resultRef.get().add(id);
          }
        }
        return true;
      });
    }
    return resultRef.isNull() ? IntSets.EMPTY_SET : resultRef.get();
  }
}
