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

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertFalse;

/**
 * @author dsl
 */
public class GraphGeneratorTest {
  @Test
  public void testEmptyGraph() {
    TestNode node = new TestNode("A");
    Graph<TestNode> graph = GraphGenerator.generate(new InboundSemiGraph<>() {
      @NotNull
      @Override
      public Collection<TestNode> getNodes() {
        return Collections.singletonList(node);
      }

      @NotNull
      @Override
      public Iterator<TestNode> getIn(TestNode n) {
        return Collections.emptyIterator();
      }
    });

    assertFalse(graph.getOut(node).hasNext());
  }

  @Test
  public void testLoop() {
    TestNode nodeA = new TestNode("A");
    TestNode nodeB = new TestNode("B");
    TestNode[] nodes = {nodeA, nodeB};
    TestNode[] inA = {nodeB};
    TestNode[] inB = {nodeA};

    Graph<TestNode> graph = GraphGenerator.generate(new InboundSemiGraph<>() {
      @NotNull
      @Override
      public Collection<TestNode> getNodes() {
        return Arrays.asList(nodes);
      }

      @NotNull
      @Override
      public Iterator<TestNode> getIn(TestNode n) {
        if (n == nodeA) return Arrays.asList(inA).iterator();
        if (n == nodeB) return Arrays.asList(inB).iterator();
        throw new NoSuchElementException();
      }
    });
    GraphTestUtil.assertIteratorsEqual(Arrays.asList(inA).iterator(), graph.getOut(nodeA));
    GraphTestUtil.assertIteratorsEqual(Arrays.asList(inB).iterator(), graph.getOut(nodeB));
  }
}