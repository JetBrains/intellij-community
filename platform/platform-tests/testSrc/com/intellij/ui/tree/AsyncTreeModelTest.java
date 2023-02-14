// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.TestApplicationManager;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.concurrency.Invoker;
import com.intellij.util.concurrency.InvokerSupplier;
import com.intellij.util.ui.tree.AbstractTreeModel;
import com.intellij.util.ui.tree.TreeModelAdapter;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.junit.Before;
import org.junit.Test;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.intellij.diagnostic.ThreadDumper.dumpThreadsToString;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.*;

public final class AsyncTreeModelTest {
  /**
   * A bigger threshold increases a probability of restarting current task.
   */
  private static final double THRESHOLD = .001;
  /**
   * Set to true to print some debugging information.
   */
  private static final boolean PRINT = false;

  @Before
  public void setUp() {
    TestApplicationManager.getInstance();
  }

  @Test
  public void testAggressiveUpdating() {
    testBackgroundThread(() -> null, test -> test.updateModelAndWait(model -> {
      for (int i = 0; i < 10000; i++) ((DefaultTreeModel)model).setRoot(createRoot());
    }, test::done), false, 0);
  }

  @Test
  public void testProcessingNPE() {
    Disposable disposable = Disposer.newDisposable();
    AsyncTreeModel model = new AsyncTreeModel(new DefaultTreeModel(new DefaultMutableTreeNode()), disposable);
    try {
      assert !model.isProcessing() : "created model should not update content";
    }
    finally {
      Disposer.dispose(disposable);
    }
  }

  @Test
  public void testNullRoot() {
    testAsync(() -> null, test
      -> testPathState0(test.tree, ()
      -> test.updateModelAndWait(model -> ((DefaultTreeModel)model).setRoot(createRoot()), ()
      -> testPathState1(test.tree, test::done))));
  }

  @Test
  public void testRootOnly() {
    testAsync(AsyncTreeModelTest::createRoot, test
      -> testPathState1(test.tree, ()
      -> test.updateModelAndWait(model -> ((DefaultTreeModel)model).setRoot(null), ()
      -> testPathState0(test.tree, test::done))));
  }

  @Test
  public void testRootOnlyUpdate() {
    testRootOnlyUpdate(false);
    testRootOnlyUpdate(true);
  }

  private static void testRootOnlyUpdate(boolean mutable) {
    TreeNode first = new Node("root", mutable);
    TreeNode second = new Node("root", mutable);
    assert mutable == first.equals(second);
    assert first != second : "both nodes should not be the same";
    testAsync(() -> first, test
      -> testPathState1(test.tree, ()
      -> testRootOnlyUpdate(test, first, ()
      -> test.updateModelAndWait(model -> ((DefaultTreeModel)model).setRoot(second), ()
      -> testPathState1(test.tree, ()
      -> testRootOnlyUpdate(test, second, test::done))))));
  }

  private static void testRootOnlyUpdate(@NotNull ModelTest test, @NotNull TreeNode expected, @NotNull Runnable task) {
    Object actual = test.tree.getModel().getRoot();
    assert expected.equals(actual) : "expected node should be equal to the tree root";
    assert expected == actual : "expected node should be the same";
    task.run();
  }

  @Test
  public void testChildrenUpdate() {
    ArrayList<TreePath> list = new ArrayList<>();
    testAsync(AsyncTreeModelTest::createMutableRoot, test
      -> TreeUtil.expandAll(test.tree, ()
      -> testPathState(test.tree, "   +'root'\n" + MUTABLE_CHILDREN, ()
      -> collectTreePaths(test.tree, list, ()
      -> test.updateModelAndWait(model -> ((DefaultTreeModel)model).setRoot(createMutableRoot()), ()
      -> testPathState(test.tree, "   +'root'\n" + MUTABLE_CHILDREN, ()
      -> checkTreePaths(test.tree, list, test::done)))))));
  }

  private static void collectTreePaths(@NotNull JTree tree, @NotNull List<? super TreePath> list, @NotNull Runnable task) {
    list.clear();
    forEachRow(tree, list::add);
    task.run();
  }

  private static void checkTreePaths(@NotNull JTree tree, @NotNull List<TreePath> list, @NotNull Runnable task) {
    Iterator<TreePath> iterator = list.iterator();
    forEachRow(tree, path -> {
      assertTrue(iterator.hasNext());
      assertTreePath(path, iterator.next());
    });
    assertFalse(iterator.hasNext());
    task.run();
  }

  private static void assertTreePath(@NotNull TreePath expected, @NotNull TreePath actual) {
    assertEquals("expected path should be equal to the actual path", expected, actual);
    // do no validate root node, because it is not updated in Swing's viewable row list
    if (expected.getParentPath() == null && actual.getParentPath() == null) return;
    assertComponent(expected.getLastPathComponent(), actual.getLastPathComponent());
    assertTreePath(expected.getParentPath(), actual.getParentPath());
  }

  private static void assertComponent(@NotNull Object expected, @NotNull Object actual) {
    assertEquals("expected node should be equal to the actual node", expected, actual);
    assertNotSame(expected, actual);
  }

  @NotNull
  private static TreeNode createMutableRoot() {
    return new Node(true, "root",
                    new Node(true, "color", "red", "green", "blue"),
                    new Node(true, "greek", "alpha", "beta", "gamma"));
  }

  private static final String MUTABLE_CHILDREN
    = """
         +'color'
            'red'
            'green'
            'blue'
         +'greek'
            'alpha'
            'beta'
            'gamma'
    """;

  @Test
  public void testChildren() {
    TreeNode color = createColorNode();
    TreeNode digit = createDigitNode();
    TreeNode greek = createGreekNode();
    TreeNode root = new Node("root", color, digit, greek);
    TreePath path = new TreePath(root);
    testAsync(() -> root, test
      -> testPathState(test.tree, "   +'root'\n" + CHILDREN, ()
      -> test.collapse(path, ()
      -> testPathState1(test.tree, ()
      -> test.setRootVisible(false, ()
      -> testPathState0(test.tree, ()
      -> test.expand(path, ()
      -> testPathState(test.tree, CHILDREN, ()
      -> test.expand(path.pathByAddingChild(color), ()
      -> testPathState(test.tree, CHILDREN_COLOR, ()
      -> test.expand(path.pathByAddingChild(greek), ()
      -> testPathState(test.tree, CHILDREN_COLOR_GREEK, ()
      -> test.collapse(path, ()
      -> testPathState0(test.tree, ()
      -> test.setRootVisible(true, ()
      -> testPathState1(test.tree, ()
      -> test.expand(path, ()
      -> testPathState(test.tree, "   +'root'\n" + CHILDREN_COLOR_GREEK, test::done))))))))))))))))));
  }

  private static final String CHILDREN
    = """
          'color'
          'digit'
          'greek'
    """;
  private static final String CHILDREN_COLOR
    = """
         +'color'
            'red'
            'green'
            'blue'
          'digit'
          'greek'
    """;
  private static final String CHILDREN_COLOR_GREEK
    = """
         +'color'
            'red'
            'green'
            'blue'
          'digit'
         +'greek'
            'alpha'
            'beta'
            'gamma'
            'delta'
            'epsilon'
    """;

  @Test
  public void testChildrenResolve() {
    Node node = new Node("node");
    Node root = new Node("root", new Node("upper", new Node("middle", new Node("lower", node))));
    TreePath tp = TreePathUtil.convertArrayToTreePath(node.getPath());
    testAsync(() -> root, test
      -> testPathState(test.tree, "   +'root'\n      'upper'\n", ()
      -> test.resolve(tp, path
      -> test.expand(path.getParentPath(), () // expand parent path because leaf nodes are ignored
      -> testPathState(test.tree, "   +'root'\n     +'upper'\n       +'middle'\n         +'lower'\n            'node'\n", test::done)))));
  }

  @Test
  public void testChildrenVisit() {
    Node node = new Node("node");
    Node root = new Node("root", new Node("upper", new Node("middle", new Node("lower", node))));
    TreePath tp = TreePathUtil.convertArrayToTreePath(node.getPath(), Object::toString);
    testAsync(() -> root, test
      -> testPathState(test.tree, "   +'root'\n      'upper'\n", ()
      -> test.visit(new TreeVisitor.ByTreePath<>(tp, Object::toString), true, path
      -> test.expand(path.getParentPath(), () // expand parent path because leaf nodes are ignored
      -> testPathState(test.tree, "   +'root'\n     +'upper'\n       +'middle'\n         +'lower'\n            'node'\n", test::done)))));
  }

  @Test
  public void testChildrenVisitWithoutLoading() {
    Node node = new Node("node");
    Node root = new Node("root", new Node("upper", new Node("middle", new Node("lower", node))));
    TreePath tp = TreePathUtil.convertArrayToTreePath(node.getPath(), Object::toString);
    testAsync(() -> root, test
      -> testPathState(test.tree, "   +'root'\n      'upper'\n", ()
      -> test.visit(new TreeVisitor.ByTreePath<>(tp, Object::toString), false, path -> {
      assertNull(path);
      test.done();
    })));
  }

  @Test
  public void testCollapsedNodeUpdateIfChildrenNotLoaded() {
    TreeNode color = createColorNode();
    TreeNode digit = createDigitNode();
    TreeNode greek = createGreekNode();
    TreeNode root = new Node("root", color, digit, greek);
    TreePath path = new TreePath(root).pathByAddingChild(greek);
    testAsync(() -> root, test
      -> testPathState(test.tree, "   +'root'\n" + CHILDREN, ()
      -> test.fireStructureChanged(path)));
  }

  @Test
  public void testCollapsedNodeUpdateIfChildrenLoaded() {
    TreeNode color = createColorNode();
    TreeNode digit = createDigitNode();
    TreeNode greek = createGreekNode();
    TreeNode root = new Node("root", color, digit, greek);
    TreePath path = new TreePath(root);
    testAsync(() -> root, test
      -> testPathState(test.tree, "   +'root'\n" + CHILDREN, ()
      -> test.collapse(path, ()
      -> testPathState1(test.tree, ()
      -> test.fireStructureChanged(path)))));
  }

  @Test
  public void testExpandedNodeUpdateIfChildrenLoaded() {
    TreeNode color = createColorNode();
    TreeNode digit = createDigitNode();
    TreeNode greek = createGreekNode();
    TreeNode root = new Node("root", color, digit, greek);
    TreePath path = new TreePath(root);
    testAsync(() -> root, test
      -> testPathState(test.tree, "   +'root'\n" + CHILDREN, ()
      -> test.fireStructureChanged(path)));
  }

  @NotNull
  private static TreeNode createRoot() {
    return new Node("root");
  }

  @NotNull
  private static TreeNode createColorNode() {
    return new Node("color", "red", "green", "blue");
  }

  @NotNull
  private static TreeNode createDigitNode() {
    return new Node("digit", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine");
  }

  @NotNull
  private static TreeNode createGreekNode() {
    return new Node("greek", "alpha", "beta", "gamma", "delta", "epsilon");
  }

  private static void testPathState(@NotNull JTree tree, @NotNull String state, @NotNull Runnable task) {
    assertEquals("unexpected tree state", state, getPathState(tree));
    task.run();
  }

  private static void testPathState0(@NotNull JTree tree, @NotNull Runnable task) {
    assert 0 == tree.getRowCount() : "tree should have no nodes";
    testPathState(tree, "", task);
  }

  private static void testPathState1(@NotNull JTree tree, @NotNull Runnable task) {
    assert 1 == tree.getRowCount() : "tree should have only one node";
    testPathState(tree, "    'root'\n", task);
  }

  private static void forEachRow(JTree tree, Consumer<? super TreePath> consumer) {
    int count = tree.getRowCount();
    for (int row = 0; row < count; row++) {
      consumer.accept(tree.getPathForRow(row));
    }
  }

  @NotNull
  private static String getPathState(JTree tree) {
    StringBuilder sb = new StringBuilder();
    forEachRow(tree, path -> addState(sb, tree, path));
    return sb.toString();
  }

  private static void addState(StringBuilder sb, JTree tree, TreePath path) {
    boolean expanded = tree.isExpanded(path);
    boolean selected = tree.isPathSelected(path);
    sb.append(selected ? '[' : ' ');
    int count = path.getPathCount();
    while (0 < count--) sb.append("  ");
    sb.append(expanded ? '+' : ' ');
    sb.append(path.getLastPathComponent());
    if (selected) sb.append(']');
    sb.append("\n");
  }

  private static void testAsync(Supplier<? extends TreeNode> root, @NotNull Consumer<? super ModelTest> consumer) {
    testAsync(root, consumer, false);
    testAsync(root, consumer, true);
  }

  private static void testAsync(Supplier<? extends TreeNode> root, @NotNull Consumer<? super ModelTest> consumer, boolean showLoadingNode) {
    testEventDispatchThread(root, consumer, showLoadingNode);
    testBackgroundThread(root, consumer, showLoadingNode);
    testBackgroundPool(root, consumer, showLoadingNode);
  }

  private static void testEventDispatchThread(Supplier<? extends TreeNode> root, Consumer<? super ModelTest> consumer, boolean showLoadingNode) {
    testEventDispatchThread(root, consumer, showLoadingNode, TreeTest.FAST);
    testEventDispatchThread(root, consumer, showLoadingNode, TreeTest.SLOW);
  }

  private static void testEventDispatchThread(Supplier<? extends TreeNode> root, Consumer<? super ModelTest> consumer, boolean showLoadingNode, int delay) {
    new AsyncTest(showLoadingNode, new EventDispatchThreadModel(delay, root)).start(consumer, getSecondsToWait(delay));
  }

  private static void testBackgroundThread(Supplier<? extends TreeNode> root, Consumer<? super ModelTest> consumer, boolean showLoadingNode) {
    testBackgroundThread(root, consumer, showLoadingNode, TreeTest.FAST);
    testBackgroundThread(root, consumer, showLoadingNode, TreeTest.SLOW);
  }

  private static void testBackgroundThread(Supplier<? extends TreeNode> root, Consumer<? super ModelTest> consumer, boolean showLoadingNode, int delay) {
    if (consumer != null) new AsyncTest(showLoadingNode, new BackgroundThreadModel(delay, root)).start(consumer, getSecondsToWait(delay));
  }

  private static void testBackgroundPool(Supplier<? extends TreeNode> root, Consumer<? super ModelTest> consumer, boolean showLoadingNode) {
    testBackgroundPool(root, consumer, showLoadingNode, TreeTest.FAST);
    testBackgroundPool(root, consumer, showLoadingNode, TreeTest.SLOW);
  }

  private static void testBackgroundPool(Supplier<? extends TreeNode> root, Consumer<? super ModelTest> consumer, boolean showLoadingNode, int delay) {
    if (consumer != null) new AsyncTest(showLoadingNode, new BackgroundPoolModel(delay, root)).start(consumer, getSecondsToWait(delay));
  }

  private static void printTime(String prefix, long time) {
    time = System.currentTimeMillis() - time;
    if (PRINT) System.out.println(prefix + time + " ms");
  }

  private static void invokeWhenProcessingDone(@NotNull Runnable task, @NotNull AsyncTreeModel model, long time, int count) {
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(() -> {
      if (model.isProcessing()) {
        invokeWhenProcessingDone(task, model, time, 0);
      }
      else if (count < 2) {
        invokeWhenProcessingDone(task, model, time, count + 1);
      }
      else {
        printTime("wait for ", time);
        task.run();
      }
    });
  }

  /**
   * @param delay a delay used to create a slow tree model
   * @return a maximal time in seconds allowed for the test
   */
  private static int getSecondsToWait(int delay) {
    return delay + 20;
  }

  private static class ModelTest {
    private final AsyncPromise<String> promise = new AsyncPromise<>();
    private final TreeModel model;
    private volatile JTree tree;

    private ModelTest(long delay, Supplier<? extends TreeNode> root) {
      this(new SlowModel(delay, root));
    }

    private ModelTest(TreeModel model) {
      this.model = model;
    }

    protected TreeModel createModelForTree(TreeModel model, Disposable disposable) {
      return model;
    }

    void start(@NotNull Consumer<? super ModelTest> consumer, int seconds) {
      if (PRINT) System.out.println("start " + toString());
      assert !SwingUtilities.isEventDispatchThread() : "test should be started on the main thread";
      long time = System.currentTimeMillis();
      Disposable disposable = Disposer.newDisposable();

      runOnSwingThread(() -> {
        tree = new JTree(createModelForTree(model, disposable));
        TreeTestUtil.assertTreeUI(tree);
        runOnSwingThreadWhenProcessingDone(() -> consumer.accept(this));
      });
      try {
        promise.blockingGet(seconds, SECONDS);
      }
      catch (TimeoutException exception) {
        System.err.println(dumpThreadsToString());
        fail(seconds + " seconds is not enough for " + toString());
      }
      finally {
        Disposer.dispose(disposable);
        printTime("done in ", time);
        if (PRINT) System.out.println();
      }
    }

    void done() {
      promise.setResult(null);
    }

    @NotNull
    private Runnable wrap(@NotNull Runnable task) {
      return () -> {
        try {
          task.run();
        }
        catch (Throwable throwable) {
          promise.setError(throwable);
        }
      };
    }

    private void setRootVisible(boolean visible, @NotNull Runnable task) {
      tree.setRootVisible(visible);
      runOnSwingThreadWhenProcessingDone(task);
    }

    private void expand(@NotNull TreePath path, @NotNull Runnable task) {
      tree.expandPath(path);
      runOnSwingThreadWhenProcessingDone(task);
    }

    private void collapse(@NotNull TreePath path, @NotNull Runnable task) {
      tree.collapsePath(path);
      runOnSwingThreadWhenProcessingDone(task);
    }

    private void fireStructureChanged(@NotNull TreePath path) {
      runOnSwingThread(() -> {
        tree.getModel().addTreeModelListener(new TreeModelAdapter() {
          @Override
          protected void process(@NotNull TreeModelEvent event, @NotNull EventType type) {
            assertEquals("unexpected tree path", path, event.getTreePath());
            //noinspection SSBasedInspection
            SwingUtilities.invokeLater(ModelTest.this::done);
          }
        });
        runOnModelThread(() -> {
          TreeModelEvent event = new TreeModelEvent(model, path);
          TreeModelListener[] listeners = ((DefaultTreeModel)model).getTreeModelListeners();
          for (TreeModelListener listener : listeners) listener.treeStructureChanged(event);
        });
      });
    }

    private void updateModelAndWait(Consumer<? super TreeModel> consumer, @NotNull Runnable task) {
      runOnModelThread(() -> {
        consumer.accept(model);
        runOnSwingThreadWhenProcessingDone(task);
      });
    }

    private void runOnModelThread(@NotNull Runnable task) {
      if (model instanceof InvokerSupplier supplier) {
        supplier.getInvoker().invoke(wrap(task));
      }
      else {
        runOnSwingThread(task);
      }
    }

    private void runOnSwingThread(@NotNull Runnable task) {
      if (SwingUtilities.isEventDispatchThread()) {
        wrap(task).run();
      }
      else {
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(wrap(task));
      }
    }

    private void runOnSwingThreadWhenProcessingDone(@NotNull Runnable task) {
      TreeModel model = tree.getModel();
      if (model instanceof AsyncTreeModel) {
        invokeWhenProcessingDone(wrap(task), (AsyncTreeModel)model, System.currentTimeMillis(), 0);
      }
      else {
        runOnSwingThread(task);
      }
    }

    private void resolve(@NotNull TreePath path, @NotNull Consumer<? super TreePath> consumer) {
      AsyncTreeModel model = (AsyncTreeModel)tree.getModel();
      model.resolve(path).onError(promise::setError).onSuccess(consumer);
    }

    private void visit(@NotNull TreeVisitor visitor, boolean allowLoading, @NotNull Consumer<? super TreePath> consumer) {
      AsyncTreeModel model = (AsyncTreeModel)tree.getModel();
      model.accept(visitor, allowLoading).onError(promise::setError).onSuccess(consumer);
    }

    @Override
    public String toString() {
      return model.toString();
    }
  }

  private static final class AsyncTest extends ModelTest {
    private final boolean showLoadingNode;

    private AsyncTest(boolean showLoadingNode, TreeModel model) {
      super(model);
      this.showLoadingNode = showLoadingNode;
    }

    @Override
    protected TreeModel createModelForTree(TreeModel model, Disposable disposable) {
      return new AsyncTreeModel(model, showLoadingNode, disposable);
    }

    @Override
    public String toString() {
      String string = super.toString();
      if (showLoadingNode) string = "show loading node " + string;
      return string;
    }
  }

  private static class SlowModel extends DefaultTreeModel implements Disposable {
    private final long delay;

    private SlowModel(long delay, Supplier<? extends TreeNode> root) {
      super(root == null ? null : root.get());
      this.delay = delay;
    }

    private void pause() {
      if (this instanceof InvokerSupplier && THRESHOLD > Math.random()) {
        // sometimes throw an exception to cancel current operation
        if (PRINT) System.out.println("interrupt access to model:" + toString());
        throw new ProcessCanceledException();
      }
      if (delay > 0) {
        try {
          Thread.sleep(delay);
        }
        catch (InterruptedException ignored) {
        }
      }
    }

    @Override
    public final Object getRoot() {
      pause();
      return super.getRoot();
    }

    @Override
    public final Object getChild(Object parent, int index) {
      if (index == 0) pause(); // do not pause for every child
      return super.getChild(parent, index);
    }

    @Override
    public final int getChildCount(Object parent) {
      pause();
      return super.getChildCount(parent);
    }

    @Override
    public final boolean isLeaf(Object node) {
      pause();
      return super.isLeaf(node);
    }

    @Override
    public final int getIndexOfChild(Object parent, Object child) {
      pause();
      return super.getIndexOfChild(parent, child);
    }

    @Override
    public final void dispose() {
    }

    @Override
    public String toString() {
      return getClass().getSimpleName() + "+" + delay + "ms";
    }
  }

  private static final class EventDispatchThreadModel extends SlowModel implements InvokerSupplier {
    private final Invoker invoker = Invoker.forEventDispatchThread(this);

    private EventDispatchThreadModel(long delay, Supplier<? extends TreeNode> root) {
      super(delay, root);
    }

    @NotNull
    @Override
    public Invoker getInvoker() {
      return invoker;
    }
  }

  private static final class BackgroundThreadModel extends SlowModel implements InvokerSupplier {
    private final Invoker invoker = Invoker.forBackgroundThreadWithReadAction(this);

    private BackgroundThreadModel(long delay, Supplier<? extends TreeNode> root) {
      super(delay, root);
    }

    @NotNull
    @Override
    public Invoker getInvoker() {
      return invoker;
    }
  }

  private static final class BackgroundPoolModel extends SlowModel implements InvokerSupplier {
    private final Invoker invoker = Invoker.forBackgroundPoolWithReadAction(this);

    private BackgroundPoolModel(long delay, Supplier<? extends TreeNode> root) {
      super(delay, root);
    }

    @NotNull
    @Override
    public Invoker getInvoker() {
      return invoker;
    }
  }

  private static final class Node extends DefaultMutableTreeNode {
    private final boolean mutable;

    private Node(String content, boolean mutable) {
      this(mutable, content, ArrayUtilRt.EMPTY_OBJECT_ARRAY);
    }

    private Node(String content, Object... children) {
      this(false, content, children);
    }

    private Node(boolean mutable, Object content, Object... children) {
      super(content);
      this.mutable = mutable;
      for (Object child : children) {
        add(child instanceof MutableTreeNode
            ? (MutableTreeNode)child
            : new Node(mutable, child, ArrayUtilRt.EMPTY_OBJECT_ARRAY));
      }
    }

    @Override
    public int hashCode() {
      if (!mutable) return super.hashCode();
      Object content = getUserObject();
      return content == null ? 0 : content.hashCode();
    }

    @Override
    public boolean equals(Object object) {
      if (!mutable) return super.equals(object);
      if (object instanceof Node node) {
        if (node.mutable) return Objects.equals(getUserObject(), node.getUserObject());
      }
      return false;
    }

    @Override
    public String toString() {
      Object content = getUserObject();
      if (content == null) return "null";
      return "'" + content + "'";
    }
  }


  @Test
  public void testNodePreservingOnEventDispatchThread() {
    testNodePreservingOnEventDispatchThread(false);
    testNodePreservingOnEventDispatchThread(true);
  }

  private static void testNodePreservingOnEventDispatchThread(boolean showLoadingNode) {
    testNodePreserving(showLoadingNode, new GroupModel() {
      private final Invoker invoker = Invoker.forEventDispatchThread(this);

      @NotNull
      @Override
      public Invoker getInvoker() {
        return invoker;
      }
    });
  }

  @Test
  public void testNodePreservingOnBackgroundThread() {
    testNodePreservingOnBackgroundThread(false);
    testNodePreservingOnBackgroundThread(true);
  }

  private static void testNodePreservingOnBackgroundThread(boolean showLoadingNode) {
    testNodePreserving(showLoadingNode, new GroupModel() {
      private final Invoker invoker = Invoker.forBackgroundThreadWithReadAction(this);

      @NotNull
      @Override
      public Invoker getInvoker() {
        return invoker;
      }
    });
  }

  @Test
  public void testNodePreservingOnBackgroundPool() {
    testNodePreservingOnBackgroundPool(false);
    testNodePreservingOnBackgroundPool(true);
  }

  private static void testNodePreservingOnBackgroundPool(boolean showLoadingNode) {
    testNodePreserving(showLoadingNode, new GroupModel() {
      private final Invoker invoker = Invoker.forBackgroundPoolWithReadAction(this);

      @NotNull
      @Override
      public Invoker getInvoker() {
        return invoker;
      }
    });
  }

  private static void testNodePreserving(boolean showLoadingNode, @NotNull GroupModel model) {
    new AsyncTest(showLoadingNode, model).start(test -> testPathState(test.tree, "   +root\n      node\n", ()
      -> testNodePreserving(test, model, "first", ()
      -> testNodePreserving(test, model, "second", ()
      -> testNodePreserving(test, model, null, ()
      -> testNodePreserving(test, model, "third", test::done))))), 10);
  }

  private static void testNodePreserving(@NotNull ModelTest test, @NotNull GroupModel model, Object group, @NotNull Runnable task) {
    model.setGroup(group, () -> test.runOnSwingThreadWhenProcessingDone(() -> test.visit(path -> {
      test.tree.makeVisible(path);
      return TreeVisitor.Action.CONTINUE;
    }, true, done -> testPathState(test.tree, group != null
                                              ? "   +root\n     +" + group + "\n       +node\n          leaf\n"
                                              : "   +root\n     +node\n        leaf\n", task))));
  }

  static abstract class GroupModel extends AbstractTreeModel implements InvokerSupplier {
    private final Object myRoot = new StringBuilder("root");
    private final Object myNode = new StringBuilder("node");
    private final Object myLeaf = new StringBuilder("leaf");
    private volatile Object myGroup;

    public void setGroup(Object group, @NotNull Runnable task) {
      getInvoker().invoke(() -> {
        myGroup = group;
        treeStructureChanged(null, null, null);
        task.run();
      });
    }

    @Override
    public final Object getRoot() {
      return myRoot;
    }

    @Override
    public final Object getChild(Object parent, int index) {
      if (index == 0) {
        Object group = myGroup;
        if (group == null) {
          if (myRoot.equals(parent)) return myNode;
        }
        else {
          if (myRoot.equals(parent)) return group;
          if (group.equals(parent)) return myNode;
        }
        if (myNode.equals(parent)) return myLeaf;
      }
      throw new IllegalStateException();
    }

    @Override
    public final int getChildCount(Object parent) {
      Object group = myGroup;
      if (myRoot.equals(parent) || group != null && group.equals(parent)) return 1;
      if (myNode.equals(parent)) return 1;
      if (myLeaf.equals(parent)) return 0;
      throw new IllegalStateException();
    }

    @Override
    public final boolean isLeaf(Object node) {
      Object group = myGroup;
      if (myRoot.equals(node) || group != null && group.equals(node)) return false;
      if (myNode.equals(node)) return false;
      if (myLeaf.equals(node)) return true;
      throw new IllegalStateException();
    }

    @Override
    public final int getIndexOfChild(Object parent, Object child) {
      Object group = myGroup;
      if (group == null) {
        if (myRoot.equals(parent) && myNode.equals(child)) return 0;
      }
      else {
        if (myRoot.equals(parent) && group.equals(child)) return 0;
        if (group.equals(parent) && myNode.equals(child)) return 0;
      }
      if (myNode.equals(parent) && myLeaf.equals(child)) return 0;
      throw new IllegalStateException();
    }

    @Override
    public String toString() {
      return getClass().getName();
    }
  }
}
