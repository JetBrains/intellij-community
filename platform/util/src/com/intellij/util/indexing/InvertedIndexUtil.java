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
package com.intellij.util.indexing;

import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.EmptyIntHashSet;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class InvertedIndexUtil {
  @NotNull
  public static <K, V, I> TIntHashSet collectInputIdsContainingAllKeys(@NotNull InvertedIndex<? super K, V, I> index,
                                                                       @NotNull Collection<? extends K> dataKeys,
                                                                       @Nullable Condition<? super K> keyChecker,
                                                                       @Nullable Condition<? super V> valueChecker,
                                                                       @Nullable ValueContainer.IntPredicate idChecker)
    throws StorageException {
    TIntHashSet mainIntersection = null;

    for (K dataKey : dataKeys) {
      if (keyChecker != null && !keyChecker.value(dataKey)) continue;

      final TIntHashSet copy = new TIntHashSet();
      final ValueContainer<V> container = index.getData(dataKey);

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
          mainIntersection.forEach(id -> {
            if (predicate.contains(id)) copy.add(id);
            return true;
          });
        }
      }

      mainIntersection = copy;
      if (mainIntersection.isEmpty()) {
        return EmptyIntHashSet.INSTANCE;
      }
    }

    return mainIntersection == null ? EmptyIntHashSet.INSTANCE : mainIntersection;
  }
}
