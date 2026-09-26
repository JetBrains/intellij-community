// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.closure.forToEach;

import org.jetbrains.plugins.groovy.intentions.GrIntentionTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

public class ForToEachWithIndexIntentionTest extends GrIntentionTestCase {
  public ForToEachWithIndexIntentionTest() {
    super("Replace with \".eachWithIndex\"");
  }

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "intentions/ForToEachWithIndex/";
  }

  public void testForToEachWithIndex() {
    doTest(true);
  }
}
