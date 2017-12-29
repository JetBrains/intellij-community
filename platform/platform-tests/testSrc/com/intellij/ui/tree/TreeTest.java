// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.concurrency.Invoker;
import com.intellij.util.concurrency.InvokerSupplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.junit.Assert;

import javax.swing.JTree;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.EventQueue;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.concurrent.TimeUnit.MINUTES;

public class TreeTest implements Disposable {
  private final AsyncPromise<Throwable> promise = new AsyncPromise<>();
  private JTree tree;

  public TreeTest(int minutes, Consumer<TreeTest> consumer, Function<Disposable, TreeModel> function) {
    assert !EventQueue.isDispatchThread() : "main thread is expected";
    invokeLater(() -> {
      tree = new JTree(function.apply(this));
      invokeAfterProcessing(() -> {
        tree.collapseRow(0); // because root node is expanded by default
        consumer.accept(this);
      });
    });
    try {
      Throwable throwable = promise.blockingGet(minutes, MINUTES);
      if (throwable != null) throw new IllegalStateException("test failed", throwable);
    }
    finally {
      Disposer.dispose(this);
    }
  }

  @Override
  public void dispose() {
  }

  public void invokeAfterProcessing(@NotNull Runnable runnable) {
    if (TreeTestUtil.isProcessing(tree)) {
      invokeLater(() -> invokeAfterProcessing(runnable));
    }
    else {
      invokeSafely(runnable);
    }
  }

  public void invokeLater(@NotNull Runnable runnable) {
    EventQueue.invokeLater(() -> invokeSafely(runnable));
  }

  public void invokeSafely(@NotNull Runnable runnable) {
    try {
      runnable.run();
    }
    catch (Throwable throwable) {
      promise.setResult(throwable);
    }
  }

  public void assertTree(@NotNull String expected, @NotNull Runnable runnable) {
    assertTree(expected, false, runnable);
  }

  public void assertTree(@NotNull String expected, boolean showSelection, @NotNull Runnable runnable) {
    invokeSafely(() -> {
      Assert.assertEquals(expected, TreeTestUtil.toString(getTree(), showSelection));
      runnable.run();
    });
  }

  public void addSelection(TreePath path, @NotNull Runnable runnable) {
    invokeSafely(() -> {
      Assert.assertNotNull(path);
      Assert.assertTrue(getTree().isVisible(path));
      getTree().addSelectionPath(path);
      runnable.run();
    });
  }

  public void done() {
    promise.setResult(null);
  }

  @NotNull
  public JTree getTree() {
    return tree;
  }

  public static void test(Supplier<TreeNode> supplier, Consumer<TreeTest> consumer) {
    test(2, supplier, consumer);
  }

  public static void test(int minutes, Supplier<TreeNode> supplier, Consumer<TreeTest> consumer) {
    new TreeTest(minutes, consumer, parent -> model(supplier, 0, false, null));
    new TreeTest(minutes, consumer, parent -> model(supplier, 10, false, null));
    new TreeTest(minutes, consumer, parent -> model(supplier, 0, true, new Invoker.EDT(parent)));
    new TreeTest(minutes, consumer, parent -> model(supplier, 0, false, new Invoker.EDT(parent)));
    new TreeTest(minutes, consumer, parent -> model(supplier, 10, true, new Invoker.EDT(parent)));
    new TreeTest(minutes, consumer, parent -> model(supplier, 10, false, new Invoker.EDT(parent)));
    new TreeTest(minutes, consumer, parent -> model(supplier, 0, true, new Invoker.BackgroundThread(parent)));
    new TreeTest(minutes, consumer, parent -> model(supplier, 0, false, new Invoker.BackgroundThread(parent)));
    new TreeTest(minutes, consumer, parent -> model(supplier, 10, true, new Invoker.BackgroundThread(parent)));
    new TreeTest(minutes, consumer, parent -> model(supplier, 10, false, new Invoker.BackgroundThread(parent)));
  }

  private static TreeModel model(Supplier<TreeNode> supplier, long delay, boolean showLoadingNode, Invoker invoker) {
    TreeModel model = new DefaultTreeModel(supplier.get());
    if (delay > 0) {
      model = new Wrapper.WithDelay(model, delay);
    }
    if (invoker != null) {
      model = new Wrapper.WithInvoker(model, invoker);
      model = new AsyncTreeModel(model, showLoadingNode);
      Disposer.register(invoker, (Disposable)model);
    }
    return model;
  }

  private static class Wrapper implements TreeModel {
    private final TreeModel model;

    Wrapper(@NotNull TreeModel model) {
      this.model = model;
    }

    @Override
    public Object getRoot() {
      return model.getRoot();
    }

    @Override
    public Object getChild(Object parent, int index) {
      return model.getChild(parent, index);
    }

    @Override
    public int getChildCount(Object parent) {
      return model.getChildCount(parent);
    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
      return model.getIndexOfChild(parent, child);
    }

    @Override
    public boolean isLeaf(Object child) {
      return model.isLeaf(child);
    }

    @Override
    public void valueForPathChanged(TreePath path, Object value) {
      model.valueForPathChanged(path, value);
    }

    @Override
    public void addTreeModelListener(TreeModelListener listener) {
      model.addTreeModelListener(listener);
    }

    @Override
    public void removeTreeModelListener(TreeModelListener listener) {
      model.removeTreeModelListener(listener);
    }

    private static class WithDelay extends Wrapper {
      private final long delay;

      WithDelay(@NotNull TreeModel model, long delay) {
        super(model);
        this.delay = delay;
      }

      void pause() {
        if (delay > 0) {
          try {
            Thread.sleep(delay);
          }
          catch (InterruptedException ignored) {
          }
        }
      }

      @Override
      public Object getRoot() {
        pause();
        return super.getRoot();
      }

      @Override
      public Object getChild(Object parent, int index) {
        pause();
        return super.getChild(parent, index);
      }

      @Override
      public int getChildCount(Object parent) {
        pause();
        return super.getChildCount(parent);
      }

      @Override
      public int getIndexOfChild(Object parent, Object child) {
        pause();
        return super.getIndexOfChild(parent, child);
      }

      @Override
      public boolean isLeaf(Object child) {
        pause();
        return super.isLeaf(child);
      }

      @Override
      public void valueForPathChanged(TreePath path, Object value) {
        pause();
        super.valueForPathChanged(path, value);
      }
    }

    private static class WithInvoker extends Wrapper implements InvokerSupplier {
      private final Invoker invoker;

      WithInvoker(@NotNull TreeModel model, @NotNull Invoker invoker) {
        super(model);
        this.invoker = invoker;
      }

      @NotNull
      @Override
      public Invoker getInvoker() {
        return invoker;
      }
    }
  }
}
