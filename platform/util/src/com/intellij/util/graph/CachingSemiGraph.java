// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.graph;

import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author dsl
 */
public final class CachingSemiGraph<Node> implements InboundSemiGraph<Node> {
  @NotNull
  public static <T> InboundSemiGraph<T> cache(@NotNull InboundSemiGraph<T> original) {
    return new CachingSemiGraph<>(original);
  }

  private final Set<Node> myNodes;
  private final Map<Node, List<Node>> myIn;

  private CachingSemiGraph(@NotNull InboundSemiGraph<Node> original) {
    myNodes = new LinkedHashSet<>(original.getNodes());
    myIn = new HashMap<>();
    for (Node node : myNodes) {
      Iterator<Node> inIterator = original.getIn(node);
      if (inIterator.hasNext()) {
        List<Node> value = new ArrayList<>();
        while (inIterator.hasNext()) {
          value.add(inIterator.next());
        }
        myIn.put(node, value);
      }
    }
  }

  @NotNull
  @Override
  public Collection<Node> getNodes() {
    return myNodes;
  }

  @NotNull
  @Override
  public Iterator<Node> getIn(Node n) {
    final List<Node> inNodes = myIn.get(n);
    return inNodes != null
           ? inNodes.iterator()
           : Collections.emptyIterator();
  }
}