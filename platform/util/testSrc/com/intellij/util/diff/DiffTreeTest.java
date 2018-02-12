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
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
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
    private final int myStartOffset;
    @NotNull
    private final Node[] myChildren;
    private final int myId;

    public Node(final int id, int startOffset, @NotNull Node... children) {
      myStartOffset = startOffset;
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
      return getChildren().length == 0 ? String.valueOf(myId) : StringUtil.join(myChildren, node -> node.toString(), "");
    }

    public TextRange getTextRange() {
      int endOffset = myChildren.length == 0 ? myStartOffset + toString().length() : myChildren[myChildren.length-1].getTextRange().getEndOffset();
      return new TextRange(myStartOffset, endOffset);
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

    @Override
    public int getStartOffset(@NotNull Node node) {
      return node.getTextRange().getStartOffset();
    }

    @Override
    public int getEndOffset(@NotNull Node node) {
      return node.getTextRange().getEndOffset();
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
    private final List<String> myResults = new ArrayList<>();

    @Override
    public void nodeReplaced(@NotNull final Node oldNode, @NotNull final Node newNode) {
      myResults.add("REPLACED: " + oldNode.getId() + " to " + newNode.getId());
    }

    @Override
    public void nodeDeleted(@NotNull final Node parent, @NotNull final Node child) {
      myResults.add("DELETED from " + parent.getId() + ": " + child.getId());
    }

    @Override
    public void nodeInserted(@NotNull final Node oldParent, @NotNull final Node node, final int pos) {
      myResults.add("INSERTED to " + oldParent.getId() + ": " + node.getId() + " at " + pos);
    }

    public List<String> getEvents() {
      return myResults;
    }
  }

  public void testEmptyEqualRoots() {
    Node r1 = new Node(0,0);
    Node r2 = new Node(0,0);
    final String expected = "";

    performTest(r1, r2, expected);
  }

  public void testSingleChildEqualRoots() {
    Node r1 = new Node(0,0, new Node(1,0));
    Node r2 = new Node(0,0, new Node(1,0));
    final String expected = "";

    performTest(r1, r2, expected);
  }

  public void testTheOnlyChildRemoved() {
    Node r1 = new Node(0,0, new Node(1,0));
    Node r2 = new Node(0,0);
    String expected = "DELETED from 0: 1";

    performTest(r1, r2, expected);
  }

  public void testTheOnlyChildAdded() {
    Node r1 = new Node(0,0);
    Node r2 = new Node(0,0, new Node(1,0));
    String expected = "INSERTED to 0: 1 at 0";

    performTest(r1, r2, expected);

  }

  public void testTheOnlyChildReplaced() {
    Node r1 = new Node(0,0, new Node(1,0));
    Node r2 = new Node(0,0, new Node(2,0));
    String expected = "REPLACED: 1 to 2";

    performTest(r1, r2, expected);
  }

  public void testInsertedIntoTheMiddle() {
    Node r1 = new Node(0,0, new Node(1,0, new Node(2,0), new Node(3,1)));
    Node r2 = new Node(0,0, new Node(1,0, new Node(2,0), new Node(4,1), new Node(3,2)));
    String expected = "INSERTED to 1: 4 at 1";

    performTest(r1, r2, expected);
  }

  public void testInsertedFirst() {
    Node r1 = new Node(0,0, new Node(1,0, new Node(2,0), new Node(4,1)));
    Node r2 = new Node(0,0, new Node(1,0, new Node(3,0), new Node(2,1), new Node(4,2)));
    String expected = "INSERTED to 1: 3 at 0";

    performTest(r1, r2, expected);
  }

  public void testInsertedLast() {
    Node r1 = new Node(0,0, new Node(1,0, new Node(2,0), new Node(3,1)));
    Node r2 = new Node(0,0, new Node(1,0, new Node(2,0), new Node(3,1), new Node(4,2)));
    String expected = "INSERTED to 1: 4 at 2";

    performTest(r1, r2, expected);
  }

  public void testInsertedTwoLast() {
    Node r1 = new Node(0,0, new Node(1,0, new Node(2,0), new Node(3,1)));
    Node r2 = new Node(0,0, new Node(1,0, new Node(2,0), new Node(3,1), new Node(4,2), new Node(5,3)));

    performTest(r1, r2, "INSERTED to 1: 4 at 2", "INSERTED to 1: 5 at 3");
  }

  public void testSubtreeAppears() {
    Node r1 = new Node(0,0, new Node(1,0, new Node(2,0), new Node(3,1), new Node(4,2)));
    Node r2 = new Node(0,0, new Node(1,0, new Node(2,0), new Node(3,1, new Node(6,1)), new Node(4,2)));

    performTest(r1, r2, "INSERTED to 3: 6 at 0");
  }

  public void testSubtreeChanges() {
    Node r1 = new Node(0,0, new Node(1,0, new Node(2,0), new Node(3,1, new Node(6,1)), new Node(4,2)));
    Node r2 = new Node(0,0, new Node(1,0, new Node(2,0), new Node(5,1, new Node(6,1)), new Node(4,2)));

    performTest(r1, r2, "REPLACED: 3 to 5");
  }

  private static void performTest(final Node r1, final Node r2, final String... expected) {
    final DiffBuilder result = new DiffBuilder();
    DiffTree.diff(new TreeStructure(r1), new TreeStructure(r2), new NodeComparator(), result, r1.toString());

    final List<String> expectedList = Arrays.asList(expected);
    final List<String> actual = result.getEvents();
    if (!expectedList.isEmpty() && !actual.isEmpty()) {
      assertEquals(expectedList, actual);
    }
  }
}
