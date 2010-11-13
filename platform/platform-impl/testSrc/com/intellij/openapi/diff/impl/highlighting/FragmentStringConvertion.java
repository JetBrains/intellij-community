package com.intellij.openapi.diff.impl.highlighting;

import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.util.StringConvertion;
import junit.framework.Assert;

public class FragmentStringConvertion extends StringConvertion {
  @Override
  public String convert(Object obj) {
    Assert.assertNotNull(obj);
    DiffFragment fragment = (DiffFragment)obj;
    return String.valueOf(fragment.getText1()) + "->" + String.valueOf(fragment.getText2()) + "\n-----------";
  }
}
