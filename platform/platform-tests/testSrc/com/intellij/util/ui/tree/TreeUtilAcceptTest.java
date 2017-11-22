// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.tree;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.Invoker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.junit.Assert;
import org.junit.Test;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.EventQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * @author Sergey.Malenkov
 */
public final class TreeUtilAcceptTest {
  @Test
  public void testAcceptDepth1() {
    testFind(() -> new DepthVisitor(1), 1);
  }

  @Test
  public void testAcceptDepth2() {
    testFind(() -> new DepthVisitor(2), 4);
  }

  @Test
  public void testAcceptDepth3() {
    testFind(() -> new DepthVisitor(3), 21);
  }

  @Test
  public void testAcceptAll() {
    testFind(() -> new Visitor(), 21);
  }


  @Test
  public void testFindRoot() {
    testFind(() -> new StringFinder("Root"), 1, "Root");
  }

  @Test
  public void testFindWrongRoot() {
    testFind(() -> new StringFinder("ROOT"), 1);
  }

  @Test
  public void testFindColor() {
    testFind(() -> new StringFinder("RootColor"), 2, "RootColor");
  }

  @Test
  public void testFindWrongColor() {
    testFind(() -> new StringFinder("RootCOLOR"), 4);
  }

  @Test
  public void testFindDigit() {
    testFind(() -> new StringFinder("RootDigit"), 3, "RootDigit");
  }

  @Test
  public void testFindWrongDigit() {
    testFind(() -> new StringFinder("RootDIGIT"), 4);
  }

  @Test
  public void testFindGreek() {
    testFind(() -> new StringFinder("RootGreek"), 4, "RootGreek");
  }

  @Test
  public void testFindWrongGreek() {
    testFind(() -> new StringFinder("RootGREEK"), 4);
  }

  @Test
  public void testFindGreen() {
    testFind(() -> new StringFinder("RootColorGreen"), 4, "RootColorGreen");
  }

  @Test
  public void testFindWrongGreen() {
    testFind(() -> new StringFinder("RootColorGREEN"), 7);
  }

  @Test
  public void testFindFive() {
    testFind(() -> new StringFinder("RootDigitFive"), 8, "RootDigitFive");
  }

  @Test
  public void testFindWrongFive() {
    testFind(() -> new StringFinder("RootDigitFIVE"), 13);
  }

  @Test
  public void testFindGamma() {
    testFind(() -> new StringFinder("RootGreekGamma"), 7, "RootGreekGamma");
  }

  @Test
  public void testFindWrongGamma() {
    testFind(() -> new StringFinder("RootGreekGAMMA"), 9);
  }

  private static void testFind(@NotNull Supplier<Visitor> supplier, long count) {
    testFind(supplier, count, null);
  }

  private static void testFind(@NotNull Supplier<Visitor> supplier, long count, String value) {
    test((tree, promise) -> {
      @NotNull Visitor visitor = supplier.get();
      TreeUtil.accept(tree, visitor, path -> invokeSafely(promise, () -> {
        Assert.assertEquals(count, visitor.counter.get());
        Assert.assertEquals(value, value(path));
      }));
    });
  }


  @Test
  public void testShowRoot() {
    testShow(() -> new StringFinder("Root"), "+[Root]\n");
  }

  @Test
  public void testShowColor() {
    testShow(() -> new StringFinder("RootColor"), "-Root\n" +
                                                  " +[RootColor]\n" +
                                                  " +RootDigit\n" +
                                                  " +RootGreek\n");
  }

  @Test
  public void testShowDigit() {
    testShow(() -> new StringFinder("RootDigit"), "-Root\n" +
                                                  " +RootColor\n" +
                                                  " +[RootDigit]\n" +
                                                  " +RootGreek\n");
  }

  @Test
  public void testShowGreek() {
    testShow(() -> new StringFinder("RootGreek"), "-Root\n" +
                                                  " +RootColor\n" +
                                                  " +RootDigit\n" +
                                                  " +[RootGreek]\n");
  }

  @Test
  public void testShowGreen() {
    testShow(() -> new StringFinder("RootColorGreen"), "-Root\n" +
                                                       " -RootColor\n" +
                                                       "  RootColorRed\n" +
                                                       "  [RootColorGreen]\n" +
                                                       "  RootColorBlue\n" +
                                                       " +RootDigit\n" +
                                                       " +RootGreek\n");
  }

  @Test
  public void testShowFive() {
    testShow(() -> new StringFinder("RootDigitFive"), "-Root\n" +
                                                      " +RootColor\n" +
                                                      " -RootDigit\n" +
                                                      "  RootDigitOne\n" +
                                                      "  RootDigitTwo\n" +
                                                      "  RootDigitThree\n" +
                                                      "  RootDigitFour\n" +
                                                      "  [RootDigitFive]\n" +
                                                      "  RootDigitSix\n" +
                                                      "  RootDigitSeven\n" +
                                                      "  RootDigitEight\n" +
                                                      "  RootDigitNine\n" +
                                                      " +RootGreek\n");
  }

  @Test
  public void testShowGamma() {
    testShow(() -> new StringFinder("RootGreekGamma"), "-Root\n" +
                                                       " +RootColor\n" +
                                                       " +RootDigit\n" +
                                                       " -RootGreek\n" +
                                                       "  RootGreekAlpha\n" +
                                                       "  RootGreekBeta\n" +
                                                       "  [RootGreekGamma]\n" +
                                                       "  RootGreekDelta\n" +
                                                       "  RootGreekEpsilon\n");
  }

  private static void testShow(@NotNull Supplier<Visitor> supplier, @NotNull String expected) {
    test((tree, promise) -> {
      @NotNull Visitor visitor = supplier.get();
      TreeUtil.accept(tree, visitor, path -> invokeSafely(promise, () -> {
        Assert.assertNotNull(path);
        tree.makeVisible(path);
        tree.setSelectionPath(path);
        PlatformTestUtil.assertTreeEqual(tree, expected, true);
      }));
    });
  }


  @Test
  public void testExpandOne() {
    test((tree, promise) -> TreeUtil.expand(tree, 1, () -> invokeSafely(promise, () -> PlatformTestUtil.assertTreeEqual(
      tree, "-Root\n" +
            " +RootColor\n" +
            " +RootDigit\n" +
            " +RootGreek\n"))));
  }

  @Test
  public void testExpandAll() {
    test((tree, promise) -> TreeUtil.expandAll(tree, () -> invokeSafely(promise, () -> PlatformTestUtil.assertTreeEqual(
      tree, "-Root\n" +
            " -RootColor\n" +
            "  RootColorRed\n" +
            "  RootColorGreen\n" +
            "  RootColorBlue\n" +
            " -RootDigit\n" +
            "  RootDigitOne\n" +
            "  RootDigitTwo\n" +
            "  RootDigitThree\n" +
            "  RootDigitFour\n" +
            "  RootDigitFive\n" +
            "  RootDigitSix\n" +
            "  RootDigitSeven\n" +
            "  RootDigitEight\n" +
            "  RootDigitNine\n" +
            " -RootGreek\n" +
            "  RootGreekAlpha\n" +
            "  RootGreekBeta\n" +
            "  RootGreekGamma\n" +
            "  RootGreekDelta\n" +
            "  RootGreekEpsilon\n"))));
  }


  private static void invokeLater(@NotNull AsyncPromise<Throwable> promise, @NotNull Runnable runnable) {
    EventQueue.invokeLater(() -> invokeSafely(promise, runnable, false));
  }

  private static void invokeSafely(@NotNull AsyncPromise<Throwable> promise, @NotNull Runnable runnable) {
    invokeSafely(promise, runnable, true);
  }

  private static void invokeSafely(@NotNull AsyncPromise<Throwable> promise, @NotNull Runnable runnable, boolean stop) {
    try {
      runnable.run();
      if (stop) promise.setResult(null);
    }
    catch (Throwable throwable) {
      promise.setResult(throwable);
    }
  }

  private static void test(BiConsumer<JTree, AsyncPromise<Throwable>> consumer) {
    test(consumer, parent -> model(0, false, null));
    test(consumer, parent -> model(10, false, null));
    test(consumer, parent -> model(0, true, new Invoker.EDT(parent)));
    test(consumer, parent -> model(0, false, new Invoker.EDT(parent)));
    test(consumer, parent -> model(10, true, new Invoker.EDT(parent)));
    test(consumer, parent -> model(10, false, new Invoker.EDT(parent)));
    test(consumer, parent -> model(0, true, new Invoker.BackgroundThread(parent)));
    test(consumer, parent -> model(0, false, new Invoker.BackgroundThread(parent)));
    test(consumer, parent -> model(10, true, new Invoker.BackgroundThread(parent)));
    test(consumer, parent -> model(10, false, new Invoker.BackgroundThread(parent)));
  }

  private static void test(BiConsumer<JTree, AsyncPromise<Throwable>> consumer, Function<Disposable, TreeModel> function) {
    if (EventQueue.isDispatchThread()) throw new IllegalThreadStateException("main thread is expected");
    AsyncPromise<Throwable> promise = new AsyncPromise<>();
    Disposable parent = Disposer.newDisposable();
    TreeModel model = function.apply(parent);
    AsyncTreeModel atm = model instanceof AsyncTreeModel ? (AsyncTreeModel)model : null;
    invokeLater(promise, () -> {
      @SuppressWarnings("UndesirableClassUsage")
      JTree tree = new JTree(model);
      new Runnable() {
        @Override
        public void run() {
          if (atm != null && atm.isProcessing()) {
            invokeLater(promise, this);
          }
          else {
            tree.collapseRow(0); // because root node is expanded by default
            PlatformTestUtil.assertTreeEqual(tree, "+Root\n");
            consumer.accept(tree, promise);
          }
        }
      }.run();
    });
    try {
      Throwable throwable = promise.blockingGet(2, MINUTES);
      if (throwable != null) throw new IllegalStateException("test failed", throwable);
    }
    finally {
      Disposer.dispose(parent);
    }
  }

  private static TreeModel model(long delay, boolean showLoadingNode, Invoker invoker) {
    TreeModel model = new DefaultTreeModel(root());
    if (delay > 0) {
      model = new TreeModelWithDelay(model, delay);
    }
    if (invoker != null) {
      model = new TreeModelWithInvoker(model, invoker);
      model = new AsyncTreeModel(model, showLoadingNode);
      Disposer.register(invoker, (Disposable)model);
    }
    return model;
  }

  private static String value(TreePath path) {
    return path == null ? null : path.getLastPathComponent().toString();
  }

  private static DefaultMutableTreeNode node(@NotNull Object object, Object... children) {
    if (object instanceof DefaultMutableTreeNode && ArrayUtil.isEmpty(children)) return (DefaultMutableTreeNode)object;
    if (object instanceof TreeNode) throw new IllegalArgumentException("do not use a tree node as a node content");
    DefaultMutableTreeNode node = new DefaultMutableTreeNode(object);
    for (Object child : children) node.add(node(child));
    return node;
  }

  private static DefaultMutableTreeNode root() {
    return node("Root",
                node("RootColor",
                     node("RootColorRed"),
                     node("RootColorGreen"),
                     node("RootColorBlue")),
                node("RootDigit",
                     node("RootDigitOne"),
                     node("RootDigitTwo"),
                     node("RootDigitThree"),
                     node("RootDigitFour"),
                     node("RootDigitFive"),
                     node("RootDigitSix"),
                     node("RootDigitSeven"),
                     node("RootDigitEight"),
                     node("RootDigitNine")),
                node("RootGreek",
                     node("RootGreekAlpha"),
                     node("RootGreekBeta"),
                     node("RootGreekGamma"),
                     node("RootGreekDelta"),
                     node("RootGreekEpsilon")));
  }

  static class Visitor extends TreeVisitor.Base<TreePath> {
    final AtomicLong counter = new AtomicLong();

    public Visitor() {
      super(path -> path);
    }

    @NotNull
    @Override
    public Action visit(@NotNull TreePath path) {
      counter.incrementAndGet();
      return super.visit(path);
    }

    @Override
    protected boolean contains(@NotNull TreePath path) {
      return true;
    }
  }

  static class DepthVisitor extends Visitor {
    private final int depth;

    DepthVisitor(int depth) {
      this.depth = depth;
    }

    @Override
    protected boolean contains(@NotNull TreePath path) {
      return depth > path.getPathCount();
    }
  }

  static class StringFinder extends Visitor {
    private final String value;

    StringFinder(@NotNull String value) {
      this.value = value;
    }

    @Override
    protected boolean matches(@NotNull TreePath path) {
      return value.equals(value(path));
    }

    @Override
    protected boolean contains(@NotNull TreePath path) {
      return value.startsWith(value(path));
    }
  }
}
