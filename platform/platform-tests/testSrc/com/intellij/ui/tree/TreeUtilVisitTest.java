// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree;

import com.intellij.testFramework.TestApplicationManager;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.intellij.ui.tree.TreePathUtil.convertArrayToTreePath;
import static com.intellij.ui.tree.TreeTestUtil.node;
import static com.intellij.util.containers.ContainerUtil.set;

public final class TreeUtilVisitTest {
  @Before
  public void setUp() {
    TestApplicationManager.getInstance();
  }


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
    TreeTest.test(TreeUtilVisitTest::root, test -> test.assertTree("+Root\n", () -> {
      @NotNull Visitor visitor = supplier.get();
      TreeUtil.visit(test.getTree(), visitor, path -> test.invokeSafely(() -> {
        Assert.assertEquals(count, visitor.counter.get());
        Assert.assertEquals(value, value(path));
        test.done();
      }));
    }));
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
    TreeTest.test(TreeUtilVisitTest::root, test -> test.assertTree("+Root\n", () -> {
      @NotNull Visitor visitor = supplier.get();
      TreeUtil.visit(test.getTree(), visitor, path -> {
        test.getTree().makeVisible(path);
        test.addSelection(path, () -> test.assertTree(expected, true, test::done));
      });
    }));
  }


  @Test
  public void testExpandOne() {
    TreeTest.test(TreeUtilVisitTest::root, test
      -> test.assertTree("+Root\n", ()
      -> TreeUtil.expand(test.getTree(), 1, ()
      -> test.assertTree("-Root\n" +
                         " +RootColor\n" +
                         " +RootDigit\n" +
                         " +RootGreek\n",
                         test::done))));
  }

  @Test
  public void testExpandTwo() {
    TreeTest.test(TreeUtilVisitTest::rootDeep, test
      -> test.assertTree("+Root\n", ()
      -> TreeUtil.expand(test.getTree(), 2, ()
      -> test.assertTree("-Root\n" +
                         " -1\n" +
                         "  +11\n" +
                         "  +12\n" +
                         "  +13\n" +
                         " -2\n" +
                         "  +21\n" +
                         "  +22\n" +
                         "  +23\n" +
                         " -3\n" +
                         "  +31\n" +
                         "  +32\n" +
                         "  +33\n",
                         test::done))));
  }

  @Test
  public void testExpandAll() {
    TreeTest.test(TreeUtilVisitTest::root, test
      -> test.assertTree("+Root\n", ()
      -> TreeUtil.expandAll(test.getTree(), ()
      -> test.assertTree("-Root\n" +
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
                         "  RootGreekEpsilon\n",
                         test::done))
    ));
  }


  @Test
  public void testCollapseAll() {
    testCollapseAll(true, true, false, 0,
                    "-Root\n" +
                    " +1\n" +
                    " +[2]\n" +
                    " +3\n");
  }

  @Test
  public void testCollapseAllStrict() {
    testCollapseAll(true, true, true, 0,
                    "+[Root]\n");
  }

  @Test
  public void testCollapseAllWithoutRoot() {
    testCollapseAll(false, true, false, 0,
                    " +1\n" +
                    " +[2]\n" +
                    " +3\n");
  }

  @Test
  public void testCollapseAllWithoutRootHandles() {
    testCollapseAll(true, false, false, 0,
                    "-Root\n" +
                    " +1\n" +
                    " +[2]\n" +
                    " +3\n");
  }

  @Test
  public void testCollapseAllWithoutRootAndHandles() {
    testCollapseAll(false, false, false, 0,
                    " -1\n" +
                    "  +11\n" +
                    "  +12\n" +
                    "  +13\n" +
                    " -2\n" +
                    "  +21\n" +
                    "  +[22]\n" +
                    "  +23\n" +
                    " -3\n" +
                    "  +31\n" +
                    "  +32\n" +
                    "  +33\n");
  }

  @Test
  public void testCollapseAllExceptSelectedNode() {
    testCollapseAll(true, true, false, -1,
                    "-Root\n" +
                    " +1\n" +
                    " -2\n" +
                    "  +21\n" +
                    "  -22\n" +
                    "   +221\n" +
                    "   -222\n" +
                    "    2221\n" +
                    "    [2222]\n" +
                    "    2223\n" +
                    "   +223\n" +
                    "  +23\n" +
                    " +3\n");
  }

  @Test
  public void testCollapseAllExceptParentOfSelectedNode() {
    testCollapseAll(true, true, false, 4,
                    "-Root\n" +
                    " +1\n" +
                    " -2\n" +
                    "  +21\n" +
                    "  -22\n" +
                    "   +221\n" +
                    "   +[222]\n" +
                    "   +223\n" +
                    "  +23\n" +
                    " +3\n");
  }

  @Test
  public void testCollapseAllExceptGrandParentOfSelectedNode() {
    testCollapseAll(true, true, false, 3,
                    "-Root\n" +
                    " +1\n" +
                    " -2\n" +
                    "  +21\n" +
                    "  +[22]\n" +
                    "  +23\n" +
                    " +3\n");
  }

  private static void testCollapseAll(boolean visible, boolean showHandles, boolean strict, int keepSelectionLevel, String expected) {
    TreeTest.test(TreeTest.FAST, 1, TreeUtilVisitTest::rootDeep, test
      -> configureRoot(test, visible, showHandles, ()
      -> TreeUtil.expandAll(test.getTree(), ()
      -> select(test, convertArrayToVisitor("2", "22", "222", "2222"), path
      -> collapseAll(test, strict, keepSelectionLevel, ()
      -> test.assertTree(expected, true, test::done))))));
  }

  private static void collapseAll(@NotNull TreeTest test, boolean strict, int keepSelectionLevel, @NotNull Runnable onDone) {
    test.getTree().addTreeExpansionListener(new TreeExpansionListener() {
      @Override
      public void treeCollapsed(TreeExpansionEvent event) {
      }

      @Override
      public void treeExpanded(TreeExpansionEvent event) {
        throw new UnsupportedOperationException("do not expand while collapse");
      }
    });
    TreeUtil.collapseAll(test.getTree(), strict, keepSelectionLevel);
    onDone.run();
  }

  private static void configureRoot(@NotNull TreeTest test, boolean visible, boolean showHandles, @NotNull Runnable onDone) {
    test.getTree().setRootVisible(visible);
    test.getTree().setShowsRootHandles(showHandles);
    onDone.run();
  }

  @Test
  public void testMakeVisible1() {
    testMakeVisible("-Root\n" +
                    " +[1]\n" +
                    " +2\n" +
                    " +3\n",
                    "1");
  }

  @Test
  public void testMakeVisible11() {
    testMakeVisible("-Root\n" +
                    " -1\n" +
                    "  +[11]\n" +
                    "  +12\n" +
                    "  +13\n" +
                    " +2\n" +
                    " +3\n",
                    "1", "11");
  }

  @Test
  public void testMakeVisible111() {
    testMakeVisible("-Root\n" +
                    " -1\n" +
                    "  -11\n" +
                    "   +[111]\n" +
                    "   +112\n" +
                    "   +113\n" +
                    "  +12\n" +
                    "  +13\n" +
                    " +2\n" +
                    " +3\n",
                    "1", "11", "111");
  }

  @Test
  public void testMakeVisible1111() {
    testMakeVisible("-Root\n" +
                    " -1\n" +
                    "  -11\n" +
                    "   -111\n" +
                    "    [1111]\n" +
                    "    1112\n" +
                    "    1113\n" +
                    "   +112\n" +
                    "   +113\n" +
                    "  +12\n" +
                    "  +13\n" +
                    " +2\n" +
                    " +3\n",
                    "1", "11", "111", "1111");
  }

  @Test
  public void testMakeVisible2() {
    testMakeVisible("-Root\n" +
                    " +1\n" +
                    " +[2]\n" +
                    " +3\n",
                    "2");
  }

  @Test
  public void testMakeVisible22() {
    testMakeVisible("-Root\n" +
                    " +1\n" +
                    " -2\n" +
                    "  +21\n" +
                    "  +[22]\n" +
                    "  +23\n" +
                    " +3\n",
                    "2", "22");
  }

  @Test
  public void testMakeVisible222() {
    testMakeVisible("-Root\n" +
                    " +1\n" +
                    " -2\n" +
                    "  +21\n" +
                    "  -22\n" +
                    "   +221\n" +
                    "   +[222]\n" +
                    "   +223\n" +
                    "  +23\n" +
                    " +3\n",
                    "2", "22", "222");
  }

  @Test
  public void testMakeVisible2222() {
    testMakeVisible("-Root\n" +
                    " +1\n" +
                    " -2\n" +
                    "  +21\n" +
                    "  -22\n" +
                    "   +221\n" +
                    "   -222\n" +
                    "    2221\n" +
                    "    [2222]\n" +
                    "    2223\n" +
                    "   +223\n" +
                    "  +23\n" +
                    " +3\n",
                    "2", "22", "222", "2222");
  }

  @Test
  public void testMakeVisible3() {
    testMakeVisible("-Root\n" +
                    " +1\n" +
                    " +2\n" +
                    " +[3]\n",
                    "3");
  }

  @Test
  public void testMakeVisible33() {
    testMakeVisible("-Root\n" +
                    " +1\n" +
                    " +2\n" +
                    " -3\n" +
                    "  +31\n" +
                    "  +32\n" +
                    "  +[33]\n",
                    "3", "33");
  }

  @Test
  public void testMakeVisible333() {
    testMakeVisible("-Root\n" +
                    " +1\n" +
                    " +2\n" +
                    " -3\n" +
                    "  +31\n" +
                    "  +32\n" +
                    "  -33\n" +
                    "   +331\n" +
                    "   +332\n" +
                    "   +[333]\n",
                    "3", "33", "333");
  }

  @Test
  public void testMakeVisible3333() {
    testMakeVisible("-Root\n" +
                    " +1\n" +
                    " +2\n" +
                    " -3\n" +
                    "  +31\n" +
                    "  +32\n" +
                    "  -33\n" +
                    "   +331\n" +
                    "   +332\n" +
                    "   -333\n" +
                    "    3331\n" +
                    "    3332\n" +
                    "    [3333]\n",
                    "3", "33", "333", "3333");
  }

  private static void testMakeVisible(String expected, String... array) {
    TreeTest.test(TreeUtilVisitTest::rootDeep, test
      -> TreeUtil.makeVisible(test.getTree(), convertArrayToVisitor(array), path
      -> test.addSelection(path, ()
      -> test.assertTree(expected, true, test::done))));
  }

  @Test
  public void testSelect11() {
    testSelect(convertArrayToVisitor("1", "11"),
               "-Root\n" +
               " -1\n" +
               "  +[11]\n" +
               "  +12\n" +
               "  +13\n" +
               " +2\n" +
               " +3\n");
  }

  @Test
  public void testSelect222() {
    testSelect(convertArrayToVisitor("2", "22", "222"),
               "-Root\n" +
               " +1\n" +
               " -2\n" +
               "  +21\n" +
               "  -22\n" +
               "   +221\n" +
               "   +[222]\n" +
               "   +223\n" +
               "  +23\n" +
               " +3\n");
  }

  @Test
  public void testSelect3333() {
    testSelect(convertArrayToVisitor("3", "33", "333", "3333"),
               "-Root\n" +
               " +1\n" +
               " +2\n" +
               " -3\n" +
               "  +31\n" +
               "  +32\n" +
               "  -33\n" +
               "   +331\n" +
               "   +332\n" +
               "   -333\n" +
               "    3331\n" +
               "    3332\n" +
               "    [3333]\n");
  }

  private static void testSelect(TreeVisitor visitor, String expected) {
    TreeTest.test(TreeUtilVisitTest::rootDeep, test
      -> select(test, visitor, path
      -> test.assertTree(expected, true, test::done)));
  }

  private static void select(@NotNull TreeTest test, @NotNull TreeVisitor visitor, @NotNull Consumer<? super TreePath> consumer) {
    TreeUtil.promiseSelect(test.getTree(), visitor).onSuccess(consumer);
  }

  @Test
  public void testMultiSelect3333and222and11andRoot() {
    TreeVisitor[] array = {
      convertArrayToVisitor("3", "33", "333", "3333"),
      convertArrayToVisitor("2", "22", "222"),
      convertArrayToVisitor("1", "11"),
      path -> TreeVisitor.Action.INTERRUPT,
    };
    testMultiSelect(array, array.length,
                    "-[Root]\n" +
                    " -1\n" +
                    "  +[11]\n" +
                    "  +12\n" +
                    "  +13\n" +
                    " -2\n" +
                    "  +21\n" +
                    "  -22\n" +
                    "   +221\n" +
                    "   +[222]\n" +
                    "   +223\n" +
                    "  +23\n" +
                    " -3\n" +
                    "  +31\n" +
                    "  +32\n" +
                    "  -33\n" +
                    "   +331\n" +
                    "   +332\n" +
                    "   -333\n" +
                    "    3331\n" +
                    "    3332\n" +
                    "    [3333]\n");
  }

  @Test
  public void testMultiSelectNothingExceptRoot() {
    TreeVisitor[] array = {
      convertArrayToVisitor("3", "33", "333", "[3333]"),
      convertArrayToVisitor("2", "22", "[222]"),
      convertArrayToVisitor("1", "[11]"),
      path -> TreeVisitor.Action.INTERRUPT,
      null,
    };
    testMultiSelect(array, 1,
                    "-[Root]\n" +
                    " -1\n" +
                    "  +11\n" +
                    "  +12\n" +
                    "  +13\n" +
                    " -2\n" +
                    "  +21\n" +
                    "  -22\n" +
                    "   +221\n" +
                    "   +222\n" +
                    "   +223\n" +
                    "  +23\n" +
                    " -3\n" +
                    "  +31\n" +
                    "  +32\n" +
                    "  -33\n" +
                    "   +331\n" +
                    "   +332\n" +
                    "   -333\n" +
                    "    3331\n" +
                    "    3332\n" +
                    "    3333\n");
  }

  @Test
  public void testMultiSelectNothing() {
    TreeVisitor[] array = {null, null, null};
    testMultiSelect(array, 0, "+Root\n");
  }

  private static void testMultiSelect(TreeVisitor @NotNull [] array, int count, @NotNull String expected) {
    testMultiSelect(array, count, expected, TreeTest::done);
  }

  private static void testMultiSelect(TreeVisitor @NotNull [] array, int count, @NotNull String expected, @NotNull Consumer<? super TreeTest> then) {
    TreeTest.test(TreeUtilVisitTest::rootDeep, test -> TreeUtil.promiseSelect(test.getTree(), Stream.of(array)).onProcessed(paths -> {
      test.invokeSafely(() -> {
        if (count == 0) {
          Assert.assertNull(paths);
        }
        else {
          Assert.assertNotNull(paths);
          Assert.assertEquals(count, paths.size());
        }
        test.assertTree(expected, true, () -> then.accept(test));
      });
    }));
  }

  @Test
  public void testCollectExpandedPaths() {
    testCollectExpandedPaths(set("Root", "1", "2", "22", "3", "33", "333"), test
      -> TreeUtil.collectExpandedPaths(test.getTree()));
  }

  @Test
  public void testCollectExpandedPathsUnderCollapsed11() {
    testCollectExpandedPaths(set(), test -> {
      TreePath root = test.getTree().getPathForRow(2); // 11
      return TreeUtil.collectExpandedPaths(test.getTree(), root);
    });
  }

  @Test
  public void testCollectExpandedPathsWithInvisibleRootUnderInvisibleRoot() {
    testCollectExpandedPaths(set("1", "2", "22", "3", "33", "333"), test -> {
      TreePath root = test.getTree().getPathForRow(0); // Root
      test.getTree().setRootVisible(false);
      return TreeUtil.collectExpandedPaths(test.getTree(), root);
    });
  }

  @Test
  public void testCollectExpandedPathsWithInvisibleRoot() {
    testCollectExpandedPaths(set("1", "2", "22", "3", "33", "333"), test -> {
      test.getTree().setRootVisible(false);
      return TreeUtil.collectExpandedPaths(test.getTree());
    });
  }

  @Test
  public void testCollectExpandedPathsWithInvisibleRootUnder33() {
    testCollectExpandedPaths(set("33", "333"), test -> {
      test.getTree().setRootVisible(false);
      TreePath root = test.getTree().getPathForRow(14); // 33
      return TreeUtil.collectExpandedPaths(test.getTree(), root);
    });
  }

  @Test
  public void testCollectExpandedPathsWithInvisibleRootUnder333() {
    testCollectExpandedPaths(set("333"), test -> {
      test.getTree().setRootVisible(false);
      TreePath root = test.getTree().getPathForRow(17); // 333
      return TreeUtil.collectExpandedPaths(test.getTree(), root);
    });
  }

  private static void testCollectExpandedPaths(@NotNull Set<String> expected, @NotNull Function<? super TreeTest, ? extends List<TreePath>> getter) {
    testCollectSelection(test -> {
      List<TreePath> paths = getter.apply(test);
      Assert.assertEquals(expected.size(), paths.size());
      paths.forEach(path -> Assert.assertTrue(expected.contains(TreeUtil.getLastUserObject(String.class, path))));
      test.done();
    });
  }

  @Test
  public void testCollectExpandedUserObjects() {
    testCollectExpandedUserObjects(set("Root", "1", "2", "22", "3", "33", "333"), test
      -> TreeUtil.collectExpandedUserObjects(test.getTree()));
  }

  @Test
  public void testCollectExpandedUserObjectsWithCollapsedPath() {
    testCollectExpandedUserObjects(set("Root", "1", "2", "22", "3"), test -> {
      test.getTree().collapseRow(15); // 33
      return TreeUtil.collectExpandedUserObjects(test.getTree());
    });
  }

  @Test
  public void testCollectExpandedUserObjectsWithCollapsedPath33Under33() {
    testCollectExpandedUserObjects(new HashSet<>(), test -> {
      test.getTree().collapseRow(15); // 33
      TreePath root = test.getTree().getPathForRow(15); // 33
      return TreeUtil.collectExpandedUserObjects(test.getTree(), root);
    });
  }

  @Test
  public void testCollectExpandedUserObjectsWithCollapsedPath333Under33() {
    testCollectExpandedUserObjects(set("33"), test -> {
      test.getTree().collapseRow(18); // 333
      TreePath root = test.getTree().getPathForRow(15); // 33
      return TreeUtil.collectExpandedUserObjects(test.getTree(), root);
    });
  }

  private static void testCollectExpandedUserObjects(@NotNull Set<String> expected, @NotNull Function<? super TreeTest, ? extends List<Object>> getter) {
    testCollectSelection(test -> {
      List<Object> objects = getter.apply(test);
      Assert.assertEquals(expected.size(), objects.size());
      objects.forEach(object -> Assert.assertTrue(expected.contains((String)object)));
      test.done();
    });
  }

  @Test
  public void testCollectSelectedPaths() {
    testCollectSelectedPaths(set("11", "222", "33", "3331", "3332", "3333", "Root"), test
      -> TreeUtil.collectSelectedPaths(test.getTree()));
  }

  @Test
  public void testCollectSelectedPathsWithInvisibleRoot() {
    testCollectSelectedPaths(set("11", "222", "33", "3331", "3332", "3333"), test -> {
      test.getTree().setRootVisible(false);
      return TreeUtil.collectSelectedPaths(test.getTree());
    });
  }

  @Test
  public void testCollectSelectedPathsWithInvisibleRootUnder33() {
    testCollectSelectedPaths(set("33", "3331", "3332", "3333"), test -> {
      test.getTree().setRootVisible(false);
      TreePath root = test.getTree().getPathForRow(14); // 33
      return TreeUtil.collectSelectedPaths(test.getTree(), root);
    });
  }

  @Test
  public void testCollectSelectedPathsWithInvisibleRootUnder333() {
    testCollectSelectedPaths(set("3331", "3332", "3333"), test -> {
      test.getTree().setRootVisible(false);
      TreePath root = test.getTree().getPathForRow(17); // 333
      return TreeUtil.collectSelectedPaths(test.getTree(), root);
    });
  }

  private static void testCollectSelectedPaths(@NotNull Set<String> expected, @NotNull Function<? super TreeTest, ? extends List<TreePath>> getter) {
    testCollectSelection(test -> {
      List<TreePath> paths = getter.apply(test);
      Assert.assertEquals(expected.size(), paths.size());
      paths.forEach(path -> Assert.assertTrue(expected.contains(TreeUtil.getLastUserObject(String.class, path))));
      test.done();
    });
  }

  @Test
  public void testCollectSelectedUserObjects() {
    testCollectSelectedUserObjects(set("11", "222", "33", "3331", "3332", "3333", "Root"), test
      -> TreeUtil.collectSelectedUserObjects(test.getTree()));
  }

  @Test
  public void testCollectSelectedUserObjectsWithCollapsedPath() {
    testCollectSelectedUserObjects(set("11", "222", "33", "Root"), test -> {
      test.getTree().collapseRow(15); // 33
      return TreeUtil.collectSelectedUserObjects(test.getTree());
    });
  }

  @Test
  public void testCollectSelectedUserObjectsWithCollapsedPathUnder33() {
    testCollectSelectedUserObjects(set("33"), test -> {
      test.getTree().collapseRow(15); // 33
      TreePath root = test.getTree().getPathForRow(15); // 33
      return TreeUtil.collectSelectedUserObjects(test.getTree(), root);
    });
  }

  @Test
  public void testCollectSelectedUserObjectsWithCollapsedPathUnder333() {
    // collapsed parent node becomes selected if it contains selected children
    testCollectSelectedUserObjects(set("333"), test -> {
      test.getTree().collapseRow(18); // 333
      TreePath root = test.getTree().getPathForRow(18); // 333
      return TreeUtil.collectSelectedUserObjects(test.getTree(), root);
    });
  }

  private static void testCollectSelectedUserObjects(@NotNull Set<String> expected, @NotNull Function<? super TreeTest, ? extends List<Object>> getter) {
    testCollectSelection(test -> {
      List<Object> objects = getter.apply(test);
      Assert.assertEquals(expected.size(), objects.size());
      objects.forEach(object -> Assert.assertTrue(expected.contains((String)object)));
      test.done();
    });
  }

  private static void testCollectSelection(@NotNull Consumer<? super TreeTest> consumer) {
    TreeVisitor[] array = {
      convertArrayToVisitor("1", "11"),
      convertArrayToVisitor("2", "22", "222"),
      convertArrayToVisitor("3", "33"),
      convertArrayToVisitor("3", "33", "333", "3331"),
      convertArrayToVisitor("3", "33", "333", "3332"),
      convertArrayToVisitor("3", "33", "333", "3333"),
      path -> TreeVisitor.Action.INTERRUPT,
    };
    testMultiSelect(array, array.length,
                    "-[Root]\n" +
                    " -1\n" +
                    "  +[11]\n" +
                    "  +12\n" +
                    "  +13\n" +
                    " -2\n" +
                    "  +21\n" +
                    "  -22\n" +
                    "   +221\n" +
                    "   +[222]\n" +
                    "   +223\n" +
                    "  +23\n" +
                    " -3\n" +
                    "  +31\n" +
                    "  +32\n" +
                    "  -[33]\n" +
                    "   +331\n" +
                    "   +332\n" +
                    "   -333\n" +
                    "    [3331]\n" +
                    "    [3332]\n" +
                    "    [3333]\n",
                    consumer);
  }

  @Test
  public void testCollectSelectedObjectsOfType() {
    TreeTest.test(() -> node(Boolean.TRUE, node(101), node(1.1f)), test
      -> test.assertTree("+true\n", ()
      -> TreeUtil.expandAll(test.getTree(), ()
      -> test.assertTree("-true\n 101\n 1.1\n", ()
      -> {
      TreeUtil.visitVisibleRows(test.getTree(), path -> path, path -> test.getTree().addSelectionPath(path));
      test.assertTree("-[true]\n [101]\n [1.1]\n", true, () -> {
        Assert.assertEquals(3, TreeUtil.collectSelectedObjectsOfType(test.getTree(), Object.class).size());
        Assert.assertEquals(2, TreeUtil.collectSelectedObjectsOfType(test.getTree(), Number.class).size());
        Assert.assertEquals(1, TreeUtil.collectSelectedObjectsOfType(test.getTree(), Boolean.class).size());
        Assert.assertEquals(1, TreeUtil.collectSelectedObjectsOfType(test.getTree(), Integer.class).size());
        Assert.assertEquals(1, TreeUtil.collectSelectedObjectsOfType(test.getTree(), Float.class).size());
        Assert.assertEquals(0, TreeUtil.collectSelectedObjectsOfType(test.getTree(), String.class).size());
        test.done();
      });
    }))));
  }

  @Test
  public void testSelectFirstEmpty() {
    testSelectFirst(() -> null, true, "");
  }

  @Test
  public void testSelectFirstWithRoot() {
    testSelectFirst(TreeUtilVisitTest::rootDeep, true, "+[Root]\n");
  }

  @Test
  public void testSelectFirstWithoutRoot() {
    testSelectFirst(TreeUtilVisitTest::rootDeep, false, " +[1]\n" + " +2\n" + " +3\n");
  }

  private static void testSelectFirst(@NotNull Supplier<TreeNode> root, boolean visible, @NotNull String expected) {
    TreeTest.test(root, test -> {
      test.getTree().setRootVisible(visible);
      TreeUtil.promiseSelectFirst(test.getTree()).onProcessed(path -> test.invokeSafely(() -> {
        if (expected.isEmpty()) {
          Assert.assertNull(path);
        }
        else {
          Assert.assertNotNull(path);
          Assert.assertTrue(test.getTree().isVisible(path));
        }
        test.assertTree(expected, true, test::done);
      }));
    });
  }

  @Test
  public void testSelectFirstLeafEmpty() {
    testSelectFirstLeaf(() -> null, true, "");
  }

  @Test
  public void testSelectFirstLeafInvisibleRoot() {
    testSelectFirstLeaf(() -> node("root"), false, "");
  }

  @Test
  public void testSelectFirstLeafWhenNoMoreLeafs() {
    testSelectFirstLeaf(() -> node("root", node("middle", node("leaf"))), false, " -middle\n  [leaf]\n");
  }

  @Test
  public void testSelectFirstLeafWithRoot() {
    testSelectFirstLeaf(TreeUtilVisitTest::rootDeep, true, "-Root\n" +
                                                           " -1\n" +
                                                           "  -11\n" +
                                                           "   -111\n" +
                                                           "    [1111]\n" +
                                                           "    1112\n" +
                                                           "    1113\n" +
                                                           "   +112\n" +
                                                           "   +113\n" +
                                                           "  +12\n" +
                                                           "  +13\n" +
                                                           " +2\n" +
                                                           " +3\n");
  }

  @Test
  public void testSelectFirstLeafWithoutRoot() {
    testSelectFirstLeaf(TreeUtilVisitTest::rootDeep, false, " -1\n" +
                                                            "  -11\n" +
                                                            "   -111\n" +
                                                            "    [1111]\n" +
                                                            "    1112\n" +
                                                            "    1113\n" +
                                                            "   +112\n" +
                                                            "   +113\n" +
                                                            "  +12\n" +
                                                            "  +13\n" +
                                                            " +2\n" +
                                                            " +3\n");
  }

  private static void testSelectFirstLeaf(@NotNull Supplier<TreeNode> root, boolean visible, @NotNull String expected) {
    TreeTest.test(root, test -> {
      test.getTree().setRootVisible(visible);
      TreeUtil.promiseSelectFirstLeaf(test.getTree()).onProcessed(path -> test.invokeSafely(() -> {
        if (expected.isEmpty()) {
          Assert.assertNull(path);
        }
        else {
          Assert.assertNotNull(path);
          Assert.assertTrue(test.getTree().isVisible(path));
        }
        test.assertTree(expected, true, test::done);
      }));
    });
  }

  @Test
  public void testMakeVisibleNonExistent() {
    testMakeVisibleNonExistent("-Root\n" +
                               " +1\n" +
                               " +2\n" +
                               " -3\n" +
                               "  +31\n" +
                               "  +32\n" +
                               "  +33\n",
                               convertArrayToVisitor("3", "NonExistent"));
  }

  @Test
  public void testMakeVisibleNonExistentRoot() {
    testMakeVisibleNonExistent("+Root\n", createRootVisitor("tOOr"));
  }

  private static void testMakeVisibleNonExistent(String expected, TreeVisitor visitor) {
    testNonExistent(expected, test -> TreeUtil.promiseMakeVisible(test.getTree(), visitor));
  }

  @Test
  public void testExpandNonExistent() {
    testExpandNonExistent("-Root\n" +
                          " +1\n" +
                          " +2\n" +
                          " -3\n" +
                          "  +31\n" +
                          "  +32\n" +
                          "  +33\n",
                          convertArrayToVisitor("3", "NonExistent"));
  }

  @Test
  public void testExpandNonExistentRoot() {
    testExpandNonExistent("+Root\n", createRootVisitor("t00r"));
  }

  private static void testExpandNonExistent(String expected, TreeVisitor visitor) {
    testNonExistent(expected, test -> TreeUtil.promiseExpand(test.getTree(), visitor));
  }

  private static void testNonExistent(String expected, Function<? super TreeTest, ? extends Promise<TreePath>> function) {
    TreeTest.test(TreeUtilVisitTest::rootDeep, test -> function.apply(test)
      .onSuccess(path -> test.invokeSafely(() -> Assert.fail("found unexpected path: " + path)))
      .onError(error -> test.invokeSafely(() -> {
        Assert.assertTrue(error instanceof CancellationException);
        test.assertTree(expected, test::done);
      })));
  }

  private static TreeVisitor createRootVisitor(@NotNull String name) {
    return new TreeVisitor.ByTreePath<>(new TreePath(name), Object::toString);
  }

  private static TreeVisitor convertArrayToVisitor(String @NotNull ... array) {
    return new TreeVisitor.ByTreePath<>(true, convertArrayToTreePath(array), Object::toString);
  }

  private static String value(TreePath path) {
    return path == null ? null : path.getLastPathComponent().toString();
  }

  private static TreeNode root() {
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

  private static TreeNode rootDeep() {
    return node("Root",
                node("1",
                     node("11",
                          node("111", "1111", "1112", "1113"),
                          node("112", "1121", "1122", "1123"),
                          node("113", "1131", "1132", "1133")),
                     node("12",
                          node("121", "1211", "1212", "1213"),
                          node("122", "1221", "1222", "1223"),
                          node("123", "1231", "1232", "1233")),
                     node("13",
                          node("131", "1311", "1312", "1313"),
                          node("132", "1321", "1322", "1323"),
                          node("133", "1331", "1332", "1333"))),
                node("2",
                     node("21",
                          node("211", "2111", "2112", "2113"),
                          node("212", "2121", "2122", "2123"),
                          node("213", "2131", "2132", "2133")),
                     node("22",
                          node("221", "2211", "2212", "2213"),
                          node("222", "2221", "2222", "2223"),
                          node("223", "2231", "2232", "2233")),
                     node("23",
                          node("231", "2311", "2312", "2313"),
                          node("232", "2321", "2322", "2323"),
                          node("233", "2331", "2332", "2333"))),
                node("3",
                     node("31",
                          node("311", "3111", "3112", "3113"),
                          node("312", "3121", "3122", "3123"),
                          node("313", "3131", "3132", "3133")),
                     node("32",
                          node("321", "3211", "3212", "3213"),
                          node("322", "3221", "3222", "3223"),
                          node("323", "3231", "3232", "3233")),
                     node("33",
                          node("331", "3311", "3312", "3313"),
                          node("332", "3321", "3322", "3323"),
                          node("333", "3331", "3332", "3333"))));
  }

  static class Visitor implements TreeVisitor {
    final AtomicLong counter = new AtomicLong();

    @NotNull
    @Override
    public Action visit(@NotNull TreePath path) {
      counter.incrementAndGet();
      if (matches(path)) return Action.INTERRUPT;
      if (contains(path)) return Action.CONTINUE;
      return Action.SKIP_CHILDREN;
    }

    protected boolean matches(@NotNull TreePath path) {
      return false;
    }

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
