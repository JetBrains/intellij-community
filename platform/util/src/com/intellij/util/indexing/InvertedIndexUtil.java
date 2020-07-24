// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.util.Condition;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public final class InvertedIndexUtil {
  @NotNull
  public static <K, V, I> IntSet collectInputIdsContainingAllKeys(@NotNull InvertedIndex<? super K, V, I> index,
                                                                  @NotNull Collection<? extends K> dataKeys,
                                                                  @Nullable Condition<? super K> keyChecker,
                                                                  @Nullable Condition<? super V> valueChecker,
                                                                  @Nullable ValueContainer.IntPredicate idChecker)
    throws StorageException {
    IntSet mainIntersection = null;

    for (K dataKey : dataKeys) {
      if (keyChecker != null && !keyChecker.value(dataKey)) continue;

      IntSet copy = new IntOpenHashSet();
      ValueContainer<V> container = index.getData(dataKey);

      for (ValueContainer.ValueIterator<V> valueIt = container.getValueIterator(); valueIt.hasNext(); ) {
        final V value = valueIt.next();
        if (valueChecker != null && !valueChecker.value(value)) {
          continue;
        }

        ValueContainer.IntIterator iterator = valueIt.getInputIdsIterator();

        final ValueContainer.IntPredicate predicate;
        if (mainIntersection == null || iterator.size() < mainIntersection.size() || (predicate = valueIt.getValueAssociationPredicate()) == null) {
          while (iterator.hasNext()) {
            final int id = iterator.next();
            if (mainIntersection == null && (idChecker == null || idChecker.contains(id)) ||
                mainIntersection != null && mainIntersection.contains(id)
              ) {
              copy.add(id);
            }
          }
        }
        else {
          for (IntIterator intIterator = mainIntersection.iterator(); intIterator.hasNext(); ) {
            int id = intIterator.nextInt();
            if (predicate.contains(id)) {
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
}
