// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.closure.forToEach;

import org.jetbrains.plugins.groovy.intentions.GrIntentionTestCase;
import org.jetbrains.plugins.groovy.intentions.closure.ForToEachIntention;
import org.jetbrains.plugins.groovy.util.TestUtils;

class ForToEachIntentionTest extends GrIntentionTestCase {

  ForToEachIntentionTest() {
    super("Replace with \".each\"")
  }

  @Override
  protected String getBasePath() {
    return TestUtils.testDataPath + "intentions/ForToEach/"
  }

  void testForToEachOnRangeWithoutParentheses() {
    doTest(true)
  }

  void testForToEachOnRangeWithParentheses() {
    doTest(true)
  }
}
