/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.util.containers.ContainerUtil;

import java.util.*;

/**
 * @author dsl
 */
public class CachingSemiGraph<Node> implements GraphGenerator.SemiGraph<Node> {
  public static <T> InboundSemiGraph<T> cache(InboundSemiGraph<T> original) {
    return new CachingSemiGraph<T>(original);
  }

  private final Set<Node> myNodes;
  private final Map<Node, Set<Node>> myIn;

  private CachingSemiGraph(InboundSemiGraph<Node> original) {
    myNodes = ContainerUtil.newLinkedHashSet(original.getNodes());
    myIn = new LinkedHashMap<Node, Set<Node>>();
    for (Node node : myNodes) {
      Set<Node> value = new LinkedHashSet<Node>();
      ContainerUtil.addAll(value, original.getIn(node));
      myIn.put(node, value);
    }
  }

  @Override
  public Collection<Node> getNodes() {
    return myNodes;
  }

  @Override
  public Iterator<Node> getIn(Node n) {
    return myIn.get(n).iterator();
  }

  //<editor-fold desc="Deprecated stuff.">
  /** @deprecated use {@link #cache(InboundSemiGraph)} (to be removed in IDEA 2018) */
  public static <T> CachingSemiGraph<T> create(GraphGenerator.SemiGraph<T> original) {
    return new CachingSemiGraph<T>((InboundSemiGraph<T>)original);
  }

  /** @deprecated use {@link #cache(InboundSemiGraph)} (to be removed in IDEA 2018) */
  public CachingSemiGraph(GraphGenerator.SemiGraph<Node> original) {
    this((InboundSemiGraph<Node>)original);
  }
  //</editor-fold>
}