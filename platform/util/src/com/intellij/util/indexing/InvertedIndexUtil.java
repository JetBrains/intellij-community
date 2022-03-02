// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.util.Condition;
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
  @NotNull
  public static <K, V, I> IntSet collectInputIdsContainingAllKeys(@NotNull InvertedIndex<? super K, V, I> index,
                                                                  @NotNull Collection<? extends K> dataKeys,
                                                                  @Nullable Condition<? super V> valueChecker,
                                                                  @Nullable IntPredicate idChecker)
    throws StorageException {
    IntSet mainIntersection = null;

    for (K dataKey : dataKeys) {
      IOCancellationCallbackHolder.checkCancelled();

      IntSet copy = new IntOpenHashSet();
      ValueContainer<V> container = index.getData(dataKey);

      for (ValueContainer.ValueIterator<V> valueIt = container.getValueIterator(); valueIt.hasNext(); ) {
        final V value = valueIt.next();
        if (valueChecker != null && !valueChecker.value(value)) {
          continue;
        }
        IOCancellationCallbackHolder.checkCancelled();

        ValueContainer.IntIterator iterator = valueIt.getInputIdsIterator();

        final IntPredicate predicate;
        if (mainIntersection == null || iterator.size() < mainIntersection.size() || (predicate = valueIt.getValueAssociationPredicate()) == null) {
          while (iterator.hasNext()) {
            final int id = iterator.next();
            if (mainIntersection == null && (idChecker == null || idChecker.test(id)) ||
                mainIntersection != null && mainIntersection.contains(id)) {
              copy.add(id);
            }
          }
        }
        else {
          for (IntIterator intIterator = mainIntersection.iterator(); intIterator.hasNext(); ) {
            int id = intIterator.nextInt();
            if (predicate.test(id) && (idChecker == null || idChecker.test(id))) {
              copy.add(id);
            }
          }
        }
      }

      mainIntersection = copy;
      if (mainIntersection.isEmpty()) {
        return IntSets.EMPTY_SET;
      }
    }

    return mainIntersection == null ? IntSets.EMPTY_SET : mainIntersection;
  }

  @NotNull
  public static <K, V, I> IntSet collectInputIdsContainingAnyKey(@NotNull InvertedIndex<? super K, V, I> index,
                                                                 @NotNull Collection<? extends K> dataKeys,
                                                                 @Nullable Condition<? super V> valueChecker,
                                                                 @Nullable IntPredicate idChecker) throws StorageException {
    IntSet result = null;
    for (K dataKey : dataKeys) {
      IOCancellationCallbackHolder.checkCancelled();
      ValueContainer<V> container = index.getData(dataKey);
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
          if (result == null) result = new IntOpenHashSet();
          result.add(id);
        }
      }
    }
    return result == null ? IntSets.EMPTY_SET : result;
  }
}
