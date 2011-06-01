package com.intellij.openapi.diff.impl.processing;

import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.diff.impl.ComparisonPolicy;
import com.intellij.openapi.diff.impl.highlighting.FragmentEquality;
import com.intellij.openapi.diff.impl.highlighting.FragmentStringConvertion;
import com.intellij.util.Assertion;
import com.intellij.util.diff.FilesTooBigForDiffException;
import junit.framework.TestCase;

public class CorrectionTest extends TestCase {
  private final Assertion CHECK = new Assertion(new FragmentStringConvertion());

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    CHECK.setEquality(new FragmentEquality());
  }

  public void testTrueLineBlock() throws FilesTooBigForDiffException {
    DiffCorrection.TrueLineBlocks correction = new DiffCorrection.TrueLineBlocks(ComparisonPolicy.DEFAULT);
    DiffFragment[] fragments = correction.correctAndNormalize(new DiffFragment[]{
      DiffFragment.unchanged(" 1\n  ab\n x\n", "  2\n ab\n x\n"),
      new DiffFragment("XXX\n111\n", "YYY\n222\n"),
      DiffFragment.unchanged(" a\n", "  a\n")});
    CHECK.compareAll(new DiffFragment[]{new DiffFragment(" 1\n", "  2\n"),
                                        new DiffFragment("  ab\n", " ab\n"),
                                        new DiffFragment(" x\n", " x\n"),
                                        new DiffFragment("XXX\n111\n", "YYY\n222\n"),
                                        new DiffFragment(" a\n", "  a\n")},
                     fragments);
  }

  public void testTrueLineBlocksWithSameLines() throws FilesTooBigForDiffException {
    DiffCorrection.TrueLineBlocks correction = new DiffCorrection.TrueLineBlocks(ComparisonPolicy.DEFAULT);
    DiffFragment[] fragments = correction.correctAndNormalize(new DiffFragment[]{
      DiffFragment.unchanged(" X\n  X\n  X", "  X\n X\n  X")});
    CHECK.compareAll(new DiffFragment[]{new DiffFragment(" X\n  X\n", "  X\n X\n"),
                                        new DiffFragment("  X", "  X")},
                     fragments);
  }

  public void testChangedSpaceCorrection() throws FilesTooBigForDiffException {
    DiffCorrection correction = new DiffCorrection.ChangedSpace(ComparisonPolicy.DEFAULT);
    DiffFragment[] fragments = correction.correct(new DiffFragment[]{
        new DiffFragment("x", "y"),
        new DiffFragment(" ", "   "),
        new DiffFragment("ab", "ab"),
        new DiffFragment(" ", " "),
        new DiffFragment(" ", " w o r d"),
        new DiffFragment("   ", " w o r d")});
    CHECK.compareAll(new DiffFragment[]{
      new DiffFragment("x", "y"),
      new DiffFragment(null, "  "), new DiffFragment(" ", " "),
      new DiffFragment("ab", "ab"),
      new DiffFragment(" ", " "),
      new DiffFragment(" ", " "), new DiffFragment(null, "w o r d"),
      new DiffFragment("  ", null), new DiffFragment(" ", " "), new DiffFragment(null, "w o r d")}, fragments);

    fragments = correction.correct(new DiffFragment[]{new DiffFragment("\n  ", "\n ")});
    CHECK.compareAll(new DiffFragment[]{new DiffFragment("\n", "\n"),
                                        new DiffFragment(" ", null), new DiffFragment(" ", " ")}, fragments);
    fragments = correction.correct(new DiffFragment[]{new DiffFragment("\n", "\n\n")});
    CHECK.compareAll(new DiffFragment[]{new DiffFragment("\n", "\n"), new DiffFragment(null, "\n")}, fragments);
  }

  public void testConcatinateSingleSide() throws FilesTooBigForDiffException {
    DiffCorrection correction = new DiffCorrection.ConcatenateSingleSide();
    DiffFragment[] corrected = correction.correct(
        new DiffFragment[]{new DiffFragment(null, "a"),
                           new DiffFragment("b", null),
                           new DiffFragment("c", "d"),
                           new DiffFragment(null, "a"),
                           new DiffFragment("b", null),
                           new DiffFragment("1", null),
                           new DiffFragment("x", "x"),
                           new DiffFragment(null, "a")});
    CHECK.compareAll(new DiffFragment[]{new DiffFragment("b", "a"),
                                        new DiffFragment("c", "d"),
                                        new DiffFragment("b1", "a"),
                                        new DiffFragment("x", "x"), new DiffFragment(null, "a")},
                     corrected);
  }

  public void testConnectSingleSideToChange() throws FilesTooBigForDiffException {
    DiffFragment first = DiffFragment.unchanged("a", "A");
    DiffFragment oneSide = new DiffFragment(null, "b");
    DiffFragment equal = new DiffFragment("c", "c");
    DiffFragment last = DiffFragment.unchanged("g", "G");
    DiffFragment[] fragments = DiffCorrection.ConnectSingleSideToChange.INSTANCE.correct(new DiffFragment[]{
      first,
      oneSide,
      equal,
      new DiffFragment(null, "d"), new DiffFragment("e", "E"), new DiffFragment("f", null),
      last
    });
    CHECK.compareAll(new DiffFragment[]{
      first, oneSide, equal,
      new DiffFragment("ef", "dE"),
      last
    }, fragments);
  }
}
