package com.intellij.openapi.diff.impl.highlighting;

import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.util.Assertion;
import junit.framework.TestCase;

public class LineBlockDividesTest extends TestCase {
  private final Assertion CHECK = new Assertion(new FragmentStringConvertion());

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    CHECK.setEquality(new FragmentEquality());
  }

  public void testSingleSide() {
    DiffFragment abc_ = new DiffFragment("abc", null);
    DiffFragment xyzL_ = new DiffFragment("xyz\n", null);
    DiffFragment x_y = new DiffFragment("x", "y");
    DiffFragment a_b = new DiffFragment("a", "b");
    DiffFragment xyzL_L = new DiffFragment("xyz\n", "\n");
    DiffFragment abcL_ = new DiffFragment("abc\n", null);
    DiffFragment[][] lineBlocks = LineBlockDivider.SINGLE_SIDE.divide(new DiffFragment[]{
      abc_, xyzL_,
      x_y, a_b, xyzL_L,
      abcL_});
    CHECK.compareAll(new DiffFragment[][]{
      new DiffFragment[]{abc_, xyzL_}, new DiffFragment[]{x_y, a_b, xyzL_L}, new DiffFragment[]{abcL_}},
                     lineBlocks);
  }
}
