package com.intellij.ide.util.treeView;

import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.Condition;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Time;
import com.intellij.util.WaitFor;
import com.intellij.util.containers.HashMap;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.*;

abstract class AbstractTreeBuilderTest extends BaseTreeTestCase<BaseTreeTestCase.NodeElement> {
  protected MyStructure myStructure;

  Node myRoot;
  DefaultTreeModel myTreeModel;
  Node myCom;
  Node myOpenApi;
  Node myIde;
  Node myRunner;
  Node myRcp;


  boolean myEnsureSelection = false;

  AbstractTreeBuilderTest.Node myFabrique;


  Map<NodeElement, ElementEntry> myElementUpdate = new TreeMap<NodeElement, ElementEntry>();
  ElementUpdateHook myElementUpdateHook;

  Map<String, Integer> mySortedParent = new TreeMap<String, Integer>();

  NodeDescriptor.NodeComparator.Delegate<NodeDescriptor> myComparator;
  Node myIntellij;

  protected AbstractTreeBuilderTest(boolean passthrougth) {
    super(passthrougth);
  }

  protected AbstractTreeBuilderTest(boolean yieldingUiBuild, boolean bgStructureBuilding) {
    super(yieldingUiBuild, bgStructureBuilding);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myComparator = new NodeDescriptor.NodeComparator.Delegate<NodeDescriptor>(new NodeDescriptor.NodeComparator<NodeDescriptor>() {
      public int compare(NodeDescriptor o1, NodeDescriptor o2) {
        return AlphaComparator.INSTANCE.compare(o1, o2);
      }
    });

    mySortedParent.clear();

    myTreeModel = new DefaultTreeModel(new DefaultMutableTreeNode(null));
    myTreeModel.addTreeModelListener(new TreeModelListener() {
      public void treeNodesChanged(TreeModelEvent e) {
        assertEdt();
      }

      public void treeNodesInserted(TreeModelEvent e) {
        assertEdt();
      }

      public void treeNodesRemoved(TreeModelEvent e) {
        assertEdt();
      }

      public void treeStructureChanged(TreeModelEvent e) {
        assertEdt();
      }
    });


    myTree = new Tree(myTreeModel);
    myStructure = new MyStructure();
    myRoot = new Node(null, "/");

    initBuilder(new MyBuilder());

    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        assertEdt();
      }
    });

    myTree.addTreeExpansionListener(new TreeExpansionListener() {
      public void treeExpanded(TreeExpansionEvent event) {
        assertEdt();
      }

      public void treeCollapsed(TreeExpansionEvent event) {
        assertEdt();
      }
    });
  }

  @Override
  protected void tearDown() throws Exception {
    myElementUpdate.clear();
    myElementUpdateHook = null;
    myStructure.setRevalidator(null);
    super.tearDown();
  }


  Node removeFromParentButKeepRef(NodeElement child) {
    NodeElement parent = (NodeElement)myStructure.getParentElement(child);
    AbstractTreeBuilderTest.Node node = myStructure.getNodeFor(parent).remove(child, false);
    Assert.assertEquals(parent, myStructure.getParentElement(child));
    Assert.assertFalse(Arrays.asList(myStructure._getChildElements(parent, false)).contains(child));

    return node;
  }

  void assertSorted(String expected) {
    Iterator<String> keys = mySortedParent.keySet().iterator();
    StringBuffer result = new StringBuffer();
    while (keys.hasNext()) {
      String each = keys.next();
      result.append(each);
      int count = mySortedParent.get(each);
      if (count > 1) {
        result.append(" (" + count + ")");
      }

      if (keys.hasNext()) {
        result.append("\n");
      }
    }

    Assert.assertEquals(expected + "\n", result + "\n");
    mySortedParent.clear();
  }

  private void addEntry(String value) {
    if (mySortedParent.containsKey(value)) {
      mySortedParent.put(value, mySortedParent.get(value) + 1);
    } else {
      mySortedParent.put(value, 0);
    }
  }

  void assertUpdates(String expected) {
    List<Object> entries = Arrays.asList(myElementUpdate.values().toArray());
    Assert.assertEquals(expected + "\n", PlatformTestUtil.print(entries) + "\n");
    myElementUpdate.clear();
  }

  public void _testNoUpdateIfHidden() throws Exception {
    final ArrayList processedNodes = new ArrayList();
    final boolean[] toHide = new boolean[] {false};

    myElementUpdateHook = new ElementUpdateHook() {
      public void onElementAction(String action, Object element) {
        if (toHide[0]) {
          if ("jetbrains".equals(element.toString())) {
            getBuilder().getUi().deactivate();
          }
        }
      }
    };
    buildStructure(myRoot);

    waitBuilderToCome();
    processedNodes.clear();


    buildNode("/", false);
    assertTree(
      "-/\n" +
      " +com\n" +
      " +jetbrains\n" +
      " +org\n" +
      " +xunit\n");
    Assert.assertEquals("/\n" +
                 "com\n" +
                 "jetbrains\n" +
                 "xunit\n" +
                 "org\n" +
                 "intellij\n" +
                 "fabrique\n" +
                 "runner\n" + 
                 "eclipse", PlatformTestUtil.print(processedNodes));


    processedNodes.clear();
    toHide[0] = true;
    updateFromRoot();

    Assert.assertEquals("/\n" +
                 "com\n" +
                 "intellij\n" +
                 "jetbrains", PlatformTestUtil.print(processedNodes));

    processedNodes.clear();
    toHide[0] = false;


    showTree();

    assertTree(
      "-/\n" +
      " +com\n" +
      " +jetbrains\n" +
      " +org\n" +
      " +xunit\n");
    Assert.assertEquals("/\n" +
                 "com\n" +
                 "intellij\n" +
                 "jetbrains\n" +
                 "fabrique\n" +
                 "org\n" +
                 "eclipse\n" +
                 "xunit\n" +
                 "runner", PlatformTestUtil.print(processedNodes));

  }


  void buildStructure(final Node root) throws Exception {
    doAndWaitForBuilder(new Runnable() {
      public void run() {
        myCom = root.addChild("com");
        myIntellij = myCom.addChild("intellij");
        myOpenApi = myIntellij.addChild("openapi");
        myFabrique = root.addChild("jetbrains").addChild("fabrique");
        myIde = myFabrique.addChild("ide");
        myRunner = root.addChild("xunit").addChild("runner");
        myRcp = root.addChild("org").addChild("eclipse").addChild("rcp");

        getBuilder().getUi().activate(true);
      }
    });
  }

  protected void activate() throws Exception {
    doAndWaitForBuilder(new Runnable() {
      public void run() {
        getBuilder().getUi().activate(true);
      }
    });
  }


  void hideTree() throws Exception {
    Assert.assertFalse(getMyBuilder().myWasCleanedUp);

    invokeLaterIfNeeded(new Runnable() {
      public void run() {
        getBuilder().getUi().deactivate();
      }
    });

    final WaitFor waitFor = new WaitFor() {
      protected boolean condition() {
        return getMyBuilder().myWasCleanedUp || myCancelRequest != null;
      }
    };

    if (myCancelRequest != null) {
      throw new Exception(myCancelRequest);
    }

    waitFor.assertCompleted("Tree cleanup was not performed");

    Assert.assertTrue(getMyBuilder().myWasCleanedUp);
  }

  void buildNode(String elementText, boolean select) throws Exception {
    buildNode(new NodeElement(elementText), select);
  }

  void buildNode(Node node, boolean select) throws Exception {
    buildNode(node.myElement, select);
  }

  void buildNode(final NodeElement element, final boolean select) throws Exception {
    buildNode(element, select, true);
  }


  void buildNode(final NodeElement element, final boolean select, final boolean addToSelection) throws Exception {
    final boolean[] done = new boolean[] {false};

    doAndWaitForBuilder(new Runnable() {
      public void run() {
        if (select) {
          getBuilder().select(element, new Runnable() {
            public void run() {
              done[0] = true;
            }
          }, addToSelection);
        } else {
          getBuilder().expand(element, new Runnable() {
            public void run() {
              done[0] = true;
            }
          });
        }
      }
    }, new Condition() {
      public boolean value(Object o) {
        return done[0];
      }
    });

    Assert.assertNotNull(findNode(element, select));
  }


  DefaultMutableTreeNode findNode(String elementText, boolean shouldBeSelected) {
    return findNode(new NodeElement(elementText), shouldBeSelected);
  }

  DefaultMutableTreeNode findNode(NodeElement element, boolean shouldBeSelected) {
    return findNode((DefaultMutableTreeNode)myTree.getModel().getRoot(), element, shouldBeSelected);
  }

  private DefaultMutableTreeNode findNode(DefaultMutableTreeNode treeNode, NodeElement toFind, boolean shouldBeSelected) {
    final Object object = treeNode.getUserObject();
    Assert.assertNotNull(object);
    if (!(object instanceof NodeDescriptor)) return null;
    final NodeElement element = (NodeElement)((NodeDescriptor)object).getElement();
    if (toFind.equals(element)) return treeNode;

    for (int i = 0; i < treeNode.getChildCount(); i++) {
      final DefaultMutableTreeNode result = findNode((DefaultMutableTreeNode)treeNode.getChildAt(i), toFind, shouldBeSelected);
      if (result != null) {
        if (shouldBeSelected) {
          final TreePath path = new TreePath(result.getPath());
          Assert.assertTrue("Path should be selected: " + path, myTree.isPathSelected(path));
        }
        return result;
      }
    }

    return null;
  }


  class Node  {

    final NodeElement myElement;
    final ArrayList<Node> myChildElements = new ArrayList<Node>();

    Node(Node parent, String textName) {
      this(parent, new NodeElement(textName));
    }

    Node(Node parent, NodeElement name) {
      myElement = name;
      setParent(parent);
    }

    private void setParent(Node parent) {
      myStructure.register(parent != null ? parent.myElement : null, this);
    }

    public NodeElement getElement() {
      return myElement;
    }

    public Node addChild(Node node) {
      myChildElements.add(node);
      node.setParent(this);
      return node;
    }

    public Node addChild(String name) {
      final Node node = new Node(this, name);
      myChildElements.add(node);
      return node;
    }

    public Node addChild(NodeElement element) {
      final Node node = new Node(this, element);
      myChildElements.add(node);
      return node;
    }

    @Override
    public String toString() {
      return myElement.toString();
    }

    public void removeAll() {
      myChildElements.clear();
    }

    public Node getChildNode(String name) {
      for (Node each : myChildElements) {
        if (name.equals(each.myElement.myName)) return each;
      }

      return null;
    }

    public Object[] getChildElements() {
      Object[] elements = new Object[myChildElements.size()];
      for (int i = 0; i < myChildElements.size(); i++) {
        elements[i] = myChildElements.get(i).myElement;
      }
      return elements;
    }

    public void delete() {
      final NodeElement parent = (NodeElement)myStructure.getParentElement(myElement);
      Assert.assertNotNull(myElement.toString(), parent);

      myStructure.getNodeFor(parent).remove(myElement, true);
    }

    private Node remove(final NodeElement name, boolean removeRefToParent) {
      final Iterator<Node> kids = myChildElements.iterator();
      Node removed = null;
      while (kids.hasNext()) {
        Node each = kids.next();
        if (name.equals(each.myElement)) {
          kids.remove();
          removed = each;
          break;
        }
      }

      if (removeRefToParent) {
        myStructure.myChild2Parent.remove(name);
      }

      return removed;
    }
  }

  class MyStructure extends BaseStructure {

    private final Map<NodeElement, NodeElement> myChild2Parent = new HashMap<NodeElement, NodeElement>();
    private final Map<NodeElement, Node> myElement2Node = new HashMap<NodeElement, Node>();
    private final Set<NodeElement> myLeaves = new HashSet<NodeElement>();
    private Revalidator myRevalidator;

    public Object getRootElement() {
      return myRoot.myElement;
    }

    public void reinitRoot(Node root) {
      myRoot = root;
      myElement2Node.clear();
      myLeaves.clear();
      myElement2Node.put(root.myElement, root);
    }

    @Override
    public Object[] doGetChildElements(Object element) {

      onElementAction("getChildren", (NodeElement)element);

      final AbstractTreeBuilderTest.Node node = myElement2Node.get((NodeElement)element);
      return node.getChildElements();
    }

    private Node getNodeFor(Object element) {
      return myElement2Node.get(element);
    }

    public Object getParentElement(final Object element) {
      NodeElement nodeElement = (NodeElement)element;
      return nodeElement.getForcedParent() != null ? nodeElement.getForcedParent() : myChild2Parent.get(nodeElement);
    }


    @Override
    public boolean isAlwaysLeaf(Object element) {
      return myLeaves.contains(element);
    }

    public void addLeaf(NodeElement element) {
      myLeaves.add(element);
    }

    public void removeLeaf(NodeElement element) {
      myLeaves.remove(element);
    }

    @NotNull
      public NodeDescriptor doCreateDescriptor(final Object element, final NodeDescriptor parentDescriptor) {
      return new NodeDescriptor(null, parentDescriptor) {
        public boolean update() {
          onElementAction("update", (NodeElement)element);
          return true;
        }

        public Object getElement() {
          return element;
        }

        @Override
        public String toString() {
          return getElement().toString();
        }
      };
    }

    public void register(final NodeElement parent, final Node child) {
      myChild2Parent.put(child.myElement, parent);
      myElement2Node.put(child.myElement, child);
    }

    public void clear() {
      myChild2Parent.clear();
      myElement2Node.clear();
      myElement2Node.put(myRoot.myElement, myRoot);
    }

    public Node getNodeFor(NodeElement element) {
      return myElement2Node.get(element);
    }

    @Override
    public AsyncResult<Object> revalidateElement(Object element) {
      return myRevalidator != null ? myRevalidator.revalidate((NodeElement)element) : super.revalidateElement(element);
    }

    public void setRevalidator(Revalidator revalidator) {
      myRevalidator = revalidator;
    }
  }

  interface Revalidator {
    AsyncResult<Object> revalidate(NodeElement element);
  }

  class MyBuilder extends BaseTreeBuilder {

    public MyBuilder() {
      super(AbstractTreeBuilderTest.this.myTree, AbstractTreeBuilderTest.this.myTreeModel, AbstractTreeBuilderTest.this.myStructure, myComparator,
            false);

      initRootNode();

      getUi().setJantorPollPeriod(Time.SECOND * 2);
    }


    @Override
    protected void sortChildren(Comparator<TreeNode> nodeComparator, DefaultMutableTreeNode node, ArrayList<TreeNode> children) {
      super.sortChildren(nodeComparator, node, children);
      addEntry(node.toString());
    }

    @Override
    public boolean isToEnsureSelectionOnFocusGained() {
      return myEnsureSelection;
    }
  }

  private void onElementAction(String action, NodeElement element) {
    ElementEntry entry = myElementUpdate.get(element);
    if (entry == null) {
      entry = new ElementEntry(element);
      myElementUpdate.put(element, entry);
    }
    entry.onElementAction(action);

    if (myElementUpdateHook != null) {
      myElementUpdateHook.onElementAction(action, element);
    }
  }

  static interface ElementUpdateHook {
    void onElementAction(String action, Object element);
  }

  private class ElementEntry {

    NodeElement myElement;

    int myUpdateCount;
    int myGetChildrenCount;

    private ElementEntry(NodeElement element) {
      myElement = element;
    }

    void onElementAction(String action) {
      try {
        if ("update".equals(action)) {
          myUpdateCount++;
        } else if ("getChildren".equals(action)) {
          Assert.assertTrue("getChildren() is called before update(), node=" + myElement, myUpdateCount > 0);
          myGetChildrenCount++;
        }
      }
      catch (Throwable e) {
        myCancelRequest = e;
      }
    }

    @Override
    public String toString() {
      return (myElement + ": " + asString(myUpdateCount, "update") + " " + asString(myGetChildrenCount, "getChildren")).trim();
    }

    private String asString(int count, String text) {
      if (count == 0) return "";
      return count > 1 ? text + " (" + count + ")" : text;
    }
  }

 
  
  MyBuilder getMyBuilder() {
    return (MyBuilder)getBuilder();
  }

  interface TreeAction {
    void run(Runnable onDone);
  }

  TreePath getPath(String s) {
    return new TreePath(findNode(s, false).getPath());
  }

}
  
