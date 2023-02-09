// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.concurrency.Invoker;
import com.intellij.util.concurrency.InvokerSupplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.junit.Assert;

import javax.swing.*;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.concurrent.TimeUnit.MINUTES;

public class TreeTest implements Disposable {
  public static final int FAST = 0;
  public static final int SLOW = 10;

  private final AsyncPromise<Throwable> promise = new AsyncPromise<>();
  private JTree tree;

  public TreeTest(int minutes, Consumer<? super TreeTest> consumer, Function<? super Disposable, ? extends TreeModel> function) {
    assert !EventQueue.isDispatchThread() : "main thread is expected";
    invokeLater(() -> {
      tree = new JTree(function.apply(this));
      TreeTestUtil.assertTreeUI(tree);
      invokeAfterProcessing(() -> {
        tree.collapseRow(0); // because root node is expanded by default
        consumer.accept(this);
      });
    });
    try {
      Throwable throwable = promise.blockingGet(minutes, MINUTES);
      if (throwable != null) throw new IllegalStateException("test failed", throwable);
    }
    catch (TimeoutException e) {
      throw new RuntimeException(e);
    }
    finally {
      Disposer.dispose(this);
    }
  }

  @Override
  public void dispose() {
  }

  public void invokeAfterProcessing(@NotNull Runnable runnable) {
    TreeModel model = tree.getModel();
    if (model instanceof AsyncTreeModel async) {
      if (async.isProcessing()) {
        invokeLater(() -> invokeAfterProcessing(runnable));
        return; // do nothing if delayed
      }
    }
    invokeSafely(runnable);
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
      Assert.assertEquals(expected, new TreeTestUtil(getTree()).setSelection(showSelection).toString());
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

  public static void test(Supplier<? extends TreeNode> supplier, Consumer<? super TreeTest> consumer) {
    test(FAST, 1, supplier, consumer);
    test(SLOW, 2, supplier, consumer);
  }

  public static void test(long delay, int minutes, Supplier<? extends TreeNode> supplier, Consumer<? super TreeTest> consumer) {
    new TreeTest(minutes, consumer, parent -> model(supplier, delay, false, null));
    new TreeTest(minutes, consumer, parent -> model(supplier, delay, true, Invoker.forEventDispatchThread(parent)));
    new TreeTest(minutes, consumer, parent -> model(supplier, delay, false, Invoker.forEventDispatchThread(parent)));
    new TreeTest(minutes, consumer, parent -> model(supplier, delay, true, Invoker.forBackgroundThreadWithReadAction(parent)));
    new TreeTest(minutes, consumer, parent -> model(supplier, delay, false, Invoker.forBackgroundThreadWithReadAction(parent)));
  }

  private static TreeModel model(Supplier<? extends TreeNode> supplier, long delay, boolean showLoadingNode, Invoker invoker) {
    TreeModel model = new DefaultTreeModel(supplier.get());
    if (delay > 0) {
      model = new Wrapper.WithDelay(model, delay);
    }
    if (invoker != null) {
      model = new Wrapper.WithInvoker(model, invoker);
      model = new AsyncTreeModel(model, showLoadingNode, invoker);
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
        if (index == 0) pause(); // do not pause for every child
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
