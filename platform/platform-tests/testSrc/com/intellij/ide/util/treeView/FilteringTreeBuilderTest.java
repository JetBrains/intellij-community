package com.intellij.ide.util.treeView;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.TreeUIHelper;
import com.intellij.ui.speedSearch.ElementFilter;
import com.intellij.ui.treeStructure.*;
import com.intellij.ui.treeStructure.filtered.FilteringTreeBuilder;
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;

/**
 * @author Kirill Kalishev
 * @author Konstantin Bulenkov
 */
public class FilteringTreeBuilderTest extends BaseTreeTestCase  {
  private FilteringTreeBuilder myBuilder;
  private MyFilter myFilter;
  private Node myRoot;
  private SimpleTreeStructure myStructure;

  public FilteringTreeBuilderTest() {
    super(false, false);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myTree = new SimpleTree() {
      @Override
      protected void configureUiHelper(final TreeUIHelper helper) {
      }
    };

    myFilter = new MyFilter();
    myRoot = new Node(null, "/");
    myStructure = new SimpleTreeStructure.Impl(myRoot);
  }

  private void initBuilder() throws Exception {
    myBuilder = new FilteringTreeBuilder(myTree, myFilter, myStructure, AlphaComparator.INSTANCE) {
      @Override
      protected AbstractTreeUpdater createUpdater() {
       return _createUpdater(this);
      }
    };

    showTree();

    Disposer.register(getRootDisposable(), myBuilder);
  }

  public void testFilter() throws Exception {
    myTree.setRootVisible(false);

    final Node f1 = myRoot.addChild("folder1");
    f1.addChild("file11");
    f1.addChild("file12");
    Node f11 = f1.addChild("folder11");
    f11.addChild("element111");
    myRoot.addChild("folder2").addChild("file21");

    initBuilder();

    assertTree("-/\n"
             + " -folder1\n"
             + "  file11\n"
             + "  file12\n"
             + "  -folder11\n"
             + "   element111\n"
             + " -folder2\n"
             + "  file21\n");

    update("", findNode("file11"));
    assertTree("-/\n"
             + " -folder1\n"
             + "  [file11]\n"
             + "  file12\n"
             + "  -folder11\n"
             + "   element111\n"
             + " -folder2\n"
             + "  file21\n");

    updateFilter("f");
    assertTree("-/\n"
             + " -folder1\n"
             + "  [file11]\n"
             + "  file12\n"
             + "  folder11\n"
             + " -folder2\n"
             + "  file21\n");

    updateFilter("fo");
    assertTree("-/\n"
             + " -folder1\n"
             + "  [folder11]\n"
             + " folder2\n");

    updateFilter("fo_");
    assertTree("+/\n");

    updateFilter("");
    assertTree("-/\n"
             + " -[folder1]\n"
             + "  file11\n"
             + "  file12\n"
             + "  -folder11\n"
             + "   element111\n"
             + " -folder2\n"
             + "  file21\n");


    select("element111");
    assertTree("-/\n"
             + " -folder1\n"
             + "  file11\n"
             + "  file12\n"
             + "  -folder11\n"
             + "   [element111]\n"
             + " -folder2\n"
             + "  file21\n");

    updateFilter("folder2");
    assertTree("-/\n"
             + " [folder2]\n");

    updateFilter("");
    assertTree("-/\n"
             + " -folder1\n"
             + "  file11\n"
             + "  file12\n"
             + "  -folder11\n"
             + "   element111\n"
             + " -[folder2]\n"
             + "  file21\n");

    updateFilter("file1");
    assertTree("-/\n"
             + " -[folder1]\n"
             + "  file11\n"
             + "  file12\n");

    select("file12");
    assertTree("-/\n"
             + " -folder1\n"
             + "  file11\n"
             + "  [file12]\n");

    updateFilter("");
    assertTree("-/\n"
             + " -folder1\n"
             + "  file11\n"
             + "  [file12]\n"
             + "  -folder11\n"
             + "   element111\n"
             + " -folder2\n"
             + "  file21\n");

  }

  private void select(String element) throws Exception {
    FilteringTreeStructure.FilteringNode node = myBuilder.getVisibleNodeFor(findNode(element));
    select(new Object[] {node}, false);
  }

  private void updateFilter(final String text) {
     update(text, null);
   }

  private void update(final String text, @Nullable final Object selection) {
    myFilter.update(text, selection);
  }

  private class Node extends CachingSimpleNode {

    private final LinkedHashMap<String, Node> myKids = new LinkedHashMap<>();

    private Node(final SimpleNode aParent, String name) {
      super(aParent);
      myName = name;
    }

    public Node addChild(String name) {
      if (!myKids.containsKey(name)) {
        myKids.put(name, new Node(this, name));
      }

      return myKids.get(name);
    }

    @Override
    protected void doUpdate() {
      setPlainText(myName);
    }

    @Override
    protected SimpleNode[] buildChildren() {
      return myKids.isEmpty() ? NO_CHILDREN : myKids.values().toArray(new Node[myKids.size()]);
    }

    @Override
    public String toString() {
      return myName;
    }

    @Override
    protected void updateFileStatus() {
      
    }
  }

  private Object findNode(final String name) {
    final Ref<Object> node = new Ref<>();
    ((SimpleTree)myTree).accept(myBuilder, new SimpleNodeVisitor() {
      @Override
      public boolean accept(final SimpleNode simpleNode) {
        if (name.equals(simpleNode.toString())) {
          node.set(myBuilder.getOriginalNode(simpleNode));
          return true;
        } else {
          return false;
        }
      }
    });

    return node.get();
  }


  private static class MyFilter extends ElementFilter.Active.Impl {

    String myPattern = "";

    @Override
    public boolean shouldBeShowing(final Object value) {
      return value.toString().startsWith(myPattern);
    }

    public ActionCallback update(final String pattern, Object selection) {
      myPattern = pattern;
      return fireUpdate(selection, true, false);
    }
  }

  @Override
  AbstractTreeBuilder getBuilder() {
    return myBuilder;
  }
}


