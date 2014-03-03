package com.intellij.openapi.diff.impl.highlighting;

import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.diff.impl.MultiCheck;
import com.intellij.openapi.diff.impl.string.DiffString;
import com.intellij.util.Assertion;
import com.intellij.util.diff.Diff;
import com.intellij.util.diff.FilesTooBigForDiffException;
import junit.framework.TestCase;

public class UtilTest extends TestCase {
  private final Assertion CHECK = new Assertion();

  public void testSplitByWord() {
    CHECK.singleElement(Util.splitByWord("abc"), "abc");
    CHECK.compareAll(new String[]{"abc", " ", "123"}, Util.splitByWord("abc 123"));
    CHECK.compareAll(new String[]{"abc", "  \n", "\t ", "123"}, Util.splitByWord("abc  \n\t 123"));
    CHECK.compareAll(new String[]{"a_b", "(", "c"}, Util.splitByWord("a_b(c"));
    CHECK.compareAll(new String[]{"ab", " ", "+","(", " ", "c"}, Util.splitByWord("ab +( c"));
    CHECK.compareAll(new String[]{"a", " ", "b", "\n"}, Util.splitByWord("a b\n"));
  }

  public void testIsSpaceOnly() {
    assertTrue(Util.isSpaceOnly(new DiffFragment(null, " ")));
    assertTrue(Util.isSpaceOnly(new DiffFragment(" ", null)));
  }

  public void testUnit() {
    DiffFragment fragment = Util.unite(new DiffFragment("1", "2"), new DiffFragment("a", "b"));
    assertEquals("1a", fragment.getText1());
    assertEquals("2b", fragment.getText2());
    assertTrue(fragment.isModified());

    fragment = Util.unite(new DiffFragment("1", "1"), DiffFragment.unchanged("  ", ""));
    assertEquals("1  ", fragment.getText1());
    assertEquals("1", fragment.getText2());
    assertTrue(fragment.isEqual());

    fragment = Util.unite(new DiffFragment("1", null), new DiffFragment("2", null));
    assertEquals("12", fragment.getText1());
    assertNull(fragment.getText2());
  }

  private void prepareForFragments() {
    CHECK.setStringConvertion(new FragmentStringConvertion());
    CHECK.setEquality(new FragmentEquality());
  }

  public void testSplitByUnchagedNewLine() {
    prepareForFragments();
    DiffFragment a_b = new DiffFragment("a", "b");
    DiffFragment x_x = new DiffFragment("x", "x");
    DiffFragment cl_dl = new DiffFragment("c\n", "d\n");
    DiffFragment yl_yl = new DiffFragment("y\n", "y\n");
    DiffFragment zl_z = new DiffFragment("z\n", "z");
    DiffFragment z_zl = new DiffFragment("z", "z\n");
    DiffFragment al_ = new DiffFragment("a\n", null);
    DiffFragment _al = new DiffFragment(null, "a\n");
    DiffFragment[] originalFragments = new DiffFragment[]{a_b, x_x, cl_dl, a_b, yl_yl,
                                                         x_x, zl_z, z_zl, yl_yl,
                                                         new DiffFragment("y\nx", "y\nx"),
                                                         a_b, al_, _al};
    CHECK.compareAll(new DiffFragment[][]{
      new DiffFragment[]{a_b, x_x, cl_dl, a_b, yl_yl},
      new DiffFragment[]{x_x, zl_z, z_zl, yl_yl},
      new DiffFragment[]{yl_yl},
      new DiffFragment[]{x_x, a_b, al_, _al}
    }, Util.splitByUnchangedLines(originalFragments));

    CHECK.compareAll(new DiffFragment[][]{new DiffFragment[]{new DiffFragment("abc\n", "abc\n")},
                                          new DiffFragment[]{DiffFragment.unchanged("123\n", "123")}},
                     Util.splitByUnchangedLines(new DiffFragment[]{DiffFragment.unchanged("abc\n123\n", "abc\n123")}));
    CHECK.compareAll(new DiffFragment[][]{new DiffFragment[]{DiffFragment.unchanged("a\n ", "a")}},
                     Util.splitByUnchangedLines(new DiffFragment[]{DiffFragment.unchanged("a\n ", "a")}));
  }

  public void testSplitByUnchangedLinesIgnoringSpaces() {
    DiffFragment[][] diffFragments = Util.splitByUnchangedLines(new DiffFragment[]{DiffFragment.unchanged("f(a, b)\n", "f(a,\nb)\n")});
    assertEquals(1, diffFragments.length);
    DiffFragment[] line = diffFragments[0];
    assertEquals(1, line.length);
    assertTrue(line[0].isEqual());
  }

  public void testConcatEquals() throws FilesTooBigForDiffException {
    Object[] left = new String[]{"a", "x", "a", "b"};
    Object[] right = new String[]{"a", "b"};
    Diff.Change change = Diff.buildChanges(left, right);
    Diff.Change newChange = Util.concatEquals(change, left, right);
    MultiCheck multiCheck = new MultiCheck();
    multiCheck.assertEquals(0, newChange.line0);
    multiCheck.assertEquals(0, newChange.line1);
    multiCheck.assertEquals(2, newChange.deleted);
    multiCheck.assertEquals(0, newChange.inserted);
    multiCheck.assertNull(newChange.link);
    multiCheck.flush();

    change = Diff.buildChanges(right, left);
    newChange = Util.concatEquals(change, right, left);
    multiCheck = new MultiCheck();
    multiCheck.assertEquals(0, newChange.line0);
    multiCheck.assertEquals(0, newChange.line1);
    multiCheck.assertEquals(0, newChange.deleted);
    multiCheck.assertEquals(2, newChange.inserted);
    multiCheck.assertNull(newChange.link);
    multiCheck.flush();

    left = new String[]{"a", "x", "a", "1", "b"};
    right = new String[]{"a", "b"};
    change = Diff.buildChanges(left, right);
    newChange = Util.concatEquals(change, left, right);
    multiCheck = new MultiCheck();
    multiCheck.assertEquals(1, newChange.line0);
    multiCheck.assertEquals(1, newChange.line1);
    multiCheck.assertEquals(3, newChange.deleted);
    multiCheck.assertEquals(0, newChange.inserted);
    multiCheck.assertNull(newChange.link);
    multiCheck.flush();

    left = new String[]{"y", "y", "y", "a", "2", "a", "b"};
    right = new String[]{"x", "a", "b"};
    change = Diff.buildChanges(left, right);
    newChange = Util.concatEquals(change, left, right);
    multiCheck = new MultiCheck();
    multiCheck.assertEquals(0, newChange.line0);
    multiCheck.assertEquals(0, newChange.line1);
    multiCheck.assertEquals(5, newChange.deleted);
    multiCheck.assertEquals(1, newChange.inserted);
    multiCheck.flush();

    left = new String[]{"y", "y", "y", "a", "2", "a", "b", "*"};
    right = new String[]{"x", "a", "b", "@"};
    change = Diff.buildChanges(left, right);
    newChange = Util.concatEquals(change, left, right);
    multiCheck = new MultiCheck();
    multiCheck.assertEquals(0, newChange.line0);
    multiCheck.assertEquals(0, newChange.line1);
    multiCheck.assertEquals(3, newChange.deleted);
    multiCheck.assertEquals(1, newChange.inserted);
    newChange = newChange.link;
    multiCheck.assertEquals(3, newChange.line0);
    multiCheck.assertEquals(1, newChange.line1);
    multiCheck.assertEquals(2, newChange.deleted);
    multiCheck.assertEquals(0, newChange.inserted);
    newChange = newChange.link;
    multiCheck.assertEquals(7, newChange.line0);
    multiCheck.assertEquals(3, newChange.line1);
    multiCheck.assertEquals(1, newChange.deleted);
    multiCheck.assertEquals(1, newChange.inserted);
    multiCheck.assertNull(newChange.link);
    multiCheck.flush();
  }

  public void testConcatEqualsConcatenatesChanged() throws FilesTooBigForDiffException {
    String[] left = new String[]{"i1", "a", "i2", "a", "b"};
    String[] right = new String[]{"a", "b"};
    Diff.Change change = Diff.buildChanges(left, right);
    MultiCheck multiCheck = new MultiCheck();
    multiCheck.assertEquals(0, change.line0);
    multiCheck.assertEquals(0, change.line1);
    multiCheck.assertEquals(3, change.deleted);
    multiCheck.assertEquals(0, change.inserted);
    multiCheck.assertNull(change.link);
    multiCheck.flush();

    left = new String[]{"i1", "a", "i2", "a", "b", "*"};
    right = new String[]{"a", "b", "$"};
    change = Diff.buildChanges(left, right);
    assertNotNull(change.link);
    assertEquals(2, change.link.deleted);
    assertEquals(2, change.link.line0);
    Diff.Change newChange = Util.concatEquals(change, left, right);
    multiCheck.assertEquals(0, newChange.line0);
    multiCheck.assertEquals(0, newChange.line1);
    multiCheck.assertEquals(3, newChange.deleted);
    multiCheck.assertEquals(0, newChange.inserted);
    assertNotNull(newChange.link);
    newChange = newChange.link;
    multiCheck.assertEquals(5, newChange.line0);
    multiCheck.assertEquals(2, newChange.line1);
    multiCheck.assertEquals(1, newChange.deleted);
    multiCheck.assertEquals(1, newChange.inserted);
    multiCheck.assertNull(newChange.link);
    multiCheck.flush();
  }

  public void testCalcShift() {
    assertEquals(-1, Util.calcShift(new String[]{"1", "a", "x", "a"}, 1, 2, 2));
    assertEquals(0, Util.calcShift(new String[]{"1", "a", "x", "b"}, 1, 2, 2));
    assertEquals(0, Util.calcShift(new String[]{"1", "a", "x", "a"}, 0, 2, 2));
    assertEquals(-2, Util.calcShift(new String[]{"1", "a", "b", "x", "a", "b"}, 1, 3, 3));
  }

  public void testSplitByLines() {
    Util.splitByLines(new DiffFragment("false;", "false;"));
  }

  public void testUniteFormattingOnly() {
    prepareForFragments();
    DiffFragment[] first = new DiffFragment[]{DiffFragment.unchanged("123", "abc")};
    DiffFragment[] last = new DiffFragment[]{new DiffFragment("qqq", "qqq")};
    DiffFragment inline1 = new DiffFragment(" ", "  ");
    DiffFragment inline2 = DiffFragment.unchanged("xyz", "cba");
    DiffFragment inline3 = new DiffFragment("  ", " ");
    DiffFragment inline4 = DiffFragment.unchanged("098", "890");
    DiffFragment[][] lines = new DiffFragment[][]{
      first,
      new DiffFragment[]{inline1, inline2},
      new DiffFragment[]{inline3, inline4},
      last};
    lines = Util.uniteFormattingOnly(lines);
    CHECK.compareAll(new DiffFragment[][]{
                       first,
                       new DiffFragment[]{inline1, inline2, inline3, inline4},
                       last},
                     lines
    );
  }

  public void testConcatenateEquals() {
    prepareForFragments();
    DiffFragment fragments = Util.concatenate(new DiffFragment[]{
      new DiffFragment("a", "a"),
      DiffFragment.unchanged("1", "XY"),
      DiffFragment.unchanged("2\n3", "Q\nW\nE")});
    assertTrue(fragments.isEqual());
    assertFalse(fragments.isOneSide());
    assertEquals("a12\n3", fragments.getText1());
    assertEquals("aXYQ\nW\nE", fragments.getText2());
  }

  public void testConcatenateModified() {
    DiffFragment fragment = Util.concatenate(new DiffFragment[]{new DiffFragment("a", "b"),
                                                                DiffFragment.unchanged("1", "1")});
    assertTrue(fragment.isModified());
  }

  public void testConcatenateWithOneSide() {
    DiffFragment fragment = Util.concatenate(new DiffFragment[]{new DiffFragment("1", "1"),
                                                                new DiffFragment("a", null)});
    assertTrue(fragment.isModified());
    assertFalse(fragment.isOneSide());
  }

  public void testCutFirst() {
    prepareForFragments();

    CHECK.singleElement(Util.cutFirst(new DiffFragment[]{
                          DiffFragment.unchanged("ab", "ac")
                        }),
                        DiffFragment.unchanged("b", "c")
    );

    CHECK.compareAll(new DiffFragment[]{
                       new DiffFragment(null, "c")
                     },
                     Util.cutFirst(new DiffFragment[]{
                       new DiffFragment(null, "b"),
                       new DiffFragment(null, "c"),
                       new DiffFragment("a", null)})
                     );

    CHECK.compareAll(new DiffFragment[]{
                       new DiffFragment(null, "b"),
                       new DiffFragment(null, "d")
                     },
                     Util.cutFirst(new DiffFragment[]{
                       new DiffFragment(null, "ab"),
                       new DiffFragment("c", "d")
                     })
    );
  }

  public void testCutFirst2() {
    prepareForFragments();

    CHECK.compareAll(new DiffFragment[] {
                       new DiffFragment(null, ")"),
                       new DiffFragment(" {", " {")
                     },
                     Util.cutFirst(new DiffFragment[] {
                       new DiffFragment(null, ")"),
                       new DiffFragment(") {", ") {" )
                     }));
  }

  public void testCutFirst3() {
    prepareForFragments();

    CHECK.compareAll(new DiffFragment[] {
                       new DiffFragment(null, ", ?"),
                       new DiffFragment(")\");", ")\");")
                     },
                     Util.cutFirst(new DiffFragment[] {
                       new DiffFragment(null, "?, "),
                       new DiffFragment("?)\");", "?)\");")
                     }));

  }

  public static void assertEquals(CharSequence obj1, CharSequence obj2) {
    assertEquals(obj1.toString(), obj2.toString());
  }
}
