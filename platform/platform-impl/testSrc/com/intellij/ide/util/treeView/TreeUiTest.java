package com.intellij.ide.util.treeView;

import com.intellij.openapi.diagnostic.Log;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Progressive;
import com.intellij.openapi.util.*;
import com.intellij.util.Time;
import com.intellij.util.WaitFor;
import junit.framework.TestSuite;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.lang.reflect.InvocationTargetException;

public class TreeUiTest extends AbstractTreeBuilderTest {

  public TreeUiTest(boolean passthrougth) {
    super(passthrougth);
  }

  public TreeUiTest(boolean yieldingUiBuild, boolean bgStructureBuilding) {
    super(yieldingUiBuild, bgStructureBuilding);
  }

  public void testEmptyInvisibleRoot() throws Exception {
    myTree.setRootVisible(false);
    showTree();
    assertTree("+/\n");

    updateFromRoot();
    assertTree("+/\n");


    buildNode("/", false);
    assertTree("+/\n");

    myTree.setRootVisible(true);
    buildNode("/", false);
    assertTree("/\n");
  }

  public void testVisibleRoot() throws Exception {
    myTree.setRootVisible(true);
    buildStructure(myRoot);
    assertTree("+/\n");

    updateFromRoot();
    assertTree("+/\n");
  }

  public void testThrowingProcessCancelledInterruptsUpdate() throws Exception {
    assertInterruption(Interruption.throwProcessCancelled);
  }

  public void testCancelUpdate() throws Exception {
    assertInterruption(Interruption.invokeCancel);
  }


  public void testBatchUpdate() throws Exception {
    buildStructure(myRoot);

    myElementUpdate.clear();

    final NodeElement[] toExpand = new NodeElement[] {
      new NodeElement("com"),
      new NodeElement("jetbrains"),
      new NodeElement("org"),
      new NodeElement("xunit")
    };

    final ActionCallback done = new ActionCallback();
    final Ref<ProgressIndicator> indicatorRef = new Ref<ProgressIndicator>();
    invokeLaterIfNeeded(new Runnable() {
      public void run() {
        getBuilder().batch(new Progressive() {
          public void run(@NotNull ProgressIndicator indicator) {
            indicatorRef.set(indicator);
            expandNext(toExpand, 0, indicator, done);
          }
        }).notify(done);
      }
    });


    waitBuilderToCome(new Condition() {
      public boolean value(Object o) {
        return done.isProcessed();
      }
    });

    assertTrue(done.isDone());

    assertTree("-/\n" +
               " -com\n" +
               "  +intellij\n" +
               " -jetbrains\n" +
               "  +fabrique\n" +
               " -org\n" +
               "  +eclipse\n" +
               " -xunit\n" +
               "  runner\n");

    assertFalse(indicatorRef.get().isCanceled());
  }


  public void testCancelUpdateBatch() throws Exception {
    buildStructure(myRoot);

    myAlwaysShowPlus.add(new NodeElement("com"));
    myAlwaysShowPlus.add(new NodeElement("jetbrains"));
    myAlwaysShowPlus.add(new NodeElement("org"));
    myAlwaysShowPlus.add(new NodeElement("xunit"));

    final Ref<Boolean> cancelled = new Ref<Boolean>(false);
    myElementUpdateHook = new ElementUpdateHook() {
      public void onElementAction(String action, Object element) {
        NodeElement stopElement = new NodeElement("com");

        if (cancelled.get()) {
          myCancelRequest = new AssertionError("Not supposed to update after element=" + stopElement);
          return;
        }

        if (element.equals(stopElement) && action.equals("getChildren")) {
          cancelled.set(true);
          getBuilder().cancelUpdate();
        }
      }
    };

    final NodeElement[] toExpand = new NodeElement[] {
      new NodeElement("com"),
      new NodeElement("jetbrains"),
      new NodeElement("org"),
      new NodeElement("xunit")
    };

    final ActionCallback done = new ActionCallback();
    final Ref<ProgressIndicator> indicatorRef = new Ref<ProgressIndicator>();

    invokeLaterIfNeeded(new Runnable() {
      public void run() {
        getBuilder().batch(new Progressive() {
          public void run(@NotNull ProgressIndicator indicator) {
            indicatorRef.set(indicator);
            expandNext(toExpand, 0, indicator, done);
          }
        }).notify(done);
      }
    });


    waitBuilderToCome(new Condition() {
      public boolean value(Object o) {
        return done.isProcessed() || myCancelRequest != null;
      }
    });


    assertNull(myCancelRequest);
    assertTrue(done.isRejected());
    assertTrue(indicatorRef.get().isCanceled());

    assertFalse(getBuilder().getUi().isCancelProcessed());
  }
  

  public void testExpandAll() throws Exception {
    buildStructure(myRoot);
    assertTree("+/\n");

    final Ref<Boolean> done = new Ref<Boolean>();
    doAndWaitForBuilder(new Runnable() {
      public void run() {
        getBuilder().expandAll(new Runnable() {
          public void run() {
            done.set(true);
          }
        });
      }
    });

    assertTree("-/\n"
               + " -com\n"
               + "  -intellij\n"
               + "   openapi\n"
               + " -jetbrains\n"
               + "  -fabrique\n"
               + "   ide\n"
               + " -org\n"
               + "  -eclipse\n"
               + "   rcp\n"
               + " -xunit\n"
               + "  runner\n");
  }

  public void testInvisibleRoot() throws Exception {
    myTree.setRootVisible(false);
    buildStructure(myRoot);
    assertTree("-/\n"
               + " +com\n"
               + " +jetbrains\n"
               + " +org\n"
               + " +xunit\n");

    collapsePath(new TreePath(myTreeModel.getRoot()));
    assertTree("+/\n");

    updateFromRoot();
    assertTree("-/\n"
               + " +com\n"
               + " +jetbrains\n"
               + " +org\n"
               + " +xunit\n");

    buildNode("com", true);
    assertTree("-/\n"
               + " +[com]\n"
               + " +jetbrains\n"
               + " +org\n"
               + " +xunit\n");

    myRoot.removeAll();
    updateFromRoot();

    assertTree("+/\n");

  }

  public void testAutoExpand() throws Exception {
    buildStructure(myRoot);
    assertTree("+/\n");

    myAutoExpand.add(new NodeElement("/"));
    buildStructure(myRoot);

    assertTree("-/\n"
               + " +com\n"
               + " +jetbrains\n"
               + " +org\n"
               + " +xunit\n");


    myAutoExpand.add(new NodeElement("jetbrains"));
    updateFromRoot();

    assertTree("-/\n"
               + " +com\n"
               + " -jetbrains\n"
               + "  +fabrique\n"
               + " +org\n"
               + " +xunit\n");

    collapsePath(getPath("jetbrains"));
    assertTree("-/\n"
               + " +com\n"
               + " +jetbrains\n"
               + " +org\n"
               + " +xunit\n");

    updateFrom(new NodeElement("org"));
    assertTree("-/\n"
               + " +com\n"
               + " +jetbrains\n"
               + " +org\n"
               + " +xunit\n");

    updateFrom(new NodeElement("jetbrains"));
    assertTree("-/\n"
               + " +com\n"
               + " -jetbrains\n"
               + "  +fabrique\n"
               + " +org\n"
               + " +xunit\n");
  }

  public void testAutoExpandDeep() throws Exception {
    myTree.setRootVisible(false);
    //myAutoExpand.add(new NodeElement("jetbrains"));
    myAutoExpand.add(new NodeElement("fabrique"));


    buildStructure(myRoot);
    //assertTree("+/\n");

    expand(getPath("/"));
    expand(getPath("jetbrains"));
    assertTree("-/\n"
               + " +com\n"
               + " -jetbrains\n"
               + "  -fabrique\n"
               + "   ide\n"
               + " +org\n"
               + " +xunit\n");

    collapsePath(getPath("/"));
    assertTree("+/\n");

    expand(getPath("/"));
    expand(getPath("jetbrains"));

    assertTree("-/\n"
               + " +com\n"
               + " -jetbrains\n"
               + "  -fabrique\n"
               + "   ide\n"
               + " +org\n"
               + " +xunit\n");

    collapsePath(getPath("jetbrains"));
    assertTree("-/\n"
               + " +com\n"
               + " +jetbrains\n"
               + " +org\n"
               + " +xunit\n");

    expand(getPath("jetbrains"));
    assertTree("-/\n"
               + " +com\n"
               + " -jetbrains\n"
               + "  -fabrique\n"
               + "   ide\n"
               + " +org\n"
               + " +xunit\n");

  }


  public void testAutoExpandInNonVisibleNode() throws Exception {
    myAutoExpand.add(new NodeElement("fabrique"));
    buildStructure(myRoot);

    expand(getPath("/"));
    assertTree("-/\n"
               + " +com\n"
               + " +jetbrains\n"
               + " +org\n"
               + " +xunit\n");
  }

  public void testSmartExpand() throws Exception {
    mySmartExpand = true;
    buildStructure(myRoot);
    assertTree("+/\n");

    expand(getPath("/"));
    assertTree("-/\n"
               + " +com\n"
               + " +jetbrains\n"
               + " +org\n"
               + " +xunit\n");

    expand(getPath("jetbrains"));
    assertTree("-/\n"
               + " +com\n"
               + " -jetbrains\n"
               + "  -fabrique\n"
               + "   ide\n"
               + " +org\n"
               + " +xunit\n");

    collapsePath(getPath("jetbrains"));
    assertTree("-/\n"
               + " +com\n"
               + " +jetbrains\n"
               + " +org\n"
               + " +xunit\n");

    updateFromRoot();
    assertTree("-/\n"
               + " +com\n"
               + " +jetbrains\n"
               + " +org\n"
               + " +xunit\n");

    mySmartExpand = false;
    collapsePath(getPath("jetbrains"));
    assertTree("-/\n"
               + " +com\n"
               + " +jetbrains\n"
               + " +org\n"
               + " +xunit\n");

    expand(getPath("jetbrains"));
    assertTree("-/\n"
               + " +com\n"
               + " -jetbrains\n"
               + "  +fabrique\n"
               + " +org\n"
               + " +xunit\n");
  }


  public void testClear() throws Exception {
    getBuilder().getUi().setClearOnHideDelay(10 * Time.SECOND);

    buildStructure(myRoot);

    assertTree("+/\n");

    final DefaultMutableTreeNode openApiNode = findNode("openapi", false);
    final DefaultMutableTreeNode ideNode = findNode("ide", false);
    final DefaultMutableTreeNode runnerNode = findNode("runner", false);
    final DefaultMutableTreeNode rcpNode = findNode("rcp", false);

    assertNull(openApiNode);
    assertNull(ideNode);
    assertNull(runnerNode);
    assertNull(rcpNode);

    buildNode(myOpenApi, true);
    buildNode(myIde, true);
    buildNode(myRunner, false);

    hideTree();

    assertNull(findNode("openapi", true));
    assertNull(findNode("ide", true));
    assertNull(findNode("runner", false));
    assertNull(findNode("rcp", false));


    showTree();

    assertTree("-/\n" +
               " -com\n" +
               "  -intellij\n" +
               "   [openapi]\n" +
               " -jetbrains\n" +
               "  -fabrique\n" +
               "   [ide]\n" +
               " +org\n" +
               " -xunit\n" +
               "  runner\n");

    getMyBuilder().myWasCleanedUp = false;
    hideTree();
    showTree();

    assertTree("-/\n" +
               " -com\n" +
               "  -intellij\n" +
               "   [openapi]\n" +
               " -jetbrains\n" +
               "  -fabrique\n" +
               "   [ide]\n" +
               " +org\n" +
               " -xunit\n" +
               "  runner\n");


    buildNode(myFabrique.myElement, true, false);
    assertTree("-/\n" +
               " -com\n" +
               "  -intellij\n" +
               "   openapi\n" +
               " -jetbrains\n" +
               "  -[fabrique]\n" +
               "   ide\n" +
               " +org\n" +
               " -xunit\n" +
               "  runner\n");
  }

  public void testUpdateRestoresState() throws Exception {
    buildStructure(myRoot);

    buildNode(myOpenApi, true);
    buildNode(myIde, true);
    buildNode(myRunner, false);

    waitBuilderToCome();

    assertTree("-/\n" +
               " -com\n" +
               "  -intellij\n" +
               "   [openapi]\n" +
               " -jetbrains\n" +
               "  -fabrique\n" +
               "   [ide]\n" +
               " +org\n" +
               " -xunit\n" +
               "  runner\n");

    myRoot.removeAll();
    myStructure.clear();

    final AbstractTreeBuilderTest.Node newRoot = myRoot.addChild("newRoot");

    buildStructure(newRoot);

    updateFromRoot();
    assertTree("-/\n" +
               " -newRoot\n" +
               "  -com\n" +
               "   -intellij\n" +
               "    [openapi]\n" +
               "  -jetbrains\n" +
               "   -fabrique\n" +
               "    [ide]\n" +
               "  +org\n" +
               "  -xunit\n" +
               "   runner\n");
  }


  public void testSelect() throws Exception {
    buildStructure(myRoot);
    assertTree(
      "+/\n");


    buildNode(myOpenApi, true);
    assertTree(
      "-/\n" +
      " -com\n" +
      "  -intellij\n" +
      "   [openapi]\n" +
      " +jetbrains\n" +
      " +org\n" +
      " +xunit\n");

    buildNode("fabrique", true);

    assertTree(
      "-/\n" +
      " -com\n" +
      "  -intellij\n" +
      "   [openapi]\n" +
      " -jetbrains\n" +
      "  +[fabrique]\n" +
      " +org\n" +
      " +xunit\n");
  }

  public void testCallbackOnceOnSelect() throws Exception {
    buildStructure(myRoot);

    assertCallbackOnce(new TreeAction() {
      public void run(Runnable onDone) {
        getMyBuilder().select(new Object[] {new NodeElement("intellij"), new NodeElement("fabrique")}, onDone);
      }
    });

    assertTree(
      "-/\n" +
      " -com\n" +
      "  +[intellij]\n" +
      " -jetbrains\n" +
      "  +[fabrique]\n" +
      " +org\n" +
      " +xunit\n");

  }

  public void testCallbackOnceOnExpand() throws Exception {
    buildStructure(myRoot);

    assertCallbackOnce(new TreeAction() {
      public void run(Runnable onDone) {
        getMyBuilder().expand(new Object[] {new NodeElement("intellij"), new NodeElement("fabrique")}, onDone);
      }
    });

    assertTree(
      "-/\n" +
      " -com\n" +
      "  -intellij\n" +
      "   openapi\n" +
      " -jetbrains\n" +
      "  -fabrique\n" +
      "   ide\n" +
      " +org\n" +
      " +xunit\n");

  }


  public void testNoInfiniteAutoExpand() throws Exception {
    mySmartExpand = false;

    assertNoInfiniteAutoExpand(new Runnable() {
      public void run() {
        myAutoExpand.add(new NodeElement("level2"));
        myAutoExpand.add(new NodeElement("level3"));
        myAutoExpand.add(new NodeElement("level4"));
        myAutoExpand.add(new NodeElement("level5"));
        myAutoExpand.add(new NodeElement("level6"));
        myAutoExpand.add(new NodeElement("level7"));
        myAutoExpand.add(new NodeElement("level8"));
        myAutoExpand.add(new NodeElement("level9"));
        myAutoExpand.add(new NodeElement("level10"));
        myAutoExpand.add(new NodeElement("level11"));
        myAutoExpand.add(new NodeElement("level12"));
        myAutoExpand.add(new NodeElement("level13"));
        myAutoExpand.add(new NodeElement("level14"));
        myAutoExpand.add(new NodeElement("level15"));
      }
    });
  }

  public void testNoInfiniteSmartExpand() throws Exception {
    mySmartExpand = false;

    assertNoInfiniteAutoExpand(new Runnable() {
      public void run() {
        mySmartExpand = true;
      }
    });
  }

  private void assertNoInfiniteAutoExpand(final Runnable enableExpand) throws Exception {
    class Level extends Node {

      int myLevel;

      Level(Node parent, int level) {
        super(parent, "level" + level);
        myLevel = level;
      }

      @Override
      public Object[] getChildElements() {
        if (super.getChildElements().length == 0) {
          addChild(new Level(this, myLevel + 1));
        }

        return super.getChildElements();
      }
    }

    myRoot.addChild(new Level(myRoot, 0));

    activate();
    buildNode("level0", false);

    assertTree("-/\n" +
               " -level0\n" +
               "  +level1\n");

    enableExpand.run();

    expand(getPath("level1"));

    assertTree("-/\n" +
               " -level0\n" +
               "  -level1\n" +
               "   -level2\n" +
               "    -level3\n" +
               "     -level4\n" +
               "      -level5\n" +
               "       +level6\n");

    expand(getPath("level6"));
    assertTree("-/\n" +
               " -level0\n" +
               "  -level1\n" +
               "   -level2\n" +
               "    -level3\n" +
               "     -level4\n" +
               "      -level5\n" +
               "       -level6\n" +
               "        -level7\n" +
               "         -level8\n" +
               "          -level9\n" +
               "           -level10\n" + 
               "            +level11\n");
  }

  private void assertCallbackOnce(final TreeAction action) {
    final int[] notifyCount = new int[1];
    final boolean[] done = new boolean[1];
    invokeLaterIfNeeded(new Runnable() {
      public void run() {
        action.run(new Runnable() {
          public void run() {
            notifyCount[0]++;
            done[0] = true;
          }
        });
      }
    });

    new WaitFor(60000) {
      @Override
      protected boolean condition() {
        return done[0] && getMyBuilder().getUi().isReady();
      }
    };

    assertTrue(done[0]);
    assertEquals(1, notifyCount[0]);
  }

  public void testSelectMultiple() throws Exception {
    buildStructure(myRoot);
    assertTree(
      "+/\n");

    select(new Object[] {new NodeElement("openapi"), new NodeElement("fabrique")}, false);
    assertTree(
      "-/\n" +
      " -com\n" +
      "  -intellij\n" +
      "   [openapi]\n" +
      " -jetbrains\n" +
      "  +[fabrique]\n" +
      " +org\n" +
      " +xunit\n");
  }

  public void testUnsuccessfulSelect() throws Exception {
    buildStructure(myRoot);
    select(new Object[] {new NodeElement("openapi"), new NodeElement("fabrique")}, false);

    assertTree(
      "-/\n" +
      " -com\n" +
      "  -intellij\n" +
      "   [openapi]\n" +
      " -jetbrains\n" +
      "  +[fabrique]\n" +
      " +org\n" +
      " +xunit\n");

    select(new Object[] {new NodeElement("whatever1"), new NodeElement("whatever2")}, false);

    assertTree(
      "-/\n" +
      " -com\n" +
      "  -intellij\n" +
      "   [openapi]\n" +
      " -jetbrains\n" +
      "  +[fabrique]\n" +
      " +org\n" +
      " +xunit\n");
  }


  public void testSelectionWhenChildMoved() throws Exception {
    buildStructure(myRoot);
    assertTree("+/\n");

    final Node refactoring = myCom.getChildNode("intellij").addChild("refactoring");

    buildNode("refactoring", true);

    assertTree(
      "-/\n" +
      " -com\n" +
      "  -intellij\n" +
      "   openapi\n" +
      "   [refactoring]\n" +
      " +jetbrains\n" +
      " +org\n" +
      " +xunit\n");

    refactoring.delete();
    myCom.getChildNode("intellij").getChildNode("openapi").addChild("refactoring");

    updateFromRoot();

    assertTree(
      "-/\n" +
      " -com\n" +
      "  -intellij\n" +
      "   -openapi\n" +
      "    [refactoring]\n" +
      " +jetbrains\n" +
      " +org\n" +
      " +xunit\n");
  }


  public void testSelectionGoesToParentWhenOnlyChildRemove() throws Exception {
    buildStructure(myRoot);
    buildNode("openapi", true);

    assertTree(
      "-/\n" +
      " -com\n" +
      "  -intellij\n" +
      "   [openapi]\n" +
      " +jetbrains\n" +
      " +org\n" +
      " +xunit\n");

    myCom.getChildNode("intellij").getChildNode("openapi").delete();

    updateFromRoot();

    assertTree(
      "-/\n" +
      " -com\n" +
      "  [intellij]\n" +
      " +jetbrains\n" +
      " +org\n" +
      " +xunit\n");
  }

  public void testCollapsedPathOnExpandedCallback() throws Exception {
    Node com = myRoot.addChild("com");

    activate();
    assertTree("+/\n");

    expand(getPath("/"));
    assertTree("-/\n" +
               " com\n");

    com.addChild("intellij");

    collapsePath(getPath("/"));

    final Ref<Boolean> done = new Ref<Boolean>();
    invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        getBuilder().expand(new NodeElement("com"), new Runnable() {
          @Override
          public void run() {
            getBuilder().getTree().collapsePath(getPath("com"));
            done.set(Boolean.TRUE);
          }
        });
      }
    });

    waitBuilderToCome(new Condition<Object>() {
      @Override
      public boolean value(Object o) {
        return (done.get() != null) && done.get().booleanValue();
      }
    });

    assertTree("-/\n" +
               " +com\n");
  }

  public void testSelectionGoesToParentWhenOnlyChildMoved() throws Exception {
    buildStructure(myRoot);
    buildNode("openapi", true);

    assertTree(
      "-/\n" +
      " -com\n" +
      "  -intellij\n" +
      "   [openapi]\n" +
      " +jetbrains\n" +
      " +org\n" +
      " +xunit\n");

    myCom.getChildNode("intellij").getChildNode("openapi").delete();
    myRoot.getChildNode("xunit").addChild("openapi");

    updateFromRoot();

    assertTree(
      "-/\n" +
      " -com\n" +
      "  intellij\n" +
      " +jetbrains\n" +
      " +org\n" +
      " -xunit\n" +
      "  [openapi]\n" +
      "  runner\n");
  }

  public void testSelectionGoesToParentWhenOnlyChildMoved2() throws Exception {
    buildStructure(myRoot);
    buildNode("openapi", true);

    assertTree(
      "-/\n" +
      " -com\n" +
      "  -intellij\n" +
      "   [openapi]\n" +
      " +jetbrains\n" +
      " +org\n" +
      " +xunit\n");

    myCom.getChildNode("intellij").getChildNode("openapi").delete();
    myRoot.getChildNode("xunit").addChild("openapi");

    getBuilder().addSubtreeToUpdateByElement(new NodeElement("intellij"));
    getBuilder().addSubtreeToUpdateByElement(new NodeElement("xunit"));


    doAndWaitForBuilder(new Runnable() {
      public void run() {
        getBuilder().getUpdater().performUpdate();
      }
    });

    assertTree(
      "-/\n" +
      " -com\n" +
      "  intellij\n" +
      " +jetbrains\n" +
      " +org\n" +
      " -xunit\n" +
      "  [openapi]\n" +
      "  runner\n");
  }

  public void testSelectionGoesToParentWhenChildrenFold() throws Exception {
    buildStructure(myRoot);
    buildNode("openapi", true);

    assertTree(
      "-/\n" +
      " -com\n" +
      "  -intellij\n" +
      "   [openapi]\n" +
      " +jetbrains\n" +
      " +org\n" +
      " +xunit\n");


    final DefaultMutableTreeNode node = findNode("intellij", false);
    collapsePath(new TreePath(node.getPath()));

    assertTree(
      "-/\n" +
      " -com\n" +
      "  +[intellij]\n" +
      " +jetbrains\n" +
      " +org\n" +
      " +xunit\n");
  }


  public void testDeferredSelection() throws Exception {
    buildStructure(myRoot, false);

    final Ref<Boolean> queued = new Ref<Boolean>(false);
    final Ref<Boolean> intellijSelected = new Ref<Boolean>(false);
    final Ref<Boolean> jetbrainsSelected = new Ref<Boolean>(false);
    invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          getBuilder().select(new NodeElement("intellij"), new Runnable() {
            @Override
            public void run() {
              intellijSelected.set(true);
            }
          }, true);
          queued.set(true);
        }
        catch (Exception e) {
          e.printStackTrace();
          fail();
        }
      }
    });

    new WaitFor() {
      @Override
      protected boolean condition() {
        return queued.get();
      }
    };

    assertTrue(getBuilder().getUi().isReady());
    assertTree("+null\n");
    assertNull(((DefaultMutableTreeNode)getBuilder().getTreeModel().getRoot()).getUserObject());

    invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        getBuilder().getUi().activate(true);
        getBuilder().select(new NodeElement("jetbrains"), new Runnable() {
          @Override
          public void run() {
            jetbrainsSelected.set(true);
          }
        }, true);
      }
    });

    waitBuilderToCome(new Condition<Object>() {
      @Override
      public boolean value(Object object) {
        return intellijSelected.get() && jetbrainsSelected.get();
      }
    });

    assertTree("-/\n" +
               " -com\n" +
               "  +[intellij]\n" +
               " +[jetbrains]\n" +
               " +org\n" +
               " +xunit\n");
  }

  private void expandNext(final NodeElement[] elements, final int index, final ProgressIndicator indicator, final ActionCallback callback) {
    if (indicator.isCanceled()) {
      callback.setRejected();
      return;
    }

    if (index >= elements.length) {
      callback.setDone();
      return;
    }

    getBuilder().expand(elements[index], new Runnable() {
      public void run() {
        expandNext(elements, index + 1, indicator, callback);
      }
    });
  }

  public void testSelectAfterCancelledUpdate() throws Exception {
    Node intellij = myRoot.addChild("com").addChild("intellij");
    myRoot.addChild("jetbrains");
    activate();

    buildNode(new NodeElement("intellij"), false);
    assertTree("-/\n" +
               " -com\n" +
               "  intellij\n" +
               " jetbrains\n");


    intellij.addChild("ide");

    runAndInterrupt(new MyRunnable() {
      @Override
      public void runSafe() throws Exception {
        updateFromRoot();
      }
    }, "getChildren", new NodeElement("intellij"), Interruption.invokeCancel);

    assertTree("-/\n" +
               " -com\n" +
               "  intellij\n" +
               " jetbrains\n");

    select(new NodeElement("ide"), false);

    assertTree("-/\n" +
               " -com\n" +
               "  -intellij\n" +
               "   [ide]\n" +
               " jetbrains\n");
  }

  private void assertInterruption(Interruption cancelled) throws Exception {
    buildStructure(myRoot);

    expand(getPath("/"));
    expand(getPath("com"));
    expand(getPath("jetbrains"));
    expand(getPath("org"));
    expand(getPath("xunit"));

    assertTree("-/\n" +
               " -com\n" +
               "  +intellij\n" +
               " -jetbrains\n" +
               "  +fabrique\n" +
               " -org\n" +
               "  +eclipse\n" +
               " -xunit\n" +
               "  runner\n");

    runAndInterrupt(new MyRunnable() {
      public void runSafe() throws Exception {
        updateFrom(new NodeElement("/"));
      }
    }, "update", new NodeElement("jetbrains"), cancelled);

    runAndInterrupt(new MyRunnable() {
      @Override
      public void runSafe() throws Exception {
        updateFrom(new NodeElement("/"));
      }
    }, "getChildren", new NodeElement("jetbrains"), cancelled);
  }


  public void testBigTreeUpdate() throws Exception {
    Node msg = myRoot.addChild("Messages");

    buildSiblings(msg, 0, 1, null, null);

    doAndWaitForBuilder(new Runnable() {
      public void run() {
        getBuilder().getUi().activate(true);
      }
    });

    buildNode("Messages", false);

    assertTree("-/\n" +
               " -Messages\n" +
               "  -File 0\n" +
               "   message 1 for 0\n" +
               "   message 2 for 0\n" +
               "  -File 1\n" +
               "   message 1 for 1\n" +
               "   message 2 for 1\n");


    buildSiblings(msg, 2, 1000, new Runnable() {
      public void run() {
        getBuilder().queueUpdate();
      }
    }, null);

    waitBuilderToCome();
  }

  private void buildSiblings(final Node node, final int start, final int end, final Runnable eachRunnable, final Runnable endRunnable) throws InvocationTargetException, InterruptedException {
    SwingUtilities.invokeAndWait(new Runnable() {
      public void run() {
        for (int i = start; i <= end; i++) {
          Node eachFile = node.addChild("File " + i);
          myAutoExpand.add(eachFile.getElement());
          eachFile.addChild("message 1 for " + i);
          eachFile.addChild("message 2 for " + i);

          if (eachRunnable != null) {
            eachRunnable.run();
          }
        }

        if (endRunnable != null) {
          endRunnable.run();
        }
      }
    });
  }

  private enum Interruption {
    throwProcessCancelled, invokeCancel
  }

  private void runAndInterrupt(final Runnable action, final String interruptAction, final Object interruptElement, final Interruption interruption) throws Exception {
    myElementUpdate.clear();

    final Ref<Thread> thread = new Ref<Thread>();

    final boolean[] wasInterrupted = new boolean[] {false};
    myElementUpdateHook = new ElementUpdateHook() {
      public void onElementAction(String action, Object element) {
        if (thread.get() == null) {
          thread.set(Thread.currentThread());
        }


        boolean toInterrupt = element.equals(interruptElement) && action.equals(interruptAction);

        if (wasInterrupted[0]) {
          if (myCancelRequest == null) {
            String status = getBuilder().getUi().getStatus();
            myCancelRequest = new AssertionError("Not supposed to be update after interruption request: action=" + action + " element=" + element + " interruptAction=" + interruptAction + " interruptElement=" + interruptElement);
          }
        } else {
          if (toInterrupt) {
            wasInterrupted[0] = true;
            switch (interruption) {
              case throwProcessCancelled:
                throw new ProcessCanceledException();
              case invokeCancel:
                getBuilder().cancelUpdate();
                break;
            }
          }
        }
      }
    };

    action.run();

    myCancelRequest = null;
    myElementUpdateHook = null;
  }

  public void testQueryStructure() throws Exception {
    buildStructure(myRoot);

    assertTree("+/\n");
    assertUpdates("/: update");

    expand(getPath("/"));
    assertTree("-/\n" +
               " +com\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xunit\n");
    assertUpdates("/: update getChildren\n" +
                  "com: update getChildren\n" +
                  "eclipse: update\n" +
                  "fabrique: update\n" +
                  "intellij: update\n" +
                  "jetbrains: update getChildren\n" +
                  "org: update getChildren\n" +
                  "runner: update\n" +
                  "xunit: update getChildren");

    collapsePath(getPath("/"));
    assertTree("+/\n");
    assertUpdates("");

    expand(getPath("/"));
    assertTree("-/\n" +
               " +com\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xunit\n");
    assertUpdates("/: update getChildren\n" +
                  "com: update getChildren\n" +
                  "eclipse: update\n" +
                  "fabrique: update\n" +
                  "intellij: update\n" +
                  "jetbrains: update getChildren\n" +
                  "org: update getChildren\n" +
                  "runner: update\n" +
                  "xunit: update getChildren");

    updateFromRoot();
    assertTree("-/\n" +
               " +com\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xunit\n");

    assertUpdates("/: update getChildren\n" +
                  "com: update getChildren\n" +
                  "eclipse: update\n" +
                  "fabrique: update\n" +
                  "intellij: update\n" +
                  "jetbrains: update getChildren\n" +
                  "org: update getChildren\n" +
                  "runner: update\n" +
                  "xunit: update getChildren");

  }

  public void testQueryStructureWhenExpand() throws Exception {
    buildStructure(myRoot);

    assertTree("+/\n");
    assertUpdates("/: update");

    buildNode("ide", false);
    assertTree("-/\n" +
               " +com\n" +
               " -jetbrains\n" +
               "  -fabrique\n" +
               "   ide\n" +
               " +org\n" +
               " +xunit\n");

    assertUpdates("/: update getChildren\n" +
                  "com: update getChildren\n" +
                  "eclipse: update\n" +
                  "fabrique: update (2) getChildren\n" +
                  "ide: update getChildren\n" +
                  "intellij: update\n" +
                  "jetbrains: update getChildren\n" +
                  "org: update getChildren\n" +
                  "runner: update\n" +
                  "xunit: update getChildren");

  }

  public void testQueryStructureIsAlwaysShowsPlus() throws Exception {
    buildStructure(myRoot);
    myAlwaysShowPlus.add(new NodeElement("jetbrains"));
    myAlwaysShowPlus.add(new NodeElement("ide"));

    expand(getPath("/"));
    assertTree("-/\n" +
               " +com\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xunit\n");

    assertUpdates("/: update (2) getChildren\n" +
                  "com: update getChildren\n" +
                  "eclipse: update\n" +
                  "intellij: update\n" +
                  "jetbrains: update\n" +
                  "org: update getChildren\n" +
                  "runner: update\n" +
                  "xunit: update getChildren");

    expand(getPath("jetbrains"));
    expand(getPath("fabrique"));

    assertTree("-/\n" +
               " +com\n" +
               " -jetbrains\n" +
               "  -fabrique\n" +
               "   +ide\n" +
               " +org\n" +
               " +xunit\n");

    assertUpdates("fabrique: update getChildren\n" +
                  "ide: update\n" +
                  "jetbrains: update getChildren");


    expand(getPath("ide"));
    assertTree("-/\n" +
               " +com\n" +
               " -jetbrains\n" +
               "  -fabrique\n" +
               "   ide\n" +
               " +org\n" +
               " +xunit\n");

    assertUpdates("ide: update getChildren");
  }

  public void testQueryStructureIsAlwaysLeaf() throws Exception {
    buildStructure(myRoot);
    myStructure.addLeaf(new NodeElement("openapi"));

    buildNode("jetbrains", false);
    assertTree("-/\n" +
               " +com\n" +
               " -jetbrains\n" +
               "  +fabrique\n" +
               " +org\n" +
               " +xunit\n");

    assertUpdates("/: update (2) getChildren\n" +
                  "com: update getChildren\n" +
                  "eclipse: update\n" +
                  "fabrique: update (2) getChildren\n" +
                  "ide: update\n" +
                  "intellij: update\n" +
                  "jetbrains: update getChildren\n" +
                  "org: update getChildren\n" +
                  "runner: update\n" +
                  "xunit: update getChildren");

    expand(getPath("fabrique"));
    assertTree("-/\n" +
               " +com\n" +
               " -jetbrains\n" +
               "  -fabrique\n" +
               "   ide\n" +
               " +org\n" +
               " +xunit\n");
    assertUpdates("ide: update getChildren");


    buildNode("com", false);
    assertTree("-/\n" +
               " -com\n" +
               "  +intellij\n" +
               " -jetbrains\n" +
               "  -fabrique\n" +
               "   ide\n" +
               " +org\n" +
               " +xunit\n");

    myElementUpdate.clear();

    expand(getPath("intellij"));
    assertTree("-/\n" +
               " -com\n" +
               "  -intellij\n" +
               "   openapi\n" +
               " -jetbrains\n" +
               "  -fabrique\n" +
               "   ide\n" +
               " +org\n" +
               " +xunit\n");

    assertUpdates("");
  }

  public void testToggleIsAlwaysLeaf() throws Exception {
    buildStructure(myRoot);

    buildNode("openapi", true);

    assertTree("-/\n" +
               " -com\n" +
               "  -intellij\n" +
               "   [openapi]\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xunit\n");

    myStructure.addLeaf(new NodeElement("intellij"));

    updateFrom(new NodeElement("com"));

    assertTree("-/\n" +
               " -com\n" +
               "  [intellij]\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xunit\n");

    myStructure.removeLeaf(new NodeElement("intellij"));
    updateFrom(new NodeElement("com"));

    assertTree("-/\n" +
               " -com\n" +
               "  +[intellij]\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xunit\n");

    expand(getPath("intellij"));

    assertTree("-/\n" +
               " -com\n" +
               "  -[intellij]\n" +
               "   openapi\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xunit\n");

  }


  public void testSorting() throws Exception {
    buildStructure(myRoot);
    assertSorted("");

    buildNode("/", false);
    assertTree("-/\n" +
               " +com\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xunit\n");

    assertSorted("/\n" +
                 "com\n" +
                 "jetbrains\n" +
                 "org\n" +
                 "xunit");

    updateFromRoot();
    assertTree("-/\n" +
               " +com\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xunit\n");
    assertSorted("/\n" +
                 "com\n" +
                 "jetbrains\n" +
                 "org\n" +
                 "xunit");

    updateFrom(new NodeElement("/"), false);
    assertTree("-/\n" +
               " +com\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xunit\n");
    assertSorted("");


    expand(getPath("com"));
    assertTree("-/\n" +
               " -com\n" +
               "  +intellij\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xunit\n");
    assertSorted("intellij");
  }

  public void testResorting() throws Exception {
    final boolean invert[] = new boolean[] {false};
    NodeDescriptor.NodeComparator<NodeDescriptor> c = new NodeDescriptor.NodeComparator<NodeDescriptor>() {
      public int compare(NodeDescriptor o1, NodeDescriptor o2) {
        return invert[0] ? AlphaComparator.INSTANCE.compare(o2, o1) : AlphaComparator.INSTANCE.compare(o1, o2);
      }
    };

    myComparator.setDelegate(c);

    buildStructure(myRoot);

    buildNode("/", false);
    assertTree("-/\n" +
               " +com\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xunit\n");

    updateFromRoot();
    updateFromRoot();
    updateFromRoot();

    assertTrue(getMyBuilder().getUi().myOwnComparatorStamp > c.getStamp());
    invert[0] = true;
    c.incStamp();

    updateFrom(new NodeElement("/"), false);
    assertTree("-/\n" +
               " +xunit\n" +
               " +org\n" +
               " +jetbrains\n" +
               " +com\n");
  }

  public void testRestoreSelectionOfRemovedElement() throws Exception {
    buildStructure(myRoot);
    buildNode("openapi", true);
    assertTree("-/\n" +
               " -com\n" +
               "  -intellij\n" +
               "   [openapi]\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xunit\n");


    removeFromParentButKeepRef(new NodeElement("openapi"));

    updateFromRoot();

    assertTree("-/\n" +
               " -com\n" +
               "  [intellij]\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xunit\n");
  }

  public void testElementMove1() throws Exception {
    assertMove(new Runnable() {
      public void run() {
        getBuilder().getUpdater().addSubtreeToUpdateByElement(new NodeElement("com"));
        getBuilder().getUpdater().addSubtreeToUpdateByElement(new NodeElement("jetbrains"));
      }
    });
  }

  public void testElementMove2() throws Exception {
    assertMove(new Runnable() {
      public void run() {
        getBuilder().getUpdater().addSubtreeToUpdateByElement(new NodeElement("jetbrains"));
        getBuilder().getUpdater().addSubtreeToUpdateByElement(new NodeElement("com"));
      }
    });
  }

  public void testSelectionOnDelete() throws Exception {
    doTestSelectionOnDelete(false);
  }

  public void testSelectionOnDeleteButKeepRef() throws Exception {
    doTestSelectionOnDelete(true);
  }

  public void testRevalidateStructure() throws Exception {
    final NodeElement com = new NodeElement("com");
    final NodeElement actionSystem = new NodeElement("actionSystem");
    actionSystem.setForcedParent(com);

    final NodeElement fabrique = new NodeElement("fabrique");
    final NodeElement ide = new NodeElement("ide");
    fabrique.setForcedParent(myRoot.getElement());

    doAndWaitForBuilder(new Runnable() {
      public void run() {
        myRoot.addChild(com).addChild(actionSystem);
        myRoot.addChild(fabrique).addChild(ide);
        getBuilder().getUi().activate(true);
      }
    });

    select(actionSystem, false);
    expand(getPath("ide"));

    assertTree("-/\n" +
               " -com\n" +
               "  [actionSystem]\n" +
               " -fabrique\n" +
               "  ide\n");


    removeFromParentButKeepRef(actionSystem);
    removeFromParentButKeepRef(fabrique);

    final NodeElement newActionSystem = new NodeElement("actionSystem");
    final NodeElement newFabrique = new NodeElement("fabrique");

    myStructure.getNodeFor(com).addChild("intellij").addChild("openapi").addChild(newActionSystem);
    myRoot.addChild("jetbrains").addChild("tools").addChild(newFabrique).addChild("ide");

    assertSame(com, myStructure.getParentElement(actionSystem));
    assertNotSame(com, newActionSystem);
    assertEquals(new NodeElement("openapi"), myStructure.getParentElement(newActionSystem));

    assertSame(myRoot.getElement(), myStructure.getParentElement(fabrique));

    myStructure.setRevalidator(new Revalidator() {
      public AsyncResult<Object> revalidate(NodeElement element) {
        if (element == actionSystem) {
          return new AsyncResult.Done<Object>(newActionSystem);
        } else if (element == fabrique) {
          return new AsyncResult.Done<Object>(newFabrique);
        }
        return null;
      }
    });

    updateFromRoot();

    assertTree("-/\n" +
               " -com\n" +
               "  -intellij\n" +
               "   -openapi\n" +
               "    [actionSystem]\n" +
               " -jetbrains\n" +
               "  -tools\n" +
               "   -fabrique\n" +
               "    ide\n");
  }

  public void testNoRevalidationIfInvalid() throws Exception {
    buildStructure(myRoot);

    final NodeElement intellij = new NodeElement("intellij");
    buildNode(intellij, true);

    assertTree("-/\n" +
               " -com\n" +
               "  +[intellij]\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xunit\n");


    removeFromParentButKeepRef(new NodeElement("intellij"));
    myValidator = new Validator() {
      public boolean isValid(Object element) {
        return !element.equals(intellij);
      }
    };
    final Ref<Object> revalidatedElement = new Ref<Object>();
    myStructure.setRevalidator(new Revalidator() {
      public AsyncResult<Object> revalidate(NodeElement element) {
        revalidatedElement.set(element);
        return null;
      }
    });

    updateFromRoot();

    assertTree("-/\n" +
               " [com]\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xunit\n");
    assertNull(revalidatedElement.get() != null ? revalidatedElement.get().toString() : null, revalidatedElement.get());
  }


  private void doTestSelectionOnDelete(boolean keepRef) throws Exception {
    myComparator.setDelegate(new NodeDescriptor.NodeComparator<NodeDescriptor>() {
      public int compare(NodeDescriptor o1, NodeDescriptor o2) {
        boolean isParent1 = myStructure._getChildElements(o1.getElement(), false).length > 0;
        boolean isParent2 = myStructure._getChildElements(o2.getElement(), false).length > 0;

        int result = AlphaComparator.INSTANCE.compare(o1, o2);

        if (isParent1) {
          result -= 1000;
        }

        if (isParent2) {
          result += 1000;
        }

        return result;
      }
    });

    buildStructure(myRoot);
    myRoot.addChild("toDelete");

    select(new NodeElement("toDelete"), false);
    assertTree("-/\n" +
               " +com\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xunit\n" +
               " [toDelete]\n");

    if (keepRef) {
      removeFromParentButKeepRef(new NodeElement("toDelete"));
    } else {
      myStructure.getNodeFor(new NodeElement("toDelete")).delete();
    }

    getMyBuilder().addSubtreeToUpdateByElement(new NodeElement("/"));

    assertTree("-/\n" +
               " +com\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +[xunit]\n");
  }

  public void testSelectWhenUpdatesArePending() throws Exception {
    getBuilder().getUpdater().setDelay(1000);

    buildStructure(myRoot);

    buildNode("intellij", false);
    select(new Object[] {new NodeElement("intellij")}, false);
    assertTree("-/\n" +
               " -com\n" +
               "  -[intellij]\n" +
               "   openapi\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xunit\n");


    myIntellij.addChild("ui");

    DefaultMutableTreeNode intellijNode = findNode("intellij", false);
    assertTrue(myTree.isExpanded(new TreePath(intellijNode.getPath())));
    getMyBuilder().addSubtreeToUpdate(intellijNode);
    assertFalse(getMyBuilder().getUi().isReady());

    select(new Object[] {new NodeElement("ui")}, false);
    assertTree("-/\n" +
               " -com\n" +
               "  -intellij\n" +
               "   openapi\n" +
               "   [ui]\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xunit\n");
  }

  public void testAddNewElementToLeafElementAlwaysShowPlus() throws Exception {
    myAlwaysShowPlus.add(new NodeElement("openapi"));

    buildStructure(myRoot);
    select(new Object[] {new NodeElement("openapi")}, false);

    assertTree("-/\n" +
               " -com\n" +
               "  -intellij\n" +
               "   +[openapi]\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xunit\n");

    expand(getPath("openapi"));
    assertTree("-/\n" +
               " -com\n" +
               "  -intellij\n" +
               "   [openapi]\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xunit\n");


    myOpenApi.addChild("ui");

    getMyBuilder().addSubtreeToUpdate(findNode("openapi", false));

    assertTree("-/\n" +
               " -com\n" +
               "  -intellij\n" +
               "   +[openapi]\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xunit\n");
  }

  public void testAddNewElementToLeafElement() throws Exception {
    buildStructure(myRoot);
    select(new Object[] {new NodeElement("openapi")}, false);

    assertTree("-/\n" +
               " -com\n" +
               "  -intellij\n" +
               "   [openapi]\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xunit\n");

    expand(getPath("openapi"));
    assertTree("-/\n" +
               " -com\n" +
               "  -intellij\n" +
               "   [openapi]\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xunit\n");


    myOpenApi.addChild("ui");

    getMyBuilder().addSubtreeToUpdate(findNode("openapi", false));

    assertTree("-/\n" +
               " -com\n" +
               "  -intellij\n" +
               "   +[openapi]\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xunit\n");
  }


  private void assertMove(Runnable updateRoutine) throws Exception {
    buildStructure(myRoot);

    buildNode("intellij", true);
    assertTree("-/\n" +
               " -com\n" +
               "  +[intellij]\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xunit\n");

    AbstractTreeBuilderTest.Node intellij = removeFromParentButKeepRef(new NodeElement("intellij"));
    myRoot.getChildNode("jetbrains").addChild(intellij);

    updateRoutine.run();

    assertTree("-/\n" +
               " com\n" +
               " -jetbrains\n" +
               "  +fabrique\n" +
               "  +[intellij]\n" +
               " +org\n" +
               " +xunit\n");

  }


  public void testChangeRootElement() throws Exception {
    buildStructure(myRoot);

    select(new NodeElement("com"), false);

    assertTree("-/\n" +
               " +[com]\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xunit\n");

    myRoot = new Node(null, "root");
    myStructure.reinitRoot(myRoot);

    myRoot.addChild("com");

    updateFromRoot();
    assertTree("+root\n");
  }

  public void testReleaseBuilderDuringUpdate() throws Exception {
    assertReleaseDuringBuilding("update", "fabrique", new Runnable() {
      public void run() {
        try {
          select(new NodeElement("ide"), false);
        }
        catch (Exception e) {
          myCancelRequest = e;
        }
      }
    });
  }

  public void testStickyLoadingNodeIssue() throws Exception {
    buildStructure(myRoot);

    final boolean[] done = new boolean[] {false};
    getBuilder().select(new NodeElement("jetbrains"), new Runnable() {
      public void run() {
        getBuilder().expand(new NodeElement("fabrique"), new Runnable() {
          public void run() {
            done[0] = true;
          }
        });
      }
    });

    waitBuilderToCome(new Condition() {
      public boolean value(Object o) {
        return done[0];
      }
    });

    assertTree("-/\n" +
               " +com\n" +
               " -[jetbrains]\n" +
               "  -fabrique\n" +
               "   ide\n" +
               " +org\n" +
               " +xunit\n");
  }

  public void testReleaseBuilderDuringGetChildren() throws Exception {
    assertReleaseDuringBuilding("getChildren", "fabrique", new Runnable() {
      public void run() {
        try {
          select(new NodeElement("ide"), false);
        }
        catch (Exception e) {
          myCancelRequest = e;
        }
      }
    });
  }

  private void assertReleaseDuringBuilding(final String actionAction, final Object actionElement, Runnable buildAction) throws Exception {
    buildStructure(myRoot);

    myElementUpdateHook = new ElementUpdateHook() {
      public void onElementAction(String action, Object element) {
        if (!element.toString().equals(actionElement.toString())) return;

        Runnable runnable = new Runnable() {
          public void run() {
            myReadyRequest = true;
            Disposer.dispose(getBuilder());
          }
        };

        if (actionAction.equals(action)) {
          if (getBuilder().getUi().isPassthroughMode()) {
            runnable.run();
          } else {
            SwingUtilities.invokeLater(runnable);
          }
        }
      }
    };

    buildAction.run();

    boolean released = new WaitFor(15000) {
      @Override
      protected boolean condition() {
        return getBuilder().getUi() == null;
      }
    }.isConditionRealized();

    assertTrue(released);
  }

  public static class SyncUpdate extends TreeUiTest {
    public SyncUpdate() {
      super(false, false);
    }
  }

  public static class Passthrough extends TreeUiTest {
    public Passthrough() {
      super(true);
    }

    public void testSelectionGoesToParentWhenOnlyChildMoved2() throws Exception {
      //todo
    }

    public void testQueryStructureWhenExpand() throws Exception {
      //todo
    }


    public void testElementMove1() throws Exception {
      //todo
    }

    @Override
    public void testClear() throws Exception {
      //todo
    }

    @Override
    public void testSelectWhenUpdatesArePending() throws Exception {
      // doesn't make sense in pass-through mode
    }

    @Override
    public void testBigTreeUpdate() throws Exception {
      // doesn't make sense in pass-thorught mode
    }
  }

  public static class YieldingUpdate extends TreeUiTest {
    public YieldingUpdate() {
      super(true, false);
    }
  }

  public static class BgLoadingSyncUpdate extends TreeUiTest {
    public BgLoadingSyncUpdate() {
      super(false, true);
    }

    @Override
    protected int getChildrenLoadingDelay() {
      return 300;
    }

    @Override
    protected int getNodeDescriptorUpdateDelay() {
      return 300;
    }

    @Override
    public void testNoInfiniteSmartExpand() throws Exception {
      //todo
    }

    @Override
    public void testBigTreeUpdate() throws Exception {
      //to slow, tested the same in VeryQuickBgLoadingTest
    }
  }

  public static class QuickBgLoadingSyncUpdate extends TreeUiTest {
    public QuickBgLoadingSyncUpdate() {
      super(false, true);
    }


    @Override
    public void testDeferredSelection() throws Exception {
      super.testDeferredSelection();
    }

    @Override
    protected int getNodeDescriptorUpdateDelay() {
      return 300;
    }

    @Override
    public void testNoInfiniteSmartExpand() throws Exception {
      //todo
    }

    @Override
    public void testBigTreeUpdate() throws Exception {
      //to slow, tested the same in VeryQuickBgLoadingTest
    }

  }

  public static class VeryQuickBgLoadingSyncUpdate extends TreeUiTest {
    public VeryQuickBgLoadingSyncUpdate() {
      super(false, true);
    }

    @Override
    protected int getNodeDescriptorUpdateDelay() {
      return 0;
    }

    @Override
    protected int getChildrenLoadingDelay() {
      return 0;
    }

    @Override
    public void testNoInfiniteSmartExpand() throws Exception {
      // todo;
    }

    @Override
    public void testReleaseBuilderDuringUpdate() throws Exception {
      // todo
    }

    @Override
    public void testReleaseBuilderDuringGetChildren() throws Exception {
      // todo
    }
  }

  public static TestSuite suite() {
    TestSuite suite = new TestSuite();
    suite.addTestSuite(Passthrough.class);
    suite.addTestSuite(SyncUpdate.class);
    suite.addTestSuite(YieldingUpdate.class);
    suite.addTestSuite(VeryQuickBgLoadingSyncUpdate.class);
    suite.addTestSuite(QuickBgLoadingSyncUpdate.class);
    suite.addTestSuite(BgLoadingSyncUpdate.class);
    return suite;
  }

  @Override
  protected void tearDown() throws Exception {
    AbstractTreeUi ui = getBuilder().getUi();
    if (ui != null) {
      ui.getReady(this).doWhenProcessed(new Runnable() {
        public void run() {
          Log.flush();
        }
      });
    } else {
      Log.flush();
    }
    super.tearDown();
  }

  abstract class MyRunnable implements Runnable {
    public final void run() {
      try {
        runSafe();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    public abstract void runSafe() throws Exception;
  }
}
