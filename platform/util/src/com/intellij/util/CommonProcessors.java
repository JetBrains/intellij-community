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

import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author max
 */
public class CommonProcessors {
  public static class CollectProcessor<T> implements Processor<T> {
    private final Collection<T> myCollection;

    public CollectProcessor(Collection<T> collection) {
      myCollection = collection;
    }

    public CollectProcessor() {
      myCollection = new ArrayList<T>();
    }

    public boolean process(T t) {
      myCollection.add(t);
      return true;
    }

    public T[] toArray(T[] a) {
      return myCollection.toArray(a);
    }

    public Collection<T> getResults() {
      return myCollection;
    }
  }

  @NotNull
  public static <T> Processor<T> notNullProcessor(@NotNull final Processor<T> processor) {
    return new Processor<T>() {
      public boolean process(@NotNull T t) {
        return processor.process(t);
      }
    };
  }


  public static class CollectUniquesProcessor<T> implements Processor<T> {
    private final Set<T> myCollection;

    public CollectUniquesProcessor() {
      myCollection = new HashSet<T>();
    }

    public boolean process(T t) {
      myCollection.add(t);
      return true;
    }

    public T[] toArray(T[] a) {
      return myCollection.toArray(a);
    }

    public Collection<T> getResults() {
      return myCollection;
    }
  }
  public static class UniqueProcessor<T> implements Processor<T> {
    private final Set<T> processed;
    private final Processor<T> myDelegate;

    public UniqueProcessor(Processor<T> delegate) {
      this(delegate, TObjectHashingStrategy.CANONICAL);
    }
    public UniqueProcessor(Processor<T> delegate, TObjectHashingStrategy<T> strategy) {
      myDelegate = delegate;
      processed = new THashSet<T>(strategy);
    }

    public boolean process(T t) {
      synchronized (processed) {
        if (!processed.add(t)) {
          return true;
        }
      }
      return myDelegate.process(t);
    }
  }

  public static class FindFirstProcessor<T> implements Processor<T> {
    private T myValue = null;

    public boolean isFound() {
      return myValue != null;
    }

    public T getFoundValue() {
      return myValue;
    }

    public boolean process(T t) {
      myValue = t;
      return false;
    }
  }

  private static final Processor FALSE = new Processor<Object>() {
    public boolean process(Object t) {
      return false;
    }
  };
  private static final Processor TRUE = new Processor<Object>() {
    public boolean process(Object t) {
      return true;
    }
  };

  @SuppressWarnings({"unchecked"})
  public static <T> Processor<T> alwaysFalse() {
    return FALSE;
  }
  @SuppressWarnings({"unchecked"})
  public static <T> Processor<T> alwaysTrue() {
    return TRUE;
  }
}
