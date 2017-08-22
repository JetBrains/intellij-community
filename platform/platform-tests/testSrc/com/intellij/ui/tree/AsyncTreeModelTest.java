/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ui.tree;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.concurrency.Invoker;
import com.intellij.util.concurrency.InvokerSupplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.junit.Test;

import javax.swing.*;
import javax.swing.tree.*;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.intellij.util.ArrayUtil.EMPTY_OBJECT_ARRAY;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class AsyncTreeModelTest {
  private final static boolean PRINT = false;

  @Test
  public void testAggressiveUpdating() {
    testBackgroundThread(() -> null, test -> test.updateModelAndWait(model -> {
      for (int i = 0; i < 10000; i++) model.setRoot(createRoot());
    }, test::done), false, 0);
  }

  @Test
  public void testNullRoot() {
    testAsync(() -> null, test
      -> testPathState0(test.tree, ()
      -> test.updateModelAndWait(model -> model.setRoot(createRoot()), ()
      -> testPathState1(test.tree, test::done))));
  }

  @Test
  public void testRootOnly() {
    testAsync(AsyncTreeModelTest::createRoot, test
      -> testPathState1(test.tree, ()
      -> test.updateModelAndWait(model -> model.setRoot(null), ()
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
      -> test.updateModelAndWait(model -> model.setRoot(second), ()
      -> testPathState1(test.tree, ()
      -> testRootOnlyUpdate(test, mutable ? first : second, test::done))))));
  }

  private static void testRootOnlyUpdate(@NotNull ModelTest test, @NotNull TreeNode expected, @NotNull Runnable task) {
    Object actual = test.tree.getModel().getRoot();
    assert expected.equals(actual) : "expected node should be equal to the tree root";
    assert expected == actual : "expected node should be the same";
    task.run();
  }

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
    = "      'color'\n" +
      "      'digit'\n" +
      "      'greek'\n";
  private static final String CHILDREN_COLOR
    = "     +'color'\n" +
      "        'red'\n" +
      "        'green'\n" +
      "        'blue'\n" +
      "      'digit'\n" +
      "      'greek'\n";
  private static final String CHILDREN_COLOR_GREEK
    = "     +'color'\n" +
      "        'red'\n" +
      "        'green'\n" +
      "        'blue'\n" +
      "      'digit'\n" +
      "     +'greek'\n" +
      "        'alpha'\n" +
      "        'beta'\n" +
      "        'gamma'\n" +
      "        'delta'\n" +
      "        'epsilon'\n";

  @Test
  public void testChildrenResolve() {
    Node node = new Node("node");
    Node root = new Node("root", new Node("upper", new Node("middle", new Node("lower", node))));
    TreePath tp = TreeUtil.convertArrayToTreePath(node.getPath());
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
    TreePath tp = TreeUtil.convertArrayToTreePath(node.getPath(), Object::toString);
    testAsync(() -> root, test
      -> testPathState(test.tree, "   +'root'\n      'upper'\n", ()
      -> test.visit(new TreeVisitor.PathFinder(tp, Object::toString), true, path
      -> test.expand(path.getParentPath(), () // expand parent path because leaf nodes are ignored
      -> testPathState(test.tree, "   +'root'\n     +'upper'\n       +'middle'\n         +'lower'\n            'node'\n", test::done)))));
  }

  @Test
  public void testChildrenVisitWithoutLoading() {
    Node node = new Node("node");
    Node root = new Node("root", new Node("upper", new Node("middle", new Node("lower", node))));
    TreePath tp = TreeUtil.convertArrayToTreePath(node.getPath(), Object::toString);
    testAsync(() -> root, test
      -> testPathState(test.tree, "   +'root'\n      'upper'\n", ()
      -> test.visit(new TreeVisitor.PathFinder(tp, Object::toString), false, path -> {
      assertNull(path);
      test.done();
    })));
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

  @NotNull
  private static String getPathState(JTree tree) {
    StringBuilder sb = new StringBuilder();
    int count = tree.getRowCount();
    for (int row = 0; row < count; row++) {
      addState(sb, tree, tree.getPathForRow(row));
    }
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

  private static void testAsync(Supplier<TreeNode> root, @NotNull Consumer<ModelTest> consumer) {
    testAsync(root, consumer, false);
    testAsync(root, consumer, true);
  }

  private static void testAsync(Supplier<TreeNode> root, @NotNull Consumer<ModelTest> consumer, boolean showLoadingNode) {
    testEventDispatchThread(root, consumer, showLoadingNode);
    testBackgroundThread(root, consumer, showLoadingNode);
    testBackgroundPool(root, consumer, showLoadingNode);
  }

  private static void testEventDispatchThread(Supplier<TreeNode> root, Consumer<ModelTest> consumer, boolean showLoadingNode) {
    testEventDispatchThread(root, consumer, showLoadingNode, 0);
    testEventDispatchThread(root, consumer, showLoadingNode, 10);
    testEventDispatchThread(root, consumer, showLoadingNode, 100);
  }

  private static void testEventDispatchThread(Supplier<TreeNode> root, Consumer<ModelTest> consumer, boolean showLoadingNode, int delay) {
    new AsyncTest(showLoadingNode, new EventDispatchThreadModel(delay, root)).start(consumer, delay + 10);
  }

  private static void testBackgroundThread(Supplier<TreeNode> root, Consumer<ModelTest> consumer, boolean showLoadingNode) {
    testBackgroundThread(root, consumer, showLoadingNode, 0);
    testBackgroundThread(root, consumer, showLoadingNode, 10);
    testBackgroundThread(root, consumer, showLoadingNode, 100);
  }

  private static void testBackgroundThread(Supplier<TreeNode> root, Consumer<ModelTest> consumer, boolean showLoadingNode, int delay) {
    if (consumer != null) new AsyncTest(showLoadingNode, new BackgroundThreadModel(delay, root)).start(consumer, delay + 10);
  }

  private static void testBackgroundPool(Supplier<TreeNode> root, Consumer<ModelTest> consumer, boolean showLoadingNode) {
    testBackgroundPool(root, consumer, showLoadingNode, 0);
    testBackgroundPool(root, consumer, showLoadingNode, 10);
    testBackgroundPool(root, consumer, showLoadingNode, 100);
  }

  private static void testBackgroundPool(Supplier<TreeNode> root, Consumer<ModelTest> consumer, boolean showLoadingNode, int delay) {
    if (consumer != null) new AsyncTest(showLoadingNode, new BackgroundPoolModel(delay, root)).start(consumer, delay + 10);
  }

  private static void printTime(long time, String postfix) {
    time = System.currentTimeMillis() - time;
    if (PRINT) System.out.println(time + postfix);
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
        printTime(time, "ms to wait");
        task.run();
      }
    });
  }

  private static class ModelTest {
    private final AsyncPromise<String> promise = new AsyncPromise<>();
    private final DefaultTreeModel model;
    private volatile JTree tree;

    private ModelTest(long delay, Supplier<TreeNode> root) {
      this(new SlowModel(delay, root));
    }

    private ModelTest(DefaultTreeModel model) {
      this.model = model;
    }

    protected TreeModel createModelForTree(TreeModel model) {
      return model;
    }

    void start(@NotNull Consumer<ModelTest> consumer, int seconds) {
      if (PRINT) System.out.println("start " + toString());
      assert !SwingUtilities.isEventDispatchThread() : "test should be started on the main thread";
      long time = System.currentTimeMillis();
      runOnSwingThread(() -> {
        //noinspection UndesirableClassUsage
        tree = new JTree(createModelForTree(model));
        runOnSwingThreadWhenProcessingDone(() -> consumer.accept(this));
      });
      try {
        promise.blockingGet(seconds, SECONDS);
      }
      finally {
        TreeModel model = tree.getModel();
        if (model instanceof Disposable) Disposer.dispose((Disposable)model);
        printTime(time, "ms to done");
      }
    }

    void done() {
      //noinspection unchecked
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

    private void updateModelAndWait(Consumer<DefaultTreeModel> consumer, @NotNull Runnable task) {
      runOnModelThread(() -> {
        consumer.accept(model);
        runOnSwingThreadWhenProcessingDone(task);
      });
    }

    private void runOnModelThread(@NotNull Runnable task) {
      if (model instanceof InvokerSupplier) {
        InvokerSupplier supplier = (InvokerSupplier)model;
        supplier.getInvoker().invokeLaterIfNeeded(wrap(task));
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

    private void resolve(@NotNull TreePath path, @NotNull Consumer<TreePath> consumer) {
      AsyncTreeModel model = (AsyncTreeModel)tree.getModel();
      model.resolve(path).rejected(promise::setError).done(consumer::accept);
    }

    private void visit(@NotNull TreeVisitor visitor, boolean allowLoading, @NotNull Consumer<TreePath> consumer) {
      AsyncTreeModel model = (AsyncTreeModel)tree.getModel();
      model.visit(visitor, allowLoading).rejected(promise::setError).done(consumer::accept);
    }

    @Override
    public String toString() {
      return model.toString();
    }
  }

  private static class AsyncTest extends ModelTest {
    private final boolean showLoadingNode;

    private AsyncTest(boolean showLoadingNode, DefaultTreeModel model) {
      super(model);
      this.showLoadingNode = showLoadingNode;
    }

    @Override
    protected TreeModel createModelForTree(TreeModel model) {
      return new AsyncTreeModel(model, showLoadingNode);
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

    private SlowModel(long delay, Supplier<TreeNode> root) {
      super(root == null ? null : root.get());
      this.delay = delay;
    }

    private void pause() {
      if (this instanceof InvokerSupplier && .9 < Math.random()) {
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
      pause();
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
    private final Invoker invoker = new Invoker.EDT(this);

    private EventDispatchThreadModel(long delay, Supplier<TreeNode> root) {
      super(delay, root);
    }

    @NotNull
    @Override
    public Invoker getInvoker() {
      return invoker;
    }
  }

  private static final class BackgroundThreadModel extends SlowModel implements InvokerSupplier {
    private final Invoker invoker = new Invoker.BackgroundThread(this);

    private BackgroundThreadModel(long delay, Supplier<TreeNode> root) {
      super(delay, root);
    }

    @NotNull
    @Override
    public Invoker getInvoker() {
      return invoker;
    }
  }

  private static final class BackgroundPoolModel extends SlowModel implements InvokerSupplier {
    private final Invoker invoker = new Invoker.BackgroundPool(this);

    private BackgroundPoolModel(long delay, Supplier<TreeNode> root) {
      super(delay, root);
    }

    @NotNull
    @Override
    public Invoker getInvoker() {
      return invoker;
    }
  }

  private static class Node extends DefaultMutableTreeNode {
    private final boolean mutable;

    private Node(Object content, boolean mutable) {
      this(content, mutable, EMPTY_OBJECT_ARRAY);
    }

    private Node(Object content, Object... children) {
      this(content, false, children);
    }

    private Node(Object content, boolean mutable, Object... children) {
      super(content);
      this.mutable = mutable;
      for (Object child : children) {
        add(child instanceof MutableTreeNode
            ? (MutableTreeNode)child
            : new Node(child, mutable, EMPTY_OBJECT_ARRAY));
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
      if (object instanceof Node) {
        Node node = (Node)object;
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
}
