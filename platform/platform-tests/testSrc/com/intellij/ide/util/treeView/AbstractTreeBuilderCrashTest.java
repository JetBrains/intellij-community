package com.intellij.ide.util.treeView;

import com.intellij.openapi.util.Disposer;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.util.ArrayList;

public abstract class AbstractTreeBuilderCrashTest extends BaseTreeTestCase  {

  private AbstractTreeStructure myStructure;
  private DefaultTreeModel myTreeModel;
  private CachedNode myRoot;

  protected AbstractTreeBuilderCrashTest(boolean yeild, boolean bg) {
    super(yeild, bg);
  }

  public void testElementMovedButNodeIsStillInStructure() throws Exception {
    assertNodeMove(() -> {
      getBuilder().addSubtreeToUpdateByElement(myRoot.getChild("folder1"));
      getBuilder().addSubtreeToUpdateByElement(myRoot.getChild("folder2"));
    });
  }

  public void testElementMovedButNodeIsStillInStructure2() throws Exception {
    assertNodeMove(() -> {
      getBuilder().addSubtreeToUpdateByElement(myRoot.getChild("folder2"));
      getBuilder().addSubtreeToUpdateByElement(myRoot.getChild("folder1"));
    });
  }

  private void assertNodeMove(final Runnable update) throws Exception {
    myRoot = new CachedNode("root");
    final CachedNode folder1 = myRoot.addChild("folder1");
    final CachedNode file = folder1.addChild("file");
    final CachedNode folder2 = myRoot.addChild("folder2");

    initTree(myRoot);

    getBuilder().updateFromRoot();
    getBuilder().select(file, null);

    assertTree(
        "-root\n" +
        " -folder1\n" +
        "  [file]\n" +
        " folder2\n");


    folder1.myChildren.remove(file);
    folder2.addChild("file");

    update.run();

    assertTree(
        "-root\n" +
        " folder1\n" +
        " -folder2\n" +
        "  [file]\n");

  }

  public void testNewUniqueChilden() throws Exception {
    final boolean[] changes = new boolean[1];

    final Node root = new Node("root", null) {
      @Override
      Node[] getChildren() {
        if (changes[0]) {
          return new Node[] {new Node("node1", this, "node1id", "changedNode1") {
            @Override
            Node[] getChildren() {
              return new Node[] {new Node("newNode11", this, "node11id"), new Node("node13", this, "node13id")};
            }
          }, new Node("node2", this, "node2id")};
        } else {
          return new Node[] {new Node("node1", this, "node1id") {
            @Override
            Node[] getChildren() {
              return new Node[] {new Node("node11", this, "node11id"), new Node("node12", this, "node12id")};
            }
          }, new Node("node2", this, "node2id")};
        }
      }
    };

    initTree(root);

    updateFromRoot();

    assertTree("-root\n" +
               " -node1\n" +
               "  node11\n" +
               "  node12\n" +
               " node2\n");

    collapsePath(new TreePath(myTreeModel.getRoot()));
    getBuilder().expand(root, null);
    assertTree("-root\n" +
               " -node1\n" +
               "  node11\n" +
               "  node12\n" +
               " node2\n");


    updateFromRoot();
    
    assertTree("-root\n" +
               " -node1\n" +
               "  node11\n" +
               "  node12\n" +
               " node2\n");

    changes[0] = true;
    updateFromRoot();

    assertTree("-root\n" +
               " -node1\n" +
               "  newNode11\n" +
               "  node13\n" +
               " node2\n");
  }

  private void initTree(final Node root) throws Exception {
    myStructure = new BaseStructure() {
      @Override
      public Object getRootElement() {
        return root;
      }

      @Override
      public Object[] doGetChildElements(final Object element) {
        return ((Node)element).getChildren();
      }

      @Override
      public Object getParentElement(final Object element) {
        return ((Node)element).getParent();
      }

      @Override
      @NotNull
      public NodeDescriptor doCreateDescriptor(final Object element, final NodeDescriptor parentDescriptor) {
        return (NodeDescriptor)element;
      }
    };


    myTreeModel = new DefaultTreeModel(new DefaultMutableTreeNode());
    myTree = new Tree(myTreeModel);
    myTree.setRootVisible(false);

    initBuilder(new BaseTreeBuilder(myTree, myTreeModel, myStructure, AlphaComparator.INSTANCE) {
      @Override
      protected boolean isAutoExpandNode(final NodeDescriptor nodeDescriptor) {
        return true;
      }

      @Override
      protected boolean isDisposeOnCollapsing(final NodeDescriptor nodeDescriptor) {
        return false;
      }
    });

    Disposer.register(getRootDisposable(), getBuilder());

    showTree();
  }

  class Node extends NodeDescriptor {
    Node myParent;
    String myId;
    String myEqualityString;
    String myComment;

    Node(String id, final Node parent) {
      this(id, parent, id, null);
    }

    Node(String id, final Node parent, String equalityString) {
      this(id, parent, equalityString, null);
    }

    Node(String id, final Node parent, String equalityString, String comment) {
      super(null, parent);
      myParent = parent;
      myId = id;
      myEqualityString = equalityString;
      myComment = comment;
    }

    Node getParent() {
      return myParent;
    }

    Node[] getChildren() {
      return new Node[0];
    }

    @Override
    public boolean update() {
      return false;
    }

    @Override
    public Object getElement() {
      return this;
    }

    @Override
    public String toString() {
      return myId;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!(o instanceof Node)) return false;

      final Node node = (Node)o;

      if (!myEqualityString.equals(node.myEqualityString)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myId.hashCode();
    }
  }

  class CachedNode extends Node {
    private final ArrayList<CachedNode> myChildren = new ArrayList<>();

    CachedNode(final String id) {
      super(id, null);
    }

    CachedNode addChild(String id) {
      final CachedNode node = new CachedNode(id);
      myChildren.add(node);
      node.myParent = this;
      return node;
    }

    CachedNode getChild(String id) {
      for (CachedNode each : myChildren) {
        if (id.equals(each.myId)) return each;
      }

      return null;
    }

    @Override
    final Node[] getChildren() {
      return myChildren.toArray(new Node[myChildren.size()]);
    }
  }

  public abstract static class NoYieldNoBackground extends AbstractTreeBuilderCrashTest {
    public NoYieldNoBackground() {
      super(false, false);
    }
  }

}
