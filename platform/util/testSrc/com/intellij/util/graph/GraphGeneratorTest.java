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
package com.intellij.util.graph;

import com.intellij.util.containers.EmptyIterator;
import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 *  @author dsl
 */
public class GraphGeneratorTest extends TestCase {

  public void testEmptyGraph() {
    final TestNode node = new TestNode("A");
    final GraphGenerator<TestNode> graphGenerator = new GraphGenerator<TestNode>(new GraphGenerator.SemiGraph<TestNode>() {
        @Override
        public Collection<TestNode> getNodes() {
          return Arrays.asList(new TestNode[]{node});
        }

        @Override
        public Iterator<TestNode> getIn(TestNode n) {
          return EmptyIterator.getInstance();
        }
      });

    assertFalse(graphGenerator.getOut(node).hasNext());
  }

  public void testLoop() {
    final TestNode nodeA = new TestNode("A");
    final TestNode nodeB = new TestNode("B");
    final TestNode[] nodes = new TestNode[]{nodeA, nodeB};
    final TestNode[] inA  = new TestNode[]{nodeB};
    final TestNode[] inB = new TestNode[] {nodeA};
    final TestNode[] outA = inA;
    final TestNode[] outB = inB;

    final GraphGenerator<TestNode> graph = new GraphGenerator<TestNode>(new GraphGenerator.SemiGraph<TestNode>() {
      @Override
      public Collection<TestNode> getNodes() {
        return Arrays.asList(nodes);
      }

      @Override
      public Iterator<TestNode> getIn(TestNode n) {
        if (n == nodeA) return Arrays.asList(inA).iterator();
        if (n == nodeB) return Arrays.asList(inB).iterator();
        throw new NoSuchElementException();
      }
    });
    GraphTestUtil.assertIteratorsEqual(Arrays.asList(outA).iterator(), graph.getOut(nodeA));
    GraphTestUtil.assertIteratorsEqual(Arrays.asList(outB).iterator(), graph.getOut(nodeB));
  }


}
