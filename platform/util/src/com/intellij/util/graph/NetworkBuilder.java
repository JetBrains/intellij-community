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

import java.util.Objects;
import java.util.OptionalInt;


/**
 * A builder for constructing instances of {@link MutableNetwork} with
 * user-defined properties.
 *
 * <p>A network built by this class will have the following properties by default:
 *
 * <ul>
 *   <li>does not allow parallel edges
 *   <li>does not allow self-loops
 *   <li>orders {@link Network#nodes()} and {@link Network#edges()} in the order in which the
 *       elements were added
 * </ul>
 *
 * <p>Examples of use:
 *
 * <pre>{@code
 * // Building a mutable network
 * MutableNetwork<String, Integer> network =
 *     NetworkBuilder.directed().allowsParallelEdges(true).build();
 * flightNetwork.addEdge("LAX", "ATL", 3025);
 * flightNetwork.addEdge("LAX", "ATL", 1598);
 * flightNetwork.addEdge("ATL", "LAX", 2450);
 *
 * // Building a immutable network
 * ImmutableNetwork<String, Integer> immutableNetwork =
 *     NetworkBuilder.directed()
 *         .allowsParallelEdges(true)
 *         .<String, Integer>immutable()
 *         .addEdge("LAX", "ATL", 3025)
 *         .addEdge("LAX", "ATL", 1598)
 *         .addEdge("ATL", "LAX", 2450)
 *         .build();
 * }</pre>
 *
 * @author James Sexton
 * @author Joshua O'Madadhain
 * @param <N> The most general node type this builder will support. This is normally {@code Object}
 *     unless it is constrained by using a method like {@link #myNodeOrder}, or the builder is
 *     constructed based on an existing {@code Network}.
 * @param <E> The most general edge type this builder will support. This is normally {@code Object}
 *     unless it is constrained by using a method like {@link #myEdgeOrder}, or the builder is
 *     constructed based on an existing {@code Network}.
 */
public abstract class NetworkBuilder<N, E> {
  protected final boolean myIsDirected;
  protected boolean myDoAllowParallelEdges = false;
  protected boolean myDoAllowSelfLoops = false;
  
  protected ElementOrder<N> myNodeOrder = ElementOrder.insertion();
  protected ElementOrder<? super E> myEdgeOrder = ElementOrder.insertion();
  protected ElementOrder<N> myIncidentEdgeOrder = ElementOrder.unordered();

  protected OptionalInt myExpectedNodeCount = OptionalInt.empty();
  protected OptionalInt myExpectedEdgeCount = OptionalInt.empty();

  /** Creates a new instance with the specified edge directionality. */
  protected NetworkBuilder(boolean directed) {
    myIsDirected = directed;
  }

  /**
   * Specifies whether the network will allow parallel edges. Attempting to add a parallel edge to a
   * network that does not allow them will throw an {@link UnsupportedOperationException}.
   *
   * <p>The default value is {@code false}.
   */
  public NetworkBuilder<N, E> allowsParallelEdges(boolean allowsParallelEdges) {
    myDoAllowParallelEdges = allowsParallelEdges;
    return this;
  }

  /**
   * Specifies whether the network will allow self-loops (edges that connect a node to itself).
   * Attempting to add a self-loop to a network that does not allow them will throw an {@link
   * UnsupportedOperationException}.
   *
   * <p>The default value is {@code false}.
   */
  public NetworkBuilder<N, E> allowsSelfLoops(boolean allowsSelfLoops) {
    myDoAllowSelfLoops = allowsSelfLoops;
    return this;
  }

  /**
   * Specifies the expected number of nodes in the network.
   *
   * @throws IllegalArgumentException if {@code expectedNodeCount} is negative
   */
  public NetworkBuilder<N, E> expectedNodeCount(int expectedNodeCount) {
    assert expectedNodeCount >= 0;
    myExpectedNodeCount = OptionalInt.of(expectedNodeCount);
    return this;
  }

  /**
   * Specifies the expected number of edges in the network.
   *
   * @throws IllegalArgumentException if {@code expectedEdgeCount} is negative
   */
  public NetworkBuilder<N, E> expectedEdgeCount(int expectedEdgeCount) {
    assert expectedEdgeCount >= 0;
    myExpectedEdgeCount = OptionalInt.of(expectedEdgeCount);
    return this;
  }

  /**
   * Specifies the order of iteration for the elements of {@link Network#nodes()}.
   *
   * <p>The default value is {@link ElementOrder#insertion() insertion order}.
   */
  public <N1 extends N> NetworkBuilder<N1, E> nodeOrder(ElementOrder<N1> nodeOrder) {
    NetworkBuilder<N1, E> newBuilder = cast();
    newBuilder.myNodeOrder = Objects.requireNonNull(nodeOrder);
    return newBuilder;
  }

  /**
   * Specifies the order of iteration for the elements of {@link Network#edges()}.
   *
   * <p>The default value is {@link ElementOrder#insertion() insertion order}.
   */
  public <E1 extends E> NetworkBuilder<N, E1> edgeOrder(ElementOrder<E1> edgeOrder) {
    NetworkBuilder<N, E1> newBuilder = cast();
    newBuilder.myEdgeOrder = Objects.requireNonNull(edgeOrder);
    return newBuilder;
  }

  /** Returns an empty {@link MutableNetwork} with the properties of this {@link NetworkBuilder}. */
  public abstract <N1 extends N, E1 extends E> MutableNetwork<N1, E1> build();

  @SuppressWarnings("unchecked")
  private <N1 extends N, E1 extends E> NetworkBuilder<N1, E1> cast() {
    return (NetworkBuilder<N1, E1>) this;
  }
}
