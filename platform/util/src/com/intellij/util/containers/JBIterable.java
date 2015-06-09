/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.util.containers;


import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * An in-house version of com.google.common.collect.FluentIterable.
 *
 * The original JavaDoc:
 * <p/>
 * {@code FluentIterable} provides a rich interface for manipulating {@code Iterable} instances in a
 * chained fashion. A {@code FluentIterable} can be created from an {@code Iterable}, or from a set
 * of elements. The following types of methods are provided on {@code FluentIterable}:
 * <ul>
 * <li>chained methods which return a new {@code FluentIterable} based in some way on the contents
 * of the current one (for example {@link #transform})
 * <li>conversion methods which copy the {@code FluentIterable}'s contents into a new collection or
 * array (for example {@link #toList})
 * <li>element extraction methods which facilitate the retrieval of certain elements (for example
 * {@link #last})
 * </ul>
 * <p/>
 * <p>Here is an example that merges the lists returned by two separate database calls, transforms
 * it by invoking {@code toString()} on each element, and returns the first 10 elements as an
 * {@code List}: <pre>   {@code
 *   FluentIterable
 *       .from(database.getClientList())
 *       .filter(activeInLastMonth())
 *       .transform(Functions.toStringFunction())
 *       .toList();}</pre>
 * <p/>
 * <p>Anything which can be done using {@code FluentIterable} could be done in a different fashion
 * (often with {@link Iterables}), however the use of {@code FluentIterable} makes many sets of
 * operations significantly more concise.
 *
 * @author Marcin Mikosik
 */
public abstract class JBIterable<E> implements Iterable<E> {

  // We store 'iterable' and use it instead of 'this' to allow Iterables to perform instanceof
  // checks on the _original_ iterable when FluentIterable.from is used.
  final Iterable<E> myIterable;

  /**
   * Constructor for use by subclasses.
   */
  protected JBIterable() {
    myIterable = this;
  }

  JBIterable(@NotNull Iterable<E> iterable) {
    myIterable = iterable;
  }

  /**
   * Returns a fluent iterable that wraps {@code iterable}, or {@code iterable} itself if it
   * is already a {@code FluentIterable}.
   */
  @NotNull
  public static <E> JBIterable<E> from(@Nullable Iterable<? extends E> iterable) {
    if (iterable == null) return empty();
    if (iterable instanceof JBIterable) return (JBIterable<E>)iterable;
    return new JBIterable<E>((Iterable<E>)iterable) {
      @Override
      public Iterator<E> iterator() {
        return myIterable.iterator();
      }
    };
  }

  /**
   * Returns a fluent iterable containing {@code elements} in the specified order.
   */
  @NotNull
  public static <E> JBIterable<E> of(E... elements) {
    return from(ContainerUtil.newArrayList(elements));
  }

  private static final JBIterable EMPTY = new JBIterable() {
    @Override
    public Iterator iterator() {
      return ContainerUtil.emptyIterator();
    }
  };

  @NotNull
  public static <E> JBIterable<E> empty() {
    return (JBIterable<E>)EMPTY;
  }

  /**
   * Returns a string representation of this fluent iterable, with the format
   * {@code [e1, e2, ..., en]}.
   */
  @Override
  public String toString() {
    return "(" + StringUtil.join(takeWhile(Conditions.countDown(50)), ", ") + ")";
  }

  /**
   * Returns the number of elements in this fluent iterable.
   */
  public final int size() {
    int count = 0;
    for (E ignored : myIterable) {
      count++;
    }
    return count;
  }

  /**
   * Returns {@code true} if this fluent iterable contains any object for which
   * {@code equals(element)} is true.
   */
  public final boolean contains(@Nullable Object element) {
    if (myIterable instanceof Collection) {
      return ((Collection)myIterable).contains(element);
    }
    for (E e : myIterable) {
      if (Comparing.equal(e, element)) return true;
    }
    return false;
  }

  /**
   * Returns a fluent iterable whose iterators traverse first the elements of this fluent iterable,
   * followed by those of {@code other}. The iterators are not polled until necessary.
   * <p/>
   * <p>The returned iterable's {@code Iterator} supports {@code remove()} when the corresponding
   * {@code Iterator} supports it.
   */
  public final JBIterable<E> append(@Nullable Iterable<? extends E> other) {
    return other == null ? this : this == EMPTY ? from(other) : from(ContainerUtil.concat(myIterable, other));
  }

  public final <T> JBIterable<E> append(@Nullable Iterable<T> other, @NotNull Function<? super T, Iterable<E>> fun) {
    return other == null ? this : this == EMPTY ? from(other).flatten(fun) : append(from(other).flatten(fun));
  }

  /**
   * Returns a fluent iterable whose iterators traverse first the elements of this fluent iterable,
   * followed by {@code elements}.
   */
  public final JBIterable<E> append(@NotNull E[] elements) {
    return this == EMPTY ? of(elements) : append(Arrays.asList(elements));
  }

  public final JBIterable<E> append(@Nullable E e) {
    return e == null ? this : this == EMPTY ? of(e) : append(Collections.singleton(e));
  }

  /**
   * Returns the elements from this fluent iterable that satisfy a condition. The
   * resulting fluent iterable's iterator does not support {@code remove()}.
   */
  public final JBIterable<E> filter(@NotNull final Condition<? super E> condition) {
    final JBIterable<E> it = this;
    return new JBIterable<E>() {
      @Override
      public Iterator<E> iterator() {
        return FilteringIterator.create(it.iterator(), condition);
      }
    };
  }

  /**
   * Returns the elements from this fluent iterable that are instances of class {@code type}.
   * @param type the type of elements desired
   */
  public final <T> JBIterable<T> filter(@NotNull Class<T> type) {
    //noinspection unchecked
    return (JBIterable<T>)filter(Conditions.instanceOf(type));
  }

  public final JBIterable<E> takeWhile(@NotNull final Condition<? super E> condition) {
    final JBIterable<E> it = this;
    return new JBIterable<E>() {
      @Override
      public Iterator<E> iterator() {
        final Iterator<E> iterator = it.iterator();
        //noinspection unchecked
        return new Iterator<E>() {
          E cur = (E)ObjectUtils.NULL;
          boolean acquired;

          @Override
          public boolean hasNext() {
            if (acquired) return cur != ObjectUtils.NULL;
            boolean b = iterator.hasNext();
            cur = b ? iterator.next() : null;
            acquired = true;
            b &= condition.value(cur);
            //noinspection unchecked
            cur = b ? cur : (E)ObjectUtils.NULL;
            return b;
          }

          @Override
          public E next() {
            if (cur == ObjectUtils.NULL) throw new NoSuchElementException();
            acquired = false;
            return cur;
          }

          @Override
          public void remove() {
            iterator.remove();
          }
        };
      }
    };
  }


  /**
   * Returns a fluent iterable that applies {@code function} to each element of this
   * fluent iterable.
   * <p/>
   * <p>The returned fluent iterable's iterator supports {@code remove()} if this iterable's
   * iterator does. After a successful {@code remove()} call, this fluent iterable no longer
   * contains the corresponding element.
   */
  @NotNull
  public final <T> JBIterable<T> transform(@NotNull final Function<? super E, T> function) {
    return from(new Iterable<T>() {
      @Override
      public Iterator<T> iterator() {
        final Iterator<E> iterator = JBIterable.this.iterator();
        return new Iterator<T>() {
          @Override
          public boolean hasNext() {
            return iterator.hasNext();
          }

          @Override
          public T next() {
            return function.fun(iterator.next());
          }

          @Override
          public void remove() {
            iterator.remove();
          }
        };
      }
    });
  }

  /**
   * Returns a fluent iterable that applies {@code function} to each element of this
   * fluent iterable and concats the produced iterables in one.
   * <p/>
   * <p>The returned fluent iterable's iterator supports {@code remove()} if an underlying iterable's
   * iterator does. After a successful {@code remove()} call, this fluent iterable no longer
   * contains the corresponding element.
   */
  @NotNull
  public <T> JBIterable<T> flatten(final Function<? super E, Iterable<T>> function) {
    if (this == EMPTY) return empty();
    final Iterable<E> thatIt = myIterable;
    return new JBIterable<T>() {
      @Override
      public Iterator<T> iterator() {
        final Iterator<E> it = thatIt.iterator();
        return new Iterator<T>() {
          Iterator<T> cur;

          @Override
          public boolean hasNext() {
            while ((cur == null || !cur.hasNext()) && it.hasNext()) {
              cur = function.fun(it.next()).iterator();
            }
            return cur != null && cur.hasNext();
          }

          @Override
          public T next() {
            return cur.next();
          }

          @Override
          public void remove() {
            cur.remove();
          }
        };
      }
    };
  }

  /**
   * Returns the first element in this fluent iterable or null.
   */
  @Nullable
  public final E first() {
    Iterator<E> iterator = myIterable.iterator();
    return iterator.hasNext() ? iterator.next() : null;
  }

  /**
   * Returns the last element in this fluent iterable or null.
   */
  @Nullable
  public final E last() {
    if (myIterable instanceof List) {
      return ContainerUtil.getLastItem((List<E>)myIterable);
    }
    Iterator<E> iterator = myIterable.iterator();
    E cur = null;
    while (iterator.hasNext()) {
      cur = iterator.next();
    }
    return cur;
  }

  /**
   * Determines whether this fluent iterable is empty.
   */
  public final boolean isEmpty() {
    return !myIterable.iterator().hasNext();
  }

  /**
   * Returns an {@code List} containing all of the elements from this fluent iterable in
   * proper sequence.
   */
  public final List<E> toList() {
    return Collections.unmodifiableList(ContainerUtil.newArrayList(myIterable));
  }

  /**
   * Returns an {@code Set} containing all of the elements from this fluent iterable with
   * duplicates removed.
   */
  public final Set<E> toSet() {
    return Collections.unmodifiableSet(ContainerUtil.newHashSet(myIterable));
  }

  /**
   * Returns an {@code Map} for which the elements of this {@code FluentIterable} are the keys in
   * the same order, mapped to values by the given function. If this iterable contains duplicate
   * elements, the returned map will contain each distinct element once in the order it first
   * appears.
   */
  public final <V> Map<E, V> toMap(Convertor<E, V> valueFunction) {
    return Collections.unmodifiableMap(ContainerUtil.newMapFromKeys(iterator(), valueFunction));
  }

  /**
   * Copies all the elements from this fluent iterable to {@code collection}. This is equivalent to
   * calling {@code Iterables.addAll(collection, this)}.
   *
   * @param collection the collection to copy elements to
   * @return {@code collection}, for convenience
   */
  public final <C extends Collection<? super E>> C addAllTo(@NotNull C collection) {
    if (myIterable instanceof Collection) {
      collection.addAll((Collection<E>)myIterable);
    }
    else {
      for (E item : myIterable) {
        collection.add(item);
      }
    }
    return collection;
  }
}
