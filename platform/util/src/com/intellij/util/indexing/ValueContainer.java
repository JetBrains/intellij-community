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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

/**
 * @author Eugene Zhuravlev
 */
public abstract class ValueContainer<Value> {
  public interface IntIterator {
    boolean hasNext();

    int next();

    int size();
  }

  public interface IntPredicate {
    boolean contains(int id);
  }

  @NotNull
  public abstract ValueIterator<Value> getValueIterator();

  public interface ValueIterator<Value> extends Iterator<Value> {
    @NotNull
    IntIterator getInputIdsIterator();

    @Nullable
    IntPredicate getValueAssociationPredicate();
  }

  public abstract int size();

  public interface ContainerAction<T> {
    boolean perform(int id, T value);
  }

  public final boolean forEach(@NotNull ContainerAction<Value> action) {
    for (final ValueIterator<Value> valueIterator = getValueIterator(); valueIterator.hasNext();) {
      final Value value = valueIterator.next();
      for (final IntIterator intIterator = valueIterator.getInputIdsIterator(); intIterator.hasNext();) {
        if (!action.perform(intIterator.next(), value)) return false;
      }
    }
    return true;
  }
}
