/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author dsl
 */
public class GraphGenerator<Node> implements Graph<Node> {
  @NotNull
  public static <T> Graph<T> generate(InboundSemiGraph<T> graph) {
    return new GraphGenerator<T>(graph);
  }

  private final InboundSemiGraph<Node> myGraph;
  private final Map<Node, Set<Node>> myOuts;

  private GraphGenerator(@NotNull InboundSemiGraph<Node> graph) {
    myGraph = graph;
    myOuts = new LinkedHashMap<Node, Set<Node>>();
    buildOuts();
  }

  private void buildOuts() {
    Collection<Node> nodes = myGraph.getNodes();
    for (Node node : nodes) {
      myOuts.put(node, new LinkedHashSet<Node>());
    }

    for (Node node : nodes) {
      Iterator<Node> inIt = myGraph.getIn(node);
      while (inIt.hasNext()) {
        Node inNode = inIt.next();
        Set<Node> set = myOuts.get(inNode);
        if (set == null) {
          throw new AssertionError("Unexpected node " + inNode + "; nodes=" + nodes);
        }
        set.add(node);
      }
    }
  }

  @Override
  public Collection<Node> getNodes() {
    return myGraph.getNodes();
  }

  @Override
  public Iterator<Node> getIn(Node n) {
    return myGraph.getIn(n);
  }

  @Override
  public Iterator<Node> getOut(Node n) {
    return myOuts.get(n).iterator();
  }

  //<editor-fold desc="Deprecated stuff.">
  public interface SemiGraph<Node> extends InboundSemiGraph<Node> {
    Collection<Node> getNodes();

    Iterator<Node> getIn(Node n);
  }

  /** @deprecated use {@link #generate(InboundSemiGraph)} (to be removed in IDEA 2018) */
  public GraphGenerator(SemiGraph<Node> graph) {
    this((InboundSemiGraph<Node>)graph);
  }

  /** @deprecated use {@link #generate(InboundSemiGraph)} (to be removed in IDEA 2018) */
  public static <T> GraphGenerator<T> create(SemiGraph<T> graph) {
    return new GraphGenerator<T>((InboundSemiGraph<T>)graph);
  }
  //</editor-fold>
}