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
package com.intellij.ide.util.treeView;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Progressive;
import com.intellij.openapi.util.*;
import com.intellij.ui.LoadingNode;
import com.intellij.util.Time;
import com.intellij.util.WaitFor;
import com.intellij.util.ui.UIUtil;
import junit.framework.AssertionFailedError;
import junit.framework.TestSuite;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.intellij.testFramework.PlatformTestUtil.notNull;

@SuppressWarnings("JUnitTestClassNamingConvention")
public class TreeUiTest extends AbstractTreeBuilderTest {
  public TreeUiTest(boolean passThrough) {
    super(passThrough);
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

  public void testDoubleCancelUpdate() throws Exception {
    buildStructure(myRoot);

    runAndInterrupt(() -> {
      try {
        select(new Object[] {new NodeElement("openapi")}, false, true);
      }
      catch (Exception e) {
        fail();
      }
    }, "getChildren", new NodeElement("intellij"), Interruption.invokeCancel);

    select(new NodeElement("fabrique"), false);

    assertTree("-/\n"
               + " -com\n"
               + "  intellij\n"
               + " -jetbrains\n"
               + "  +[fabrique]\n"
               + " +org\n"
               + " +xUnit\n");

    updateFromRoot();

    assertTree("-/\n"
               + " -com\n"
               + "  +intellij\n"
               + " -jetbrains\n"
               + "  +[fabrique]\n"
               + " +org\n"
               + " +xUnit\n");
  }

  public void testBatchUpdate() throws Exception {
    buildStructure(myRoot);

    myElementUpdate.clear();

    final NodeElement[] toExpand = new NodeElement[] {
      new NodeElement("com"),
      new NodeElement("jetbrains"),
      new NodeElement("org"),
      new NodeElement("xUnit")
    };

    final ActionCallback done = new ActionCallback();
    final Ref<ProgressIndicator> indicatorRef = new Ref<>();
    final Ref<ActionCallback> ready = new Ref<>();

    myElementUpdateHook = new ElementUpdateHook() {
      @Override
      public void onElementAction(String action, Object element) {
        if (new NodeElement("jetbrains").equals(element) && ready.get() == null) {
          ActionCallback readyCallback = new ActionCallback();
          assertFalse(getBuilder().getUi().isReady());
          getBuilder().getReady(this).notify(readyCallback);
          ready.set(readyCallback);
        }
      }
    };

    invokeLaterIfNeeded(() -> getBuilder().batch(new Progressive() {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicatorRef.set(indicator);
        expandNext(toExpand, 0, indicator, done);
      }
    }).notify(done));

    waitBuilderToCome(o -> done.isProcessed());

    assertTrue(done.isDone());
    assertNotNull(ready.get());
    assertTrue(ready.get().isDone());

    assertTree("-/\n" +
               " -com\n" +
               "  +intellij\n" +
               " -jetbrains\n" +
               "  +fabrique\n" +
               " -org\n" +
               "  +eclipse\n" +
               " -xUnit\n" +
               "  runner\n");

    assertFalse(indicatorRef.get().isCanceled());
  }

  public void testRenameCollapsedParentAlwaysShowsPlus() throws Exception {
    buildStructure(myRoot);
    myAlwaysShowPlus.add(myCom.getElement());

    buildNode("com", false);

    assertTree("-/\n" +
               " -com\n" +
               "  +intellij\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xUnit\n");

    collapsePath(getPath("com"));
    assertTree("-/\n" +
               " +com\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xUnit\n");

    myCom.getElement().setPresentableName("com1");
    updateFrom(myCom.getElement());

    assertTree("-/\n" +
               " +com1\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xUnit\n");
  }

  public void testMoveElementToAdjacentEmptyParentWithSmartExpandAndSerialUpdateSubtrees() throws Exception {
    Node com = myRoot.addChild("com");
    Node folder1 = com.addChild("folder1");
    Node folder2 = com.addChild("folder2");

    Node file21 = folder2.addChild("file21");
    folder2.addChild("file22");

    mySmartExpand = true;
    activate();

    buildNode(file21, true);

    assertTree("-/\n" +
               " -com\n" +
               "  folder1\n" +
               "  -folder2\n" +
               "   [file21]\n" +
               "   file22\n");

    folder2.myChildElements.remove(file21);
    folder1.addChild(file21);

    getBuilder().queueUpdateFrom(folder2.getElement(), false);
    getBuilder().queueUpdateFrom(folder1.getElement(), false);

    assertTree("-/\n" +
               " -com\n" +
               "  -folder1\n" +
               "   [file21]\n" +
               "  -folder2\n" +
               "   file22\n");
  }

  public void testReadyCallbackWhenReleased() throws Exception {
    buildStructure(myRoot);

    final Ref<Boolean> done = new Ref<>(false);
    final Ref<Boolean> rejected = new Ref<>(false);
    final Ref<Boolean> processed = new Ref<>(false);
    final Ref<Boolean> wasUiNull = new Ref<>(true);

    final Ref<Runnable> addReadyCallbacks = new Ref<>(new Runnable() {
      public void run() {
        getBuilder().getReady(this).doWhenDone(new NamedRunnable("on done") {
          @Override
          public void run() {
            wasUiNull.set(getBuilder().getUi() == null);
            done.set(true);
          }
        }).doWhenRejected(new NamedRunnable("on rejected") {
          @Override
          public void run() {
            wasUiNull.set(getBuilder().getUi() == null);
            rejected.set(true);
          }
        }).doWhenProcessed(new NamedRunnable("on processed") {
          @Override
          public void run() {
            processed.set(true);
          }
        });
      }
    });

    final Ref<Boolean> disposeRequested = new Ref<>(false);
    myElementUpdateHook = new ElementUpdateHook() {
      @Override
      public void onElementAction(String action, Object element) {
        if (addReadyCallbacks.get() != null) {
          addReadyCallbacks.get().run();
          addReadyCallbacks.set(null);
        }

        if (element.equals(new NodeElement("ide"))) {
          disposeRequested.set(true);
          //noinspection SSBasedInspection
          getBuilder().dispose();
        }
      }
    };

    invokeLaterIfNeeded(() -> getBuilder().expand(new NodeElement("fabrique"), null));

    waitBuilderToCome(o -> disposeRequested.get());
    assertTrue(wasUiNull.get());
    assertFalse(done.get());
    assertTrue(rejected.get());
    assertTrue(processed.get());
  }

  public void testNoExtraJTreeModelUpdate() throws Exception {
    buildStructure(myRoot);
    expand(getPath("/"));

    assertTree("-/\n" +
               " +com\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xUnit\n");

    final Ref<StringBuffer> updates = new Ref<>(new StringBuffer());
    notNull(getMyBuilder().getTreeModel()).addTreeModelListener(new TreeModelListener() {
      @Override
      public void treeNodesChanged(TreeModelEvent e) {
        updates.get().append("changed parent").append(e.getTreePath()).append(" children=").append(Arrays.asList(e.getChildren()))
          .append("\n");
      }

      @Override
      public void treeNodesInserted(TreeModelEvent e) {
        updates.get().append("inserted=").append(e.getTreePath()).append("\n");
      }

      @Override
      public void treeNodesRemoved(TreeModelEvent e) {
        updates.get().append("removed=").append(e.getTreePath()).append("\n");
      }

      @Override
      public void treeStructureChanged(TreeModelEvent e) {
        updates.get().append("structureChanged=").append(e.getTreePath()).append("\n");
      }
    });

    assertEquals("", updates.get().toString());

    updateFromRoot();
    assertEquals("", updates.get().toString());

    myChanges.add(new NodeElement("com"));
    updateFromRoot();
    assertEquals("changed parent[/] children=[com]\n", updates.get().toString());
    assertTree("-/\n" +
               " +com\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xUnit\n");
    updates.set(new StringBuffer());

    updateFrom(new NodeElement("org"));
    assertEquals("", updates.get().toString());

    myChanges.add(new NodeElement("org"));
    updateFrom(new NodeElement("org"));
    assertEquals("changed parent[/] children=[org]\n", updates.get().toString());
    updates.set(new StringBuffer());

    myChanges.add(new NodeElement("intellij"));
    updateFromRoot();
    assertEquals("", updates.get().toString());
  }

  public void testCancelUpdateBatch() throws Exception {
    buildStructure(myRoot);

    myAlwaysShowPlus.add(new NodeElement("com"));
    myAlwaysShowPlus.add(new NodeElement("jetbrains"));
    myAlwaysShowPlus.add(new NodeElement("org"));
    myAlwaysShowPlus.add(new NodeElement("xUnit"));

    final Ref<Boolean> cancelled = new Ref<>(false);
    myElementUpdateHook = new ElementUpdateHook() {
      @Override
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
      new NodeElement("xUnit")
    };

    final ActionCallback done = new ActionCallback();
    final Ref<ProgressIndicator> indicatorRef = new Ref<>();

    invokeLaterIfNeeded(() -> getBuilder().batch(new Progressive() {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicatorRef.set(indicator);
        expandNext(toExpand, 0, indicator, done);
      }
    }).notify(done));

    waitBuilderToCome(o -> done.isProcessed() || myCancelRequest != null);

    assertNull(myCancelRequest);
    assertTrue(done.isRejected());
    assertTrue(indicatorRef.get().isCanceled());

    assertFalse(getBuilder().getUi().isCancelProcessed());
  }
  

  public void testExpandAll() throws Exception {
    buildStructure(myRoot);
    assertTree("+/\n");

    final Ref<Boolean> done = new Ref<>();
    doAndWaitForBuilder(() -> getBuilder().expandAll(() -> done.set(true)));

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
               + " -xUnit\n"
               + "  runner\n");
  }

  public void testInvisibleRoot() throws Exception {
    myTree.setRootVisible(false);
    buildStructure(myRoot);
    assertTree("-/\n"
               + " +com\n"
               + " +jetbrains\n"
               + " +org\n"
               + " +xUnit\n");

    collapsePath(new TreePath(myTreeModel.getRoot()));
    assertTree("+/\n");

    updateFromRoot();
    assertTree("-/\n"
               + " +com\n"
               + " +jetbrains\n"
               + " +org\n"
               + " +xUnit\n");

    buildNode("com", true);
    assertTree("-/\n"
               + " +[com]\n"
               + " +jetbrains\n"
               + " +org\n"
               + " +xUnit\n");

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
               + " +xUnit\n");

    myAutoExpand.add(new NodeElement("jetbrains"));
    updateFromRoot();

    assertTree("-/\n"
               + " +com\n"
               + " -jetbrains\n"
               + "  +fabrique\n"
               + " +org\n"
               + " +xUnit\n");

    collapsePath(getPath("jetbrains"));
    assertTree("-/\n"
               + " +com\n"
               + " +jetbrains\n"
               + " +org\n"
               + " +xUnit\n");

    updateFrom(new NodeElement("org"));
    assertTree("-/\n"
               + " +com\n"
               + " +jetbrains\n"
               + " +org\n"
               + " +xUnit\n");

    updateFrom(new NodeElement("jetbrains"));
    assertTree("-/\n"
               + " +com\n"
               + " -jetbrains\n"
               + "  +fabrique\n"
               + " +org\n"
               + " +xUnit\n");
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
               + " +xUnit\n");

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
               + " +xUnit\n");

    collapsePath(getPath("jetbrains"));
    assertTree("-/\n"
               + " +com\n"
               + " +jetbrains\n"
               + " +org\n"
               + " +xUnit\n");

    expand(getPath("jetbrains"));
    assertTree("-/\n"
               + " +com\n"
               + " -jetbrains\n"
               + "  -fabrique\n"
               + "   ide\n"
               + " +org\n"
               + " +xUnit\n");
  }

  public void testExpandEqualElements() throws Exception {
    buildStructure(myRoot, false);
    notNull(myRoot.getChildNode("org")).addChild("jetbrains").addChild("community").addChild("ide");

    activate();
    expand(getPath("/"));

    doAndWaitForBuilder(() -> {
      myTree.expandPath(getPath("org"));
      myTree.setSelectionPath(myTree.getPathForRow(5));
    });

    assertTree("-/\n"
               + " +com\n"
               + " +jetbrains\n"
               + " -org\n"
               + "  +eclipse\n"
               + "  +[jetbrains]\n"
               + " +xUnit\n");

    doAndWaitForBuilder(() -> myTree.expandPath(myTree.getPathForRow(5)));

    assertTree("-/\n"
               + " +com\n"
               + " +jetbrains\n"
               + " -org\n"
               + "  +eclipse\n"
               + "  -[jetbrains]\n"
               + "   +community\n"
               + " +xUnit\n");

    doAndWaitForBuilder(() -> myTree.expandPath(myTree.getPathForRow(6)));

    assertTree("-/\n"
               + " +com\n"
               + " +jetbrains\n"
               + " -org\n"
               + "  +eclipse\n"
               + "  -[jetbrains]\n"
               + "   -community\n"
               + "    ide\n"
               + " +xUnit\n");

    doAndWaitForBuilder(() -> myTree.collapsePath(myTree.getPathForRow(5)));

    assertTree("-/\n"
               + " +com\n"
               + " +jetbrains\n"
               + " -org\n"
               + "  +eclipse\n"
               + "  +[jetbrains]\n"
               + " +xUnit\n");

    doAndWaitForBuilder(() -> myTree.expandPath(myTree.getPathForRow(5)));

    assertTree("-/\n"
               + " +com\n"
               + " +jetbrains\n"
               + " -org\n"
               + "  +eclipse\n"
               + "  -[jetbrains]\n"
               + "   +community\n"
               + " +xUnit\n");
  }

  public void testAutoExpandInNonVisibleNode() throws Exception {
    myAutoExpand.add(new NodeElement("fabrique"));
    buildStructure(myRoot);

    expand(getPath("/"));
    assertTree("-/\n"
               + " +com\n"
               + " +jetbrains\n"
               + " +org\n"
               + " +xUnit\n");
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
               + " +xUnit\n");

    expand(getPath("jetbrains"));
    assertTree("-/\n"
               + " +com\n"
               + " -jetbrains\n"
               + "  -fabrique\n"
               + "   ide\n"
               + " +org\n"
               + " +xUnit\n");

    collapsePath(getPath("jetbrains"));
    assertTree("-/\n"
               + " +com\n"
               + " +jetbrains\n"
               + " +org\n"
               + " +xUnit\n");

    updateFromRoot();
    assertTree("-/\n"
               + " +com\n"
               + " +jetbrains\n"
               + " +org\n"
               + " +xUnit\n");

    mySmartExpand = false;
    collapsePath(getPath("jetbrains"));
    assertTree("-/\n"
               + " +com\n"
               + " +jetbrains\n"
               + " +org\n"
               + " +xUnit\n");

    expand(getPath("jetbrains"));
    assertTree("-/\n"
               + " +com\n"
               + " -jetbrains\n"
               + "  +fabrique\n"
               + " +org\n"
               + " +xUnit\n");
  }

  public void testClear() throws Exception {
    getBuilder().getUi().setClearOnHideDelay(Time.SECOND);

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
               " -xUnit\n" +
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
               " -xUnit\n" +
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
               " -xUnit\n" +
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
               " -xUnit\n" +
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
               "  -xUnit\n" +
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
      " +xUnit\n");

    buildNode("fabrique", true);

    assertTree(
      "-/\n" +
      " -com\n" +
      "  -intellij\n" +
      "   [openapi]\n" +
      " -jetbrains\n" +
      "  +[fabrique]\n" +
      " +org\n" +
      " +xUnit\n");
  }

  public void testSelectWhileUpdating() throws Exception {
    buildStructure(myRoot, false);
    myForegroundLoadingNodes.add(myRoot.getElement());

    myAutoExpand.add(myRoot.getElement());

    invokeAndWaitIfNeeded(() -> {
      getBuilder().getUi().activate(true);
      getBuilder().select(new NodeElement("com"));
    });

    waitBuilderToCome();

    assertTree(
      "-/\n" +
      " +[com]\n" +
      " +jetbrains\n" +
      " +org\n" +
      " +xUnit\n");
  }

  public void testCallbackOnceOnSelect() throws Exception {
    buildStructure(myRoot);

    assertCallbackOnce(new TreeAction() {
      @Override
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
      " +xUnit\n");
  }

  public void testCallbackOnceOnExpand() throws Exception {
    buildStructure(myRoot);

    assertCallbackOnce(new TreeAction() {
      @Override
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
      " +xUnit\n");
  }

  public void testNoInfiniteAutoExpand() throws Exception {
    mySmartExpand = false;

    assertNoInfiniteAutoExpand(() -> {
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
    });
  }

  public void testNoInfiniteSmartExpand() throws Exception {
    mySmartExpand = false;

    assertNoInfiniteAutoExpand(() -> mySmartExpand = true);
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
    invokeLaterIfNeeded(() -> action.run(() -> {
      notifyCount[0]++;
      done[0] = true;
    }));

    new WaitFor(2000) {
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
      " +xUnit\n");
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
      " +xUnit\n");

    select(new Object[] {new NodeElement("whatever1"), new NodeElement("whatever2")}, false);

    assertTree(
      "-/\n" +
      " -com\n" +
      "  -intellij\n" +
      "   [openapi]\n" +
      " -jetbrains\n" +
      "  +[fabrique]\n" +
      " +org\n" +
      " +xUnit\n");
  }

  public void testSelectionWhenChildMoved() throws Exception {
    buildStructure(myRoot);
    assertTree("+/\n");

    final Node refactoring = notNull(myCom.getChildNode("intellij")).addChild("refactoring");

    buildNode("refactoring", true);

    assertTree(
      "-/\n" +
      " -com\n" +
      "  -intellij\n" +
      "   openapi\n" +
      "   [refactoring]\n" +
      " +jetbrains\n" +
      " +org\n" +
      " +xUnit\n");

    refactoring.delete();
    notNull(notNull(myCom.getChildNode("intellij")).getChildNode("openapi")).addChild("refactoring");

    updateFromRoot();

    assertTree(
      "-/\n" +
      " -com\n" +
      "  -intellij\n" +
      "   -openapi\n" +
      "    [refactoring]\n" +
      " +jetbrains\n" +
      " +org\n" +
      " +xUnit\n");
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
      " +xUnit\n");

    notNull(notNull(myCom.getChildNode("intellij")).getChildNode("openapi")).delete();

    updateFromRoot();

    assertTree(
      "-/\n" +
      " -com\n" +
      "  [intellij]\n" +
      " +jetbrains\n" +
      " +org\n" +
      " +xUnit\n");
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

    final Ref<Boolean> done = new Ref<>();
    invokeLaterIfNeeded(() -> getBuilder().expand(new NodeElement("com"), () -> {
      notNull(getBuilder().getTree()).collapsePath(getPath("com"));
      done.set(Boolean.TRUE);
    }));

    waitBuilderToCome(o -> (done.get() != null) && done.get().booleanValue());

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
      " +xUnit\n");

    notNull(notNull(myCom.getChildNode("intellij")).getChildNode("openapi")).delete();
    notNull(myRoot.getChildNode("xUnit")).addChild("openapi");

    updateFromRoot();

    assertTree(
      "-/\n" +
      " -com\n" +
      "  intellij\n" +
      " +jetbrains\n" +
      " +org\n" +
      " -xUnit\n" +
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
      " +xUnit\n");

    notNull(notNull(myCom.getChildNode("intellij")).getChildNode("openapi")).delete();
    notNull(myRoot.getChildNode("xUnit")).addChild("openapi");

    getBuilder().addSubtreeToUpdateByElement(new NodeElement("intellij"));
    getBuilder().addSubtreeToUpdateByElement(new NodeElement("xUnit"));

    doAndWaitForBuilder(() -> notNull(getBuilder().getUpdater()).performUpdate());

    assertTree(
      "-/\n" +
      " -com\n" +
      "  intellij\n" +
      " +jetbrains\n" +
      " +org\n" +
      " -xUnit\n" +
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
      " +xUnit\n");

    collapsePath(new TreePath(notNull(findNode("intellij", false)).getPath()));

    assertTree(
      "-/\n" +
      " -com\n" +
      "  +[intellij]\n" +
      " +jetbrains\n" +
      " +org\n" +
      " +xUnit\n");
  }

  public void testUpdateWithNewDescriptor() throws Exception {
    buildStructure(myRoot);

    expand(getPath("/"));

    assertTree(
      "-/\n" +
      " +com\n" +
      " +jetbrains\n" +
      " +org\n" +
      " +xUnit\n");

    Node google = myStructure.getNodeFor(new NodeElement("jetbrains"));
    google.myElement.myName = "google";

    updateFromRoot();

    assertTree(
      "-/\n" +
      " +com\n" +
      " +google\n" +
      " +org\n" +
      " +xUnit\n");
  }

  public void testDeferredSelection() throws Exception {
    buildStructure(myRoot, false);

    final Ref<Boolean> queued = new Ref<>(false);
    final Ref<Boolean> intellijSelected = new Ref<>(false);
    final Ref<Boolean> jetbrainsSelected = new Ref<>(false);
    invokeLaterIfNeeded(() -> {
      try {
        getBuilder().select(new NodeElement("intellij"), () -> intellijSelected.set(true), true);
        queued.set(true);
      }
      catch (Exception e) {
        e.printStackTrace();
        fail();
      }
    });

    new WaitFor() {
      @Override
      protected boolean condition() {
        return queued.get();
      }
    };

    assertTrue(getBuilder().getUi().isIdle());
    assertTreeNow("+null\n");
    assertNull(((DefaultMutableTreeNode)notNull(getBuilder().getTreeModel()).getRoot()).getUserObject());

    invokeLaterIfNeeded(() -> {
      getBuilder().getUi().activate(true);
      getBuilder().select(new NodeElement("jetbrains"), () -> jetbrainsSelected.set(true), true);
    });

    waitBuilderToCome(object -> intellijSelected.get() && jetbrainsSelected.get());

    assertTree("-/\n" +
               " -com\n" +
               "  +[intellij]\n" +
               " +[jetbrains]\n" +
               " +org\n" +
               " +xUnit\n");
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

    getBuilder().expand(elements[index], () -> expandNext(elements, index + 1, indicator, callback));
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
    expand(getPath("xUnit"));

    assertTree("-/\n" +
               " -com\n" +
               "  +intellij\n" +
               " -jetbrains\n" +
               "  +fabrique\n" +
               " -org\n" +
               "  +eclipse\n" +
               " -xUnit\n" +
               "  runner\n");

    runAndInterrupt(new MyRunnable() {
      @Override
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

    doAndWaitForBuilder(() -> getBuilder().getUi().activate(true));

    buildNode("Messages", false);

    assertTree("-/\n" +
               " -Messages\n" +
               "  -File 0\n" +
               "   message 1 for 0\n" +
               "   message 2 for 0\n" +
               "  -File 1\n" +
               "   message 1 for 1\n" +
               "   message 2 for 1\n");

    buildSiblings(msg, 2, 1000, () -> getBuilder().queueUpdate(), null);

    waitBuilderToCome();
  }

  private void buildSiblings(final Node node,
                             final int start,
                             final int end,
                             @Nullable final Runnable eachRunnable,
                             @Nullable final Runnable endRunnable) throws InvocationTargetException, InterruptedException {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
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

  private void runAndInterrupt(final Runnable action,
                               final String interruptAction,
                               final Object interruptElement,
                               final Interruption interruption) throws Exception {
    myElementUpdate.clear();

    final boolean[] wasInterrupted = new boolean[]{false};
    myElementUpdateHook = new ElementUpdateHook() {
      @Override
      public void onElementAction(String action, Object element) {
        boolean toInterrupt = element.equals(interruptElement) && action.equals(interruptAction);

        if (wasInterrupted[0]) {
          if (myCancelRequest == null) {
            getBuilder().getUi().getStatus();
            final String message = "Not supposed to be update after interruption request: action=" + action + " element=" + element +
                                   " interruptAction=" + interruptAction + " interruptElement=" + interruptElement;
            myCancelRequest = new AssertionError(message);
          }
        }
        else if (toInterrupt) {
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
               " +xUnit\n");
    assertUpdates("/: update getChildren\n" +
                  "com: update getChildren\n" +
                  "eclipse: update\n" +
                  "fabrique: update\n" +
                  "intellij: update\n" +
                  "jetbrains: update getChildren\n" +
                  "org: update getChildren\n" +
                  "runner: update\n" +
                  "xUnit: update getChildren");

    collapsePath(getPath("/"));
    assertTree("+/\n");
    assertUpdates("");

    expand(getPath("/"));
    assertTree("-/\n" +
               " +com\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xUnit\n");
    assertUpdates("/: update getChildren\n" +
                  "com: update getChildren\n" +
                  "eclipse: update\n" +
                  "fabrique: update\n" +
                  "intellij: update\n" +
                  "jetbrains: update getChildren\n" +
                  "org: update getChildren\n" +
                  "runner: update\n" +
                  "xUnit: update getChildren");

    updateFromRoot();
    assertTree("-/\n" +
               " +com\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xUnit\n");

    assertUpdates("/: update getChildren\n" +
                  "com: update getChildren\n" +
                  "eclipse: update\n" +
                  "fabrique: update\n" +
                  "intellij: update\n" +
                  "jetbrains: update getChildren\n" +
                  "org: update getChildren\n" +
                  "runner: update\n" +
                  "xUnit: update getChildren");
  }

  public void testQueryWhenUpdatingPresentation() throws Exception {
    buildStructure(myRoot);
    expand(getPath("/"));
    assertTree("-/\n" +
               " +com\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xUnit\n");

    myElementUpdate.clear();

    updateFromRoot(false);

    assertUpdates("/: update\n" +
                  "com: update\n" +
                  "jetbrains: update\n" +
                  "org: update\n" +
                  "xUnit: update");
  }

  public void testInfiniteUpdatingWhenReQueueingUpdates() throws Exception {
    buildStructure(myRoot, false);
    myCom.addChild("ibm").addChild("alphaWorks");
    myCom.addChild("apple").addChild("cocoa");

    doAndWaitForBuilder(() -> getBuilder().getUi().activate(true));

    buildNode("/", false);
    assertTree("-/\n" +
               " +com\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xUnit\n");

    myElementUpdateHook = new ElementUpdateHook() {
      @SuppressWarnings("deprecation")
      @Override
      public void onElementAction(String action, Object element) {
        if (new NodeElement("apple").equals(element) && "getChildren".equals(action)) {
          notNull(getBuilder().getUpdater()).addSubtreeToUpdate(new TreeUpdatePass(notNull(findNode("ibm", false))).setUpdateStamp(1));
          notNull(getBuilder().getUpdater()).addSubtreeToUpdate(new TreeUpdatePass(notNull(findNode("intellij", false))).setUpdateStamp(1));
        }
      }
    };

    doAndWaitForBuilder(() -> myTree.expandPath(getPath("com")));

    assertTree("-/\n" +
               " -com\n" +
               "  +apple\n" +
               "  +ibm\n" +
               "  +intellij\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xUnit\n");
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
               " +xUnit\n");

    assertUpdates("/: update getChildren\n" +
                  "com: update getChildren\n" +
                  "eclipse: update\n" +
                  "fabrique: update (2) getChildren\n" +
                  "ide: update getChildren\n" +
                  "intellij: update\n" +
                  "jetbrains: update getChildren\n" +
                  "org: update getChildren\n" +
                  "runner: update\n" +
                  "xUnit: update getChildren");
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
               " +xUnit\n");

    assertUpdates("/: update (2) getChildren\n" +
                  "com: update getChildren\n" +
                  "eclipse: update\n" +
                  "intellij: update\n" +
                  "jetbrains: update\n" +
                  "org: update getChildren\n" +
                  "runner: update\n" +
                  "xUnit: update getChildren");

    expand(getPath("jetbrains"));
    expand(getPath("fabrique"));

    assertTree("-/\n" +
               " +com\n" +
               " -jetbrains\n" +
               "  -fabrique\n" +
               "   +ide\n" +
               " +org\n" +
               " +xUnit\n");

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
               " +xUnit\n");

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
               " +xUnit\n");

    assertUpdates("/: update (2) getChildren\n" +
                  "com: update getChildren\n" +
                  "eclipse: update\n" +
                  "fabrique: update (2) getChildren\n" +
                  "ide: update\n" +
                  "intellij: update\n" +
                  "jetbrains: update getChildren\n" +
                  "org: update getChildren\n" +
                  "runner: update\n" +
                  "xUnit: update getChildren");

    expand(getPath("fabrique"));
    assertTree("-/\n" +
               " +com\n" +
               " -jetbrains\n" +
               "  -fabrique\n" +
               "   ide\n" +
               " +org\n" +
               " +xUnit\n");
    assertUpdates("ide: update getChildren");

    buildNode("com", false);
    assertTree("-/\n" +
               " -com\n" +
               "  +intellij\n" +
               " -jetbrains\n" +
               "  -fabrique\n" +
               "   ide\n" +
               " +org\n" +
               " +xUnit\n");

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
               " +xUnit\n");

    assertUpdates("openapi: update");
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
               " +xUnit\n");

    myStructure.addLeaf(new NodeElement("intellij"));

    updateFrom(new NodeElement("com"));

    assertTree("-/\n" +
               " -com\n" +
               "  [intellij]\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xUnit\n");

    myStructure.removeLeaf(new NodeElement("intellij"));
    updateFrom(new NodeElement("com"));

    assertTree("-/\n" +
               " -com\n" +
               "  +[intellij]\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xUnit\n");

    expand(getPath("intellij"));

    assertTree("-/\n" +
               " -com\n" +
               "  -[intellij]\n" +
               "   openapi\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xUnit\n");
  }

  public void testSorting() throws Exception {
    buildStructure(myRoot);
    assertSorted("");

    buildNode("/", false);
    assertTree("-/\n" +
               " +com\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xUnit\n");

    assertSorted("/\n" +
                 "com\n" +
                 "jetbrains\n" +
                 "org\n" +
                 "xUnit");

    updateFromRoot();
    assertTree("-/\n" +
               " +com\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xUnit\n");
    assertSorted("/\n" +
                 "com\n" +
                 "jetbrains\n" +
                 "org\n" +
                 "xUnit");

    updateFrom(new NodeElement("/"), false);
    assertTree("-/\n" +
               " +com\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xUnit\n");
    assertSorted("");

    expand(getPath("com"));
    assertTree("-/\n" +
               " -com\n" +
               "  +intellij\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xUnit\n");
    assertSorted("intellij");
  }

  public void testResorting() throws Exception {
    final boolean[] invert = new boolean[]{false};
    NodeDescriptor.NodeComparator<NodeDescriptor> c = new NodeDescriptor.NodeComparator<NodeDescriptor>() {
      @Override
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
               " +xUnit\n");

    updateFromRoot();
    updateFromRoot();
    updateFromRoot();

    assertTrue(getMyBuilder().getUi().myOwnComparatorStamp > c.getStamp());
    invert[0] = true;
    c.incStamp();

    updateFrom(new NodeElement("/"), false);
    assertTree("-/\n" +
               " +xUnit\n" +
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
               " +xUnit\n");

    removeFromParentButKeepRef(new NodeElement("openapi"));

    updateFromRoot();

    assertTree("-/\n" +
               " -com\n" +
               "  [intellij]\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xUnit\n");
  }

  public void testElementMove1() throws Exception {
    assertMove(() -> {
      notNull(getBuilder().getUpdater()).addSubtreeToUpdateByElement(new NodeElement("com"));
      notNull(getBuilder().getUpdater()).addSubtreeToUpdateByElement(new NodeElement("jetbrains"));
    });
  }

  public void testElementMove2() throws Exception {
    assertMove(() -> {
      notNull(getBuilder().getUpdater()).addSubtreeToUpdateByElement(new NodeElement("jetbrains"));
      notNull(getBuilder().getUpdater()).addSubtreeToUpdateByElement(new NodeElement("com"));
    });
  }

  public void testSelectionOnDelete() throws Exception {
    doTestSelectionOnDelete(false);
  }

  public void testSelectionOnDeleteButKeepRef() throws Exception {
    doTestSelectionOnDelete(true);
  }

  public void testMultipleSelectionOnDelete() throws Exception {
    buildStructure(myRoot);
    select(new NodeElement("fabrique"), false);
    select(new NodeElement("openapi"), true);
    select(new NodeElement("runner"), true);
    select(new NodeElement("eclipse"), true);

    assertTree("-/\n" +
                  " -com\n" +
                  "  -intellij\n" +
                  "   [openapi]\n" +
                  " -jetbrains\n" +
                  "  +[fabrique]\n" +
                  " -org\n" +
                  "  +[eclipse]\n" +
                  " -xUnit\n" +
                  "  [runner]\n");

    myStructure.getNodeFor(new NodeElement("runner")).delete();
    myStructure.getNodeFor(new NodeElement("eclipse")).delete();
    myStructure.getNodeFor(new NodeElement("openapi")).delete();

    updateFromRoot();

    assertTree("-/\n" +
                  " -com\n" +
                  "  intellij\n" +
                  " -jetbrains\n" +
                  "  +[fabrique]\n" +
                  " org\n" +
                  " xUnit\n");

    myStructure.getNodeFor(new NodeElement("fabrique")).delete();

    updateFromRoot();
    assertTree("-/\n" +
                      " -com\n" +
                      "  intellij\n" +
                      " [jetbrains]\n" +
                      " org\n" +
                      " xUnit\n");
  }

  public void testRevalidateStructure() throws Exception {
    final NodeElement com = new NodeElement("com");
    final NodeElement actionSystem = new NodeElement("actionSystem");
    actionSystem.setForcedParent(com);

    final NodeElement fabrique = new NodeElement("fabrique");
    final NodeElement ide = new NodeElement("ide");
    fabrique.setForcedParent(myRoot.getElement());

    doAndWaitForBuilder(() -> {
      myRoot.addChild(com).addChild(actionSystem);
      myRoot.addChild(fabrique).addChild(ide);
      getBuilder().getUi().activate(true);
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

    myStructure.setReValidator(new ReValidator() {
      @Nullable
      @Override
      public AsyncResult<Object> revalidate(NodeElement element) {
        if (element == actionSystem) {
          return AsyncResult.done(newActionSystem);
        }
        else if (element == fabrique) {
          return AsyncResult.done(newFabrique);
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

  public void testNoReValidationIfInvalid() throws Exception {
    buildStructure(myRoot);

    final NodeElement intellij = new NodeElement("intellij");
    buildNode(intellij, true);

    assertTree("-/\n" + " -com\n" + "  +[intellij]\n" + " +jetbrains\n" + " +org\n" + " +xUnit\n");

    removeFromParentButKeepRef(new NodeElement("intellij"));
    myValidator = new Validator() {
      @Override
      public boolean isValid(Object element) {
        return !element.equals(intellij);
      }
    };
    final Ref<Object> reValidatedElement = new Ref<>();
    myStructure.setReValidator(new ReValidator() {
      @Nullable
      @Override
      public AsyncResult<Object> revalidate(NodeElement element) {
        reValidatedElement.set(element);
        return null;
      }
    });

    updateFromRoot();

    assertTree("-/\n" +
               " [com]\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xUnit\n");
    assertNull(reValidatedElement.get() != null ? reValidatedElement.get().toString() : null, reValidatedElement.get());
  }

  private void doTestSelectionOnDelete(boolean keepRef) throws Exception {
    myComparator.setDelegate(new NodeDescriptor.NodeComparator<NodeDescriptor>() {
      @Override
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
               " +xUnit\n" +
               " [toDelete]\n");

    if (keepRef) {
      removeFromParentButKeepRef(new NodeElement("toDelete"));
    } else {
      myStructure.getNodeFor(new NodeElement("toDelete")).delete();
    }

    getMyBuilder().addSubtreeToUpdateByElement(new NodeElement("/"));

    assertTree("-/\n" + " +com\n" + " +jetbrains\n" + " +org\n" + " +[xUnit]\n");
  }

  public void testGetChildrenOnInvalidNode() throws Exception {
    buildStructure(myRoot);

    final Set<NodeElement> invalid = new HashSet<>();
    myValidator = new Validator<NodeElement>() {
      @Override
      public boolean isValid(NodeElement element) {
        return !invalid.contains(element);
      }
    };

    expand(getPath("/"));
    assertTree("-/\n" +
               " +com\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xUnit\n");

    invalid.add(new NodeElement("com"));

    updateFrom(new NodeElement("com"));
    assertTree("-/\n" +
               " com\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xUnit\n");

    updateFromRoot();
    assertTree("-/\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xUnit\n");
  }

  public void testSelectWhenUpdatesArePending() throws Exception {
    notNull(getBuilder().getUpdater()).setDelay(100);

    buildStructure(myRoot);

    buildNode("intellij", false);
    select(new Object[]{new NodeElement("intellij")}, false);
    assertTree("-/\n" + " -com\n" + "  -[intellij]\n" + "   openapi\n" + " +jetbrains\n" + " +org\n" + " +xUnit\n");

    myIntellij.addChild("ui");

    DefaultMutableTreeNode intellijNode = findNode("intellij", false);
    assertNotNull(intellijNode);
    assertTrue(myTree.isExpanded(new TreePath(intellijNode.getPath())));
    getMyBuilder().addSubtreeToUpdate(intellijNode);
    assertFalse(getMyBuilder().getUi().isReady());

    select(new Object[]{new NodeElement("ui")}, false);
    assertTree("-/\n" + " -com\n" + "  -intellij\n" + "   openapi\n" + "   [ui]\n" + " +jetbrains\n" + " +org\n" + " +xUnit\n");
  }

  public void testAddNewElementToLeafElementAlwaysShowPlus() throws Exception {
    myAlwaysShowPlus.add(new NodeElement("openapi"));

    buildStructure(myRoot);
    select(new Object[]{new NodeElement("openapi")}, false);

    assertTree("-/\n" + " -com\n" + "  -intellij\n" + "   +[openapi]\n" + " +jetbrains\n" + " +org\n" + " +xUnit\n");

    expand(getPath("openapi"));
    assertTree("-/\n" +
               " -com\n" +
               "  -intellij\n" +
               "   [openapi]\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xUnit\n");

    myOpenApi.addChild("ui");

    getMyBuilder().addSubtreeToUpdate(findNode("openapi", false));

    assertTree("-/\n" +
               " -com\n" +
               "  -intellij\n" +
               "   +[openapi]\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xUnit\n");
  }

  public void testAddNewElementToLeafElement() throws Exception {
    buildStructure(myRoot);
    select(new Object[]{new NodeElement("openapi")}, false);

    assertTree("-/\n" + " -com\n" + "  -intellij\n" + "   [openapi]\n" + " +jetbrains\n" + " +org\n" + " +xUnit\n");

    expand(getPath("openapi"));
    assertTree("-/\n" + " -com\n" + "  -intellij\n" + "   [openapi]\n" + " +jetbrains\n" + " +org\n" + " +xUnit\n");

    myOpenApi.addChild("ui");

    getMyBuilder().addSubtreeToUpdate(findNode("openapi", false));

    assertTree("-/\n" + " -com\n" + "  -intellij\n" + "   +[openapi]\n" + " +jetbrains\n" + " +org\n" + " +xUnit\n");
  }

  public void testUpdateAlwaysLeaf() throws Exception {
    myStructure.addLeaf(new NodeElement("openapi"));

    buildStructure(myRoot);
    buildNode(new NodeElement("intellij"), false);
    expand(getPath("intellij"));

    assertTree("-/\n" + " -com\n" + "  -intellij\n" + "   openapi\n" + " +jetbrains\n" + " +org\n" + " +xUnit\n");

    myElementUpdate.clear();

    invokeAndWaitIfNeeded(() -> getBuilder().queueUpdateFrom(new NodeElement("openapi"), false));
    waitBuilderToCome();

    assertUpdates("openapi: update");
  }

  private void assertMove(Runnable updateRoutine) throws Exception {
    buildStructure(myRoot);

    buildNode("intellij", true);
    assertTree("-/\n" +
               " -com\n" +
               "  +[intellij]\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xUnit\n");

    AbstractTreeBuilderTest.Node intellij = removeFromParentButKeepRef(new NodeElement("intellij"));
    notNull(myRoot.getChildNode("jetbrains")).addChild(intellij);

    updateRoutine.run();

    assertTree("-/\n" +
               " com\n" +
               " -jetbrains\n" +
               "  +fabrique\n" +
               "  +[intellij]\n" +
               " +org\n" +
               " +xUnit\n");
  }

  public void testChangeRootElement() throws Exception {
    buildStructure(myRoot);

    select(new NodeElement("com"), false);

    assertTree("-/\n" +
               " +[com]\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xUnit\n");

    myRoot = new Node(null, "root");
    myStructure.reInitRoot(myRoot);

    myRoot.addChild("com");

    updateFromRoot();
    assertTree("+root\n");
  }

  public void testQueueUpdate() throws Exception {
    buildStructure(myRoot);

    buildNode(new NodeElement("com"), false);

    assertTree("-/\n" +
               " -com\n" +
               "  +intellij\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xUnit\n");

    myCom.addChild("ibm");

    getBuilder().queueUpdateFrom(new NodeElement("com"), false, true);
    getBuilder().queueUpdateFrom(new NodeElement("/"), false, false);

    assertTree("-/\n" +
               " -com\n" +
               "  ibm\n" +
               "  +intellij\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xUnit\n");
  }

  public void testReleaseBuilderDuringUpdate() throws Exception {
    assertReleaseDuringBuilding("update", "fabrique", () -> {
      try {
        select(new NodeElement("ide"), false);
      }
      catch (Exception e) {
        myCancelRequest = e;
      }
    });
  }

  public void testStickyLoadingNodeIssue() throws Exception {
    buildStructure(myRoot);

    final boolean[] done = new boolean[] {false};
    invokeLaterIfNeeded(
      () -> getBuilder().select(new NodeElement("jetbrains"), () -> getBuilder().expand(new NodeElement("fabrique"), () -> done[0] = true)));

    waitBuilderToCome(o -> done[0]);

    assertTree("-/\n" + " +com\n" + " -[jetbrains]\n" + "  -fabrique\n" + "   ide\n" + " +org\n" + " +xUnit\n");
  }

  public void testUpdateCollapsedBuiltNode() throws Exception {
    buildStructure(myRoot, false);

    myCom.removeAll();
    myAlwaysShowPlus.add(new NodeElement("com"));

    activate();

    buildNode("/", false);
    assertTree("-/\n" +
               " +com\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xUnit\n");

    final DefaultMutableTreeNode com = findNode("com", false);
    assertNotNull(com);
    assertEquals(1, com.getChildCount());
    assertEquals(LoadingNode.getText(), com.getChildAt(0).toString());

    expand(getPath("com"));
    assertTree("-/\n" +
               " com\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xUnit\n");

    myCom.addChild(myIntellij);
    updateFrom(new NodeElement("com"));
    assertTree("-/\n" +
               " +com\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xUnit\n");
    assertEquals(1, com.getChildCount());
    assertEquals("intellij", com.getChildAt(0).toString());

    myCom.removeAll();
    updateFrom(new NodeElement("com"));

    expand(getPath("com"));
    assertTree("-/\n" +
               " com\n" +
               " +jetbrains\n" +
               " +org\n" +
               " +xUnit\n");
  }

  public void testReleaseBuilderDuringGetChildren() throws Exception {
    assertReleaseDuringBuilding("getChildren", "fabrique", () -> {
      try {
        select(new NodeElement("ide"), false);
      }
      catch (Exception e) {
        myCancelRequest = e;
      }
    });
  }

  public void testAssertionOnInfiniteTree() throws Exception {
    final Node com = myRoot.addChild("com");
    final Node intellij = com.addChild("intellij");
    final Node com2 = intellij.addChild("com");
    final Node idea = com2.addChild("idea");

    idea.myElement.setForcedParent(com2.myElement);
    com2.myElement.setForcedParent(intellij.myElement);
    intellij.myElement.setForcedParent(com.myElement);
    com.myElement.setForcedParent(myRoot.myElement);

    activate();

    try {
      System.err.println("The following error is part of the test, no need to fix it");
      buildNode("idea", true);
    }
    catch (AssertionFailedError e) {
    }

    assertTrue(getBuilder().getUi().isReady());
  }

  private void assertReleaseDuringBuilding(final String actionAction, final Object actionElement, Runnable buildAction) throws Exception {
    buildStructure(myRoot);

    myElementUpdateHook = new ElementUpdateHook() {
      @Override
      public void onElementAction(String action, Object element) {
        if (!element.toString().equals(actionElement.toString())) return;

        Runnable runnable = () -> {
          myReadyRequest = true;
          Disposer.dispose(getBuilder());
        };

        if (actionAction.equals(action)) {
          if (getBuilder().getUi().isPassthroughMode()) {
            runnable.run();
          } else {
            //noinspection SSBasedInspection
            SwingUtilities.invokeLater(runnable);
          }
        }
      }
    };

    buildAction.run();

    boolean released = new WaitFor(1000) {
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


  public static class PassThrough extends TreeUiTest {
    public PassThrough() {
      super(true);
    }

    @Override
    public void testSelectionGoesToParentWhenOnlyChildMoved2() throws Exception {
      //todo
    }

    @Override
    public void testQueryStructureWhenExpand() throws Exception {
      //todo
    }

    @Override
    public void testMoveElementToAdjacentEmptyParentWithSmartExpandAndSerialUpdateSubtrees() throws Exception {
      // doesn't make sense since pass-through mode is always serial, it doesn't queue for updates
    }

    @Override
    public void testElementMove1() throws Exception {
      //todo
    }

    @Override
    public void testClear() throws Exception {
      //todo
    }

    @Override
    public void testDoubleCancelUpdate() throws Exception {
      // doesn't make sense in pass-through mode
    }

    @Override
    public void testNoExtraJTreeModelUpdate() throws Exception {
      // doesn't make sense in pass-through mode
    }

    @Override
    public void testSelectWhenUpdatesArePending() throws Exception {
      // doesn't make sense in pass-through mode
    }

    @Override
    public void testBigTreeUpdate() throws Exception {
      // doesn't make sense in pass-through mode
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
      return 50;
    }

    @Override
    protected int getNodeDescriptorUpdateDelay() {
      return 50;
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
    protected int getNodeDescriptorUpdateDelay() {
      return 30;
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

    suite.addTestSuite(PassThrough.class);
    suite.addTestSuite(SyncUpdate.class);
    suite.addTestSuite(YieldingUpdate.class);
    suite.addTestSuite(BgLoadingSyncUpdate.class);

    // to run on suspecting of race conditions in background loading
    //suite.addTestSuite(VeryQuickBgLoadingSyncUpdate.class);
    //suite.addTestSuite(QuickBgLoadingSyncUpdate.class);

    return suite;
  }

  private abstract static class MyRunnable implements Runnable {
    @Override
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
