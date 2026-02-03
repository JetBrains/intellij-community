// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.closure.eachToFor;

import org.jetbrains.plugins.groovy.intentions.GrIntentionTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import static org.jetbrains.plugins.groovy.intentions.closure.EachToForIntention.HINT;

public class EachWithIndexToForIntentionTest extends GrIntentionTestCase {
  public EachWithIndexToForIntentionTest() {
    super(HINT);
  }

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "intentions/EachWithIndexToFor/";
  }

  public void testEachToFor() { doTest(true); }

  public void testEachToForWithFinal() { doTest(true); }

  public void testEachForInWithNoQualifier() { doTest(true); }

  public void testWithClosureInBody() { doTest(true); }

  public void testUpdateReturn() { doTest(true); }

  public void testUpdateReturn2() { doTest(true); }
}
