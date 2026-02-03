// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.closure.forToEach;

import org.jetbrains.plugins.groovy.intentions.GrIntentionTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

public class ForToEachIntentionTest extends GrIntentionTestCase {
  public ForToEachIntentionTest() {
    super("Replace with \".each\"");
  }

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "intentions/ForToEach/";
  }

  public void testForToEachOnRangeWithoutParentheses() {
    doTest(true);
  }

  public void testForToEachOnRangeWithParentheses() {
    doTest(true);
  }
}
