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
        public Collection<TestNode> getNodes() {
          return Arrays.asList(new TestNode[]{node});
        }

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
      public Collection<TestNode> getNodes() {
        return Arrays.asList(nodes);
      }

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
