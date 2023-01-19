// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.graph;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class GraphGenerator<Node> implements Graph<Node> {
  @NotNull
  public static <T> Graph<T> generate(@NotNull InboundSemiGraph<T> graph) {
    return new GraphGenerator<>(graph);
  }

  private final InboundSemiGraph<Node> myGraph;
  private final Map<Node, List<Node>> myOuts;

  private GraphGenerator(@NotNull InboundSemiGraph<Node> graph) {
    myGraph = graph;
    myOuts = new HashMap<>();
    buildOuts();
  }

  private void buildOuts() {
    Set<Pair<Node, Node>> edges = new HashSet<>();
    for (Node node : myGraph.getNodes()) {
      Iterator<Node> inIt = myGraph.getIn(node);
      while (inIt.hasNext()) {
        Node inNode = inIt.next();

        if (!edges.add(new Pair<>(inNode, node))) {
          // Duplicate edge
          continue;
        }
        myOuts.computeIfAbsent(inNode, __ -> new ArrayList<>()).add(node);
      }
    }
  }

  @NotNull
  @Override
  public Collection<Node> getNodes() {
    return myGraph.getNodes();
  }

  @NotNull
  @Override
  public Iterator<Node> getIn(Node n) {
    return myGraph.getIn(n);
  }

  @NotNull
  @Override
  public Iterator<Node> getOut(Node n) {
    List<Node> outNodes = myOuts.get(n);
    return outNodes != null ? outNodes.iterator() : Collections.emptyIterator();
  }
}