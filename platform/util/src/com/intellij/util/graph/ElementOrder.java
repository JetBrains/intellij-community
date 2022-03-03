/*
 * Copyright 2000-2021 JetBrains s.r.o.
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
package com.intellij.util.graph;

import org.jetbrains.annotations.Nullable;

import java.util.*;


/**
 * <p><b>NOTE:</b> The class is a full copy of the {@code com.google.common.graph.ElementOrder}.
 * We like Guava's implementation, but we cannot use Guava in our API because
 * we need additional abstraction layer on our side.</p>
 *
 * <h3>The description from Guava sources:</h3>
 * <p>
 * Used to represent the order of elements in a data structure that supports different options for
 * iteration order guarantees.
 *
 * @author Joshua O'Madadhain
 */
public final class ElementOrder<T> {
  private final Type myType;

  @SuppressWarnings("Immutable") // Hopefully the comparator provided is immutable!
  private final @Nullable Comparator<T> myComparator;

  /**
   * The type of ordering that this object specifies.
   *
   * <ul>
   *   <li>UNORDERED: no order is guaranteed.
   *   <li>STABLE: ordering is guaranteed to follow a pattern that won't change between releases.
   *       Some methods may have stronger guarantees.
   *   <li>INSERTION: insertion ordering is guaranteed.
   *   <li>SORTED: ordering according to a supplied comparator is guaranteed.
   * </ul>
   */
  public enum Type {
    UNORDERED,
    STABLE,
    INSERTION,
    SORTED
  }

  private ElementOrder(Type type, @Nullable Comparator<T> comparator) {
    myType = Objects.requireNonNull(type);
    myComparator = comparator;
    assert ((type == Type.SORTED) == (comparator != null));
  }

  /**
   * Returns an instance which specifies that no ordering is guaranteed.
   */
  public static <S> ElementOrder<S> unordered() {
    return new ElementOrder<>(Type.UNORDERED, null);
  }

  /**
   * Returns an instance which specifies that ordering is guaranteed to be always be the same across
   * iterations, and across releases. Some methods may have stronger guarantees.
   *
   * <p>This instance is only useful in combination with {@code incidentEdgeOrder}, e.g. {@code
   * graphBuilder.incidentEdgeOrder(ElementOrder.stable())}.
   *
   * <h3>In combination with {@code incidentEdgeOrder}</h3>
   *
   * <p>{@code incidentEdgeOrder(ElementOrder.stable())} guarantees the ordering of the returned
   * collections of the following methods:
   *
   * <ul>
   *   <li>For {@link Graph}:
   *       <ul>
   *         <li>{@code edges()}: Stable order
   *         <li>{@code adjacentNodes(node)}: Connecting edge insertion order
   *         <li>{@code predecessors(node)}: Connecting edge insertion order
   *         <li>{@code successors(node)}: Connecting edge insertion order
   *         <li>{@code incidentEdges(node)}: Edge insertion order
   *       </ul>
   *   <li>For {@link Network}:
   *       <ul>
   *         <li>{@code adjacentNodes(node)}: Stable order
   *         <li>{@code predecessors(node)}: Connecting edge insertion order
   *         <li>{@code successors(node)}: Connecting edge insertion order
   *         <li>{@code incidentEdges(node)}: Stable order
   *         <li>{@code inEdges(node)}: Edge insertion order
   *         <li>{@code outEdges(node)}: Edge insertion order
   *         <li>{@code adjacentEdges(edge)}: Stable order
   *         <li>{@code edgesConnecting(nodeU, nodeV)}: Edge insertion order
   *       </ul>
   * </ul>
   */
  public static <S> ElementOrder<S> stable() {
    return new ElementOrder<>(Type.STABLE, null);
  }

  /**
   * Returns an instance which specifies that insertion ordering is guaranteed.
   */
  public static <S> ElementOrder<S> insertion() {
    return new ElementOrder<>(Type.INSERTION, null);
  }

  /**
   * Returns an instance which specifies that the natural ordering of the elements is guaranteed.
   */
  public static <S extends Comparable<? super S>> ElementOrder<S> natural() {
    return new ElementOrder<>(Type.SORTED, Comparator.<S>naturalOrder());
  }

  /**
   * Returns an instance which specifies that the ordering of the elements is guaranteed to be
   * determined by {@code comparator}.
   */
  public static <S> ElementOrder<S> sorted(Comparator<S> comparator) {
    return new ElementOrder<S>(Type.SORTED, Objects.requireNonNull(comparator));
  }

  /**
   * Returns the type of ordering used.
   */
  public Type type() {
    return myType;
  }

  /**
   * Returns the {@link Comparator} used.
   *
   * @throws UnsupportedOperationException if comparator is not defined
   */
  public Comparator<T> comparator() {
    if (myComparator != null) {
      return myComparator;
    }
    throw new UnsupportedOperationException("This ordering does not define a comparator.");
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof ElementOrder)) {
      return false;
    }

    ElementOrder<?> other = (ElementOrder<?>)obj;
    return (myType == other.myType) && Objects.equals(myComparator, other.myComparator);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myType, myComparator);
  }

  @Override
  public String toString() {
    return "ElementOrder{" + "myType=" + myType + ", myComparator=" + myComparator + '}';
  }
}
