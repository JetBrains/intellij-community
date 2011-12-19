package com.intellij.openapi.diff.impl.processing;

import com.intellij.openapi.diff.ex.DiffFragment;
import junit.framework.TestCase;

public class PreferWholeLinesTest extends TestCase {
  public void test() {
    DiffFragment[] fragments = new DiffFragment[]{new DiffFragment("1", "2"), new DiffFragment(null, "\nadded"), new DiffFragment("a", "\nb")};
    fragments = new PreferWholeLines().correct(fragments);

  }
}
