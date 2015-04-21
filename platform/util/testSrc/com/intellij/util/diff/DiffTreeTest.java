/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util.diff;

import com.intellij.openapi.util.Ref;
import com.intellij.util.ThreeState;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author max
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class DiffTreeTest extends TestCase {
  private static class Node {
    @NotNull
    private final Node[] myChildren;
    private final int myId;

    public Node(final int id, @NotNull Node... children) {
      myChildren = children;
      myId = id;
    }

    @Override
    public int hashCode() {
      return myId + myChildren.length; // This is intentionally bad hashcode
    }

    @NotNull
    public Node[] getChildren() {
      return myChildren;
    }

    public int getId() {
      return myId;
    }

    @Override
    public String toString() {
      return String.valueOf(myId);
    }
  }

  private static class TreeStructure implements FlyweightCapableTreeStructure<Node> {
    private final Node myRoot;

    private TreeStructure(final Node root) {
      myRoot = root;
    }

    @Override
    @NotNull
    public Node getRoot() {
      return myRoot;
    }

    @Override
    public Node getParent(@NotNull final Node node) {
      return null;
    }

    @Override
    @NotNull
    public Node prepareForGetChildren(@NotNull final Node node) {
      return node;
    }

    @Override
    public int getChildren(@NotNull final Node node, @NotNull final Ref<Node[]> into) {
      into.set(node.getChildren());
      return into.get().length;
    }

    @Override
    public void disposeChildren(final Node[] nodes, final int count) {
    }

    @NotNull
    @Override
    public CharSequence toString(@NotNull Node node) {
      return node.toString();
    }
  }

  private static class NodeComparator implements ShallowNodeComparator<Node, Node> {
    @NotNull
    @Override
    public ThreeState deepEqual(@NotNull final Node node, @NotNull final Node node1) {
      return ThreeState.UNSURE;
    }

    @Override
    public boolean typesEqual(@NotNull final Node node, @NotNull final Node node1) {
      return node.getId() == node1.getId();
    }

    @Override
    public boolean hashCodesEqual(@NotNull final Node node, @NotNull final Node node1) {
      return node.hashCode() == node1.hashCode();
    }
  }

  private static class DiffBuilder implements DiffTreeChangeBuilder<Node, Node> {
    private final List<String> myResults = new ArrayList<String>();

    @Override
    public void nodeReplaced(@NotNull final Node oldNode, @NotNull final Node newNode) {
      myResults.add("REPLACED: " + oldNode + " to " + newNode);
    }

    @Override
    public void nodeDeleted(@NotNull final Node parent, @NotNull final Node child) {
      myResults.add("DELETED from " + parent + ": " + child);
    }

    @Override
    public void nodeInserted(@NotNull final Node oldParent, @NotNull final Node node, final int pos) {
      myResults.add("INSERTED to " + oldParent + ": " + node + " at " + pos);
    }

    public List<String> getEvents() {
      return myResults;
    }
  }

  public void testEmptyEqualRoots() throws Exception {
    Node r1 = new Node(0);
    Node r2 = new Node(0);
    final String expected = "";

    performTest(r1, r2, expected);
  }

  public void testSingleChildEqualRoots() throws Exception {
    Node r1 = new Node(0, new Node(1));
    Node r2 = new Node(0, new Node(1));
    final String expected = "";

    performTest(r1, r2, expected);
  }

  public void testTheOnlyChildRemoved() throws Exception {
    Node r1 = new Node(0, new Node(1));
    Node r2 = new Node(0);
    String expected = "DELETED from 0: 1";

    performTest(r1, r2, expected);
  }

  public void testTheOnlyChildAdded() throws Exception {
    Node r1 = new Node(0);
    Node r2 = new Node(0, new Node(1));
    String expected = "INSERTED to 0: 1 at 0";

    performTest(r1, r2, expected);

  }

  public void testTheOnlyChildReplaced() throws Exception {
    Node r1 = new Node(0, new Node(1));
    Node r2 = new Node(0, new Node(2));
    String expected = "REPLACED: 1 to 2";

    performTest(r1, r2, expected);
  }

  public void testInsertedIntoTheMiddle() throws Exception {
    Node r1 = new Node(0, new Node(1, new Node(21), new Node(23)));
    Node r2 = new Node(0, new Node(1, new Node(21), new Node(22), new Node(23)));
    String expected = "INSERTED to 1: 22 at 1";

    performTest(r1, r2, expected);
  }

  public void testInsertedFirst() throws Exception {
    Node r1 = new Node(0, new Node(1, new Node(22), new Node(23)));
    Node r2 = new Node(0, new Node(1, new Node(21), new Node(22), new Node(23)));
    String expected = "INSERTED to 1: 21 at 0";

    performTest(r1, r2, expected);
  }

  public void testInsertedLast() throws Exception {
    Node r1 = new Node(0, new Node(1, new Node(21), new Node(22)));
    Node r2 = new Node(0, new Node(1, new Node(21), new Node(22), new Node(23)));
    String expected = "INSERTED to 1: 23 at 2";

    performTest(r1, r2, expected);
  }

  public void testInsertedTwoLast() throws Exception {
    Node r1 = new Node(0, new Node(1, new Node(21), new Node(22)));
    Node r2 = new Node(0, new Node(1, new Node(21), new Node(22), new Node(23), new Node(24)));

    performTest(r1, r2, "INSERTED to 1: 24 at 2", "INSERTED to 1: 23 at 2");
  }

  public void testSubtreeAppears() throws Exception {
    Node r1 = new Node(0, new Node(1, new Node(21), new Node(22), new Node(23)));
    Node r2 = new Node(0, new Node(1, new Node(21), new Node(22, new Node(221)), new Node(23)));

    performTest(r1, r2, "INSERTED to 22: 221 at 0");
  }

  public void testSubtreeChanges() throws Exception {
    Node r1 = new Node(0, new Node(1, new Node(21), new Node(22, new Node(221)), new Node(23)));
    Node r2 = new Node(0, new Node(1, new Node(21), new Node(250, new Node(222)), new Node(23)));

    performTest(r1, r2, "REPLACED: 22 to 250");
  }

  private static void performTest(final Node r1, final Node r2, final String... expected) {
    final DiffBuilder result = new DiffBuilder();
    DiffTree.diff(new TreeStructure(r1), new TreeStructure(r2), new NodeComparator(), result);

    final List<String> expectedList = Arrays.asList(expected);
    final List<String> actual = result.getEvents();
    if (!expectedList.isEmpty() && !actual.isEmpty()) {
      assertEquals(expectedList, actual);
    }
  }
}
