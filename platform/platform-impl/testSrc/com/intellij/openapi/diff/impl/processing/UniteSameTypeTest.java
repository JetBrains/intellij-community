package com.intellij.openapi.diff.impl.processing;

import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.diff.impl.highlighting.FragmentEquality;
import com.intellij.openapi.diff.impl.highlighting.FragmentStringConvertion;
import com.intellij.util.Assertion;
import com.intellij.util.diff.FilesTooBigForDiffException;
import junit.framework.TestCase;

public class UniteSameTypeTest extends TestCase {
  private final Assertion CHECK = new Assertion(new FragmentStringConvertion());

  @Override
  public void setUp() throws Exception {
    super.setUp();
    CHECK.setEquality(new FragmentEquality());
  }

  public void testUnitDifferentOnesides() throws FilesTooBigForDiffException {
    DiffFragment[] fragments = UniteSameType.INSTANCE.correct(new DiffFragment[]{new DiffFragment("a", "b"),
                                                        new DiffFragment(null, " "),
                                                        new DiffFragment("\n ", null),
                                                        new DiffFragment("x", "x")});
    CHECK.compareAll(new DiffFragment[]{new DiffFragment("a\n ", "b "), new DiffFragment("x", "x")}, fragments);
  }

  public void testUniteEqualsUnitesFormattingOnly() throws FilesTooBigForDiffException {
    DiffFragment changed = new DiffFragment("abc", "123");
    DiffFragment equal = new DiffFragment("qqq", "qqq");
    DiffFragment[] fragments = DiffCorrection.UnitEquals.INSTANCE.correct(new DiffFragment[]{
      changed,
      new DiffFragment(" xxx", "xxx"), new DiffFragment("yyy", "  yyy"),
      equal});
    CHECK.compareAll(new DiffFragment[]{changed, new DiffFragment(" xxxyyy", "xxx  yyy"), equal}, fragments);
  }
}
