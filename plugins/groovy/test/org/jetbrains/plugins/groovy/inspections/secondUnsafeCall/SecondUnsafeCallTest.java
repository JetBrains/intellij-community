// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.inspections.secondUnsafeCall;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.codeInspection.secondUnsafeCall.SecondUnsafeCallInspection;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.Assert;

import java.util.List;

public class SecondUnsafeCallTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/inspections/secondUnsafeCall";
  }

  public void doTest() {
    final List<String> data = TestUtils.readInput(getTestDataPath() + "/" + getTestName(true) + ".test");

    myFixture.configureByText("a.groovy", data.get(0));
    myFixture.enableInspections(new SecondUnsafeCallInspection());
    final IntentionAction action = myFixture.findSingleIntention("Second unsafe call");
    myFixture.launchAction(action);

    myFixture.checkResult(data.get(1));
  }

  public void doNoTest() {
    final List<String> data = TestUtils.readInput(getTestDataPath() + "/" + getTestName(true) + ".test");

    myFixture.configureByText("a.groovy", data.get(0));
    myFixture.enableInspections(new SecondUnsafeCallInspection());
    final IntentionAction action = myFixture.getAvailableIntention("Second unsafe call");
    Assert.assertNull(action);
  }

  public void test4Calls() { doTest(); }
  public void testMethodCall() { doTest(); }
  public void testMethodsCalls() { doTest(); }
  public void testSecondUnsafeCall1() { doTest(); }
  public void testVarInit() { doTest(); }
  public void testSafeChain() { doNoTest(); }

}