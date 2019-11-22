/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Common {@link Processor} collect/find implementations.
 * .
 * @author max
 */
public class CommonProcessors {
  public static class CollectProcessor<T> implements Processor<T> {
    private final Collection<T> myCollection;

    public CollectProcessor(@NotNull Collection<T> collection) {
      myCollection = collection;
    }

    public CollectProcessor() {
      myCollection = new ArrayList<>();
    }

    @Override
    public boolean process(T t) {
      if (accept(t)) {
        myCollection.add(t);
      }
      return true;
    }

    protected boolean accept(T t) {
      return true;
    }

    @NotNull
    public T[] toArray(@NotNull T[] a) {
      return myCollection.toArray(a);
    }

    @NotNull
    public Collection<T> getResults() {
      return myCollection;
    }
  }

  @NotNull
  public static <T> Processor<T> notNullProcessor(@NotNull final Processor<? super T> processor) {
    return processor::process;
  }


  public static class CollectUniquesProcessor<T> implements Processor<T> {
    private final Set<T> myCollection;

    public CollectUniquesProcessor() {
      myCollection = new HashSet<>();
    }

    @Override
    public boolean process(T t) {
      myCollection.add(t);
      return true;
    }

    @NotNull
    public T[] toArray(@NotNull T[] a) {
      return myCollection.toArray(a);
    }

    @NotNull
    public Collection<T> getResults() {
      return myCollection;
    }
  }

  public static class UniqueProcessor<T> implements Processor<T> {
    private final Set<T> processed;
    private final Processor<? super T> myDelegate;

    public UniqueProcessor(@NotNull Processor<? super T> delegate) {
      this(delegate, ContainerUtil.canonicalStrategy());
    }

    public UniqueProcessor(@NotNull Processor<? super T> delegate, @NotNull TObjectHashingStrategy<T> strategy) {
      myDelegate = delegate;
      processed = new THashSet<>(strategy);
    }

    @Override
    public boolean process(T t) {
      // in case of exception do not mark the element as processed, we couldn't recover otherwise
      synchronized (processed) {
        if (processed.contains(t)) {
          return true;
        }
      }
      boolean result = myDelegate.process(t);
      synchronized (processed) {
        processed.add(t);
      }
      return result;
    }
  }

  public abstract static class FindProcessor<T> implements Processor<T> {
    private T myValue;

    public boolean isFound() {
      return myValue != null;
    }

    @Nullable
    public T getFoundValue() {
      return myValue;
    }

    @Nullable
    public T reset() {
      T prev = myValue;
      myValue = null;
      return prev;
    }

    @Override
    public boolean process(T t) {
      if (accept(t)) {
        myValue = t;
        return false;
      }
      return true;
    }

    protected abstract boolean accept(T t);
  }

  public static class FindFirstProcessor<T> extends FindProcessor<T> {
    @Override
    protected boolean accept(T t) {
      return true;
    }
  }

  public static class FindFirstAndOnlyProcessor<T> extends FindFirstProcessor<T> {

    @Override
    public boolean process(T t) {
      boolean firstFound = getFoundValue() != null;
      boolean result = super.process(t);
      if (!result) {
        if (firstFound) reset();
        return !firstFound;
      }
      return true;
    }
  }
  
  /**
   * @return processor processing all elements.
   * Useful if you know that the processor shouldn't be stopped by client. It protects you from accidentally returning {@code false} value.
   */
  @NotNull
  public static <T> Processor<T> processAll(@NotNull final Consumer<? super T> consumer) {
    return t -> {
      consumer.consume(t);
      return true;
    };
  }
  
  private static final Processor<Object> FALSE = __ -> false;
  private static final Processor<Object> TRUE = __ -> true;

  @NotNull
  public static <T> Processor<T> alwaysFalse() {
    //noinspection unchecked
    return (Processor<T>)FALSE;
  }

  @NotNull
  public static <T> Processor<T> alwaysTrue() {
    //noinspection unchecked
    return (Processor<T>)TRUE;
  }
}
