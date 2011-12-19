package com.intellij.openapi.diff.impl.highlighting;

import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.util.Comparing;
import gnu.trove.Equality;

public class FragmentEquality implements Equality {
  @Override
  public boolean equals(Object o1, Object o2) {
    DiffFragment fragment1 = (DiffFragment) o1;
    DiffFragment fragment2 = (DiffFragment) o2;
    return Comparing.equal(fragment1.getText1(), fragment2.getText1()) &&
           Comparing.equal(fragment1.getText2(), fragment2.getText2()) &&
           fragment1.isModified() == fragment2.isModified();
  }
}
