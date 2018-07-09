/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ui.tree;

import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static com.intellij.ui.tree.TreePathUtil.convertArrayToTreePath;
import static com.intellij.ui.tree.TreeTestUtil.node;

/**
 * @author Sergey.Malenkov
 */
public final class TreeUtilVisitTest {
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
      -> TreeUtil.makeVisible(test.getTree(), new TreeVisitor.ByTreePath<>(true, convertArrayToTreePath(array), Object::toString), path
      -> test.addSelection(path, ()
      -> test.assertTree(expected, true, test::done))));
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
