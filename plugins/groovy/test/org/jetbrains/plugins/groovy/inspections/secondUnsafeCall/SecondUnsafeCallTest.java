// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.inspections.secondUnsafeCall;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.codeInspection.secondUnsafeCall.SecondUnsafeCallInspection;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class SecondUnsafeCallTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "groovy/inspections/secondUnsafeCall");
  }

  public void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final List<String> data = TestUtils.readInput(myFixture.getTestDataPath() + "/" + myFixture.getTestName(true) + ".test");

    myFixture.configureByText("a.groovy", data.get(0));
    myFixture.enableInspections(new SecondUnsafeCallInspection());
    final IntentionAction action = myFixture.findSingleIntention("Second unsafe call");
    myFixture.launchAction(action);

    myFixture.checkResult(data.get(1));
  }

  public void doNoTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final List<String> data = TestUtils.readInput(myFixture.getTestDataPath() + "/" + myFixture.getTestName(true) + ".test");

    myFixture.configureByText("a.groovy", data.get(0));
    myFixture.enableInspections(new SecondUnsafeCallInspection());
    final IntentionAction action = myFixture.getAvailableIntention("Second unsafe call");
    assertNull(action);
  }

  @Test
  public void test4Calls() { doTest(); }
  @Test
  public void testMethodCall() { doTest(); }
  @Test
  public void testMethodsCalls() { doTest(); }
  @Test
  public void testSecondUnsafeCall1() { doTest(); }
  @Test
  public void testVarInit() { doTest(); }
  @Test
  public void testSafeChain() { doNoTest(); }

}