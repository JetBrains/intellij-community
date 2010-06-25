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

import com.intellij.util.containers.ConcurrentHashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author max
 */
public class UniqueResultsQuery<T, M> implements Query<T> {
  private final Query<T> myOriginal;
  private final TObjectHashingStrategy<M> myHashingStrategy;
  private final Function<T, M> myMapper;

  public static final Function ID = new Function() {
    public Object fun(Object o) {
      return o;
    }
  };

  public UniqueResultsQuery(final Query<T> original) {
    //noinspection unchecked
    this(original, TObjectHashingStrategy.CANONICAL, ID);
  }

  public UniqueResultsQuery(final Query<T> original, TObjectHashingStrategy<M> hashingStrategy) {
    //noinspection unchecked
    this(original, hashingStrategy, ID);
  }

  public UniqueResultsQuery(final Query<T> original, TObjectHashingStrategy<M> hashingStrategy, Function<T, M> mapper) {
    myOriginal = original;
    myHashingStrategy = hashingStrategy;
    myMapper = mapper;
  }

  public T findFirst() {
    return myOriginal.findFirst();
  }

  public boolean forEach(@NotNull final Processor<T> consumer) {
    Collection<M> collection = process(consumer);
    return collection != null;
  }

  // returns null if canceled
  private Collection<M> process(final Processor<T> consumer) {
    final Set<M> processedElements = new ConcurrentHashSet<M>(myHashingStrategy);
    boolean success = myOriginal.forEach(new Processor<T>() {
      public boolean process(final T t) {
        return !processedElements.add(myMapper.fun(t)) || consumer.process(t);
      }
    });
    return success ? processedElements : null;
  }

  @NotNull
  public Collection<T> findAll() {
    if (myMapper == ID) {
      Collection<M> collection = process(CommonProcessors.<T>alwaysTrue());
      //noinspection unchecked
      return collection == null ? Collections.<T>emptyList() : (Collection<T>)collection;
    }
    else {
      final CommonProcessors.CollectProcessor<T> processor = new CommonProcessors.CollectProcessor<T>(new CopyOnWriteArrayList<T>());
      forEach(processor);
      return processor.getResults();
    }
  }

  public T[] toArray(final T[] a) {
    return findAll().toArray(a);
  }

  public Iterator<T> iterator() {
    return findAll().iterator();
  }
}
