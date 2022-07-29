// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.view;

import org.junit.Assert;
import org.junit.Test;

public class ParseDoubleTest {
  @Test
  public void testDoubleParsing() {
    Assert.assertEquals(0.0, PercentageCoverageColumnInfo.safeParseDouble(""), 0.01);
    Assert.assertEquals(1.0, PercentageCoverageColumnInfo.safeParseDouble("1.0"), 0.01);
    Assert.assertEquals(-1.0, PercentageCoverageColumnInfo.safeParseDouble("-1.0"), 0.01);
    Assert.assertEquals(1.0, PercentageCoverageColumnInfo.safeParseDouble("xx 1"), 0.01);
    Assert.assertEquals(12.0, PercentageCoverageColumnInfo.safeParseDouble("17 12.0"), 0.01);
    Assert.assertEquals(12.0, PercentageCoverageColumnInfo.safeParseDouble("17 12 xxx"), 0.01);
  }
}
