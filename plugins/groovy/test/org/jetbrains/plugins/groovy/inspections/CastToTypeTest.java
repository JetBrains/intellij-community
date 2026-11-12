// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.inspections;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Maxim.Medvedev
 */
@TestApplication
public class CastToTypeTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "groovy/inspections/castToType");


  private void doTest(String name) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(true) + ".groovy");
    myFixture.enableInspections(new GroovyAssignabilityCheckInspection());
    final IntentionAction quickFix = myFixture.findSingleIntention(name);
    assertNotNull(quickFix);
    myFixture.launchAction(quickFix);
    myFixture.checkResultByFile(myFixture.getTestName(true) + "_after.groovy");
  }

  @SuppressWarnings("SameParameterValue")
  private void checkIntentions(String hint, Set<String> intentions) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(true) + ".groovy");
    myFixture.enableInspections(new GroovyAssignabilityCheckInspection());
    assertEquals(intentions,
                          myFixture.filterAvailableIntentions(hint).stream().map(it -> it.getText()).collect(Collectors.toSet()));
  }

  @Test
  public void testSimple() { doTest("Cast to List<? extends Abc>"); }

  @Test
  public void testInReturnType() { doTest("Cast to int"); }

  @Test
  public void testInForCycle() { doTest("Cast to List<Integer>"); }

  @Test
  public void testInBinaryExpression() { doTest("Cast operand to String"); }

  @Test
  public void testBinaryExpressionTwoIntentions() {
    checkIntentions("Cast", Set.of("Cast operand to Integer", "Cast operand to Double"));
  }

  @Test
  public void testConstructorInvocation() {
    doTest("Cast 1st parameter to Integer");
  }

  @Test
  public void testConstructorInvocationSeveralFixes() {
    checkIntentions("Cast", Set.of("Cast 1st parameter to Boolean", "Cast 1st parameter to String", "Cast 2nd parameter to Double"));
  }

  @Test
  public void testNewExpression() {
    doTest("Cast 1st parameter to Integer");
  }

  @Test
  public void testNewExpressionSeveralFixes() {
    checkIntentions("Cast", Set.of("Cast 1st parameter to Boolean", "Cast 1st parameter to String", "Cast 2nd parameter to Double"));
  }
}
