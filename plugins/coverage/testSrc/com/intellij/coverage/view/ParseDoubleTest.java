// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.view;

import org.junit.Assert;
import org.junit.Test;

public class ParseDoubleTest {
  @Test
  public void testDoubleParsing() {
    Assert.assertEquals(0.0, PercentageParser.safeParseDouble(""), 0.01);
    Assert.assertEquals(1.0, PercentageParser.safeParseDouble("1.0"), 0.01);
    Assert.assertEquals(-1.0, PercentageParser.safeParseDouble("-1.0"), 0.01);
    Assert.assertEquals(1.0, PercentageParser.safeParseDouble("xx 1"), 0.01);
    Assert.assertEquals(12.0, PercentageParser.safeParseDouble("17 12.0"), 0.01);
    Assert.assertEquals(12.0, PercentageParser.safeParseDouble("17 12 xxx"), 0.01);
  }
}
