package com.intellij.openapi.diff.impl.processing;

import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.diff.impl.highlighting.FragmentEquality;
import com.intellij.openapi.diff.impl.highlighting.FragmentStringConvertion;
import com.intellij.util.Assertion;
import com.intellij.util.diff.FilesTooBigForDiffException;
import junit.framework.TestCase;

public class NormalizationTest extends TestCase {
  private final Assertion CHECK = new Assertion(new FragmentStringConvertion());

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    CHECK.setEquality(new FragmentEquality());
  }

  public void testSingleSide() throws FilesTooBigForDiffException {
    DiffCorrection correction = DiffCorrection.Normalize.INSTANCE;
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

  public void testUnitesEquals() throws FilesTooBigForDiffException {
    DiffCorrection correction = DiffCorrection.Normalize.INSTANCE;
    DiffFragment[] fragments = correction.correct(new DiffFragment[]{new DiffFragment(null, "a"),
                                            new DiffFragment("x", "x"),
                                            new DiffFragment("y", "y"),
                                            new DiffFragment("z", null), new DiffFragment(null, "z")});
    CHECK.compareAll(new DiffFragment[]{new DiffFragment(null, "a"), new DiffFragment("xyz", "xyz")}, fragments);
  }
}
