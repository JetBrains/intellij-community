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

import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
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
  private final Map<Node, List<Node>> myOuts;

  private GraphGenerator(@NotNull InboundSemiGraph<Node> graph) {
    myGraph = graph;
    myOuts = new THashMap<Node, List<Node>>();
    buildOuts();
  }

  private void buildOuts() {
    final Set<Pair<Node, Node>> edges = new THashSet<Pair<Node, Node>>();

    Collection<Node> nodes = myGraph.getNodes();

    for (Node node : nodes) {
      Iterator<Node> inIt = myGraph.getIn(node);
      while (inIt.hasNext()) {
        Node inNode = inIt.next();

        if (!edges.add(Pair.create(inNode, node))) {
          // Duplicate edge
          continue;
        }

        List<Node> edgesFromInNode = myOuts.get(inNode);
        if (edgesFromInNode == null) {
          edgesFromInNode = new ArrayList<Node>();
          myOuts.put(inNode, edgesFromInNode);
        }
        edgesFromInNode.add(node);
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
    final List<Node> outNodes = myOuts.get(n);
    return outNodes != null
           ? outNodes.iterator()
           : ContainerUtil.<Node>emptyIterator();
  }

  //<editor-fold desc="Deprecated stuff.">
  public interface SemiGraph<Node> extends InboundSemiGraph<Node> {
    Collection<Node> getNodes();

    Iterator<Node> getIn(Node n);
  }

  /** @deprecated use {@link #generate(InboundSemiGraph)} (to be removed in IDEA 2018) */
  public static <T> GraphGenerator<T> create(SemiGraph<T> graph) {
    return new GraphGenerator<T>((InboundSemiGraph<T>)graph);
  }
  //</editor-fold>
}