// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.inspections;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.LightProjectDescriptor;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Maxim.Medvedev
 */
public class CastToTypeTest extends LightGroovyTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/inspections/castToType";
  }

  private void doTest(String name) {
    myFixture.configureByFile(getTestName(true) + ".groovy");
    myFixture.enableInspections(new GroovyAssignabilityCheckInspection());
    final IntentionAction quickFix = myFixture.findSingleIntention(name);
    TestCase.assertNotNull(quickFix);
    myFixture.launchAction(quickFix);
    myFixture.checkResultByFile(getTestName(true) + "_after.groovy");
  }

  @SuppressWarnings("SameParameterValue")
  private void checkIntentions(String hint, Set<String> intentions) {
    myFixture.configureByFile(getTestName(true) + ".groovy");
    myFixture.enableInspections(new GroovyAssignabilityCheckInspection());
    TestCase.assertEquals(intentions,
                          myFixture.filterAvailableIntentions(hint).stream().map(it -> it.getText()).collect(Collectors.toSet()));
  }

  public void testSimple() { doTest("Cast to List<? extends Abc>"); }

  public void testInReturnType() { doTest("Cast to int"); }

  public void testInForCycle() { doTest("Cast to List<Integer>"); }

  public void testInBinaryExpression() { doTest("Cast operand to String"); }

  public void testBinaryExpressionTwoIntentions() {
    checkIntentions("Cast", Set.of("Cast operand to Integer", "Cast operand to Double"));
  }

  public void testConstructorInvocation() {
    doTest("Cast 1st parameter to Integer");
  }

  public void testConstructorInvocationSeveralFixes() {
    checkIntentions("Cast", Set.of("Cast 1st parameter to Boolean", "Cast 1st parameter to String", "Cast 2nd parameter to Double"));
  }

  public void testNewExpression() {
    doTest("Cast 1st parameter to Integer");
  }

  public void testNewExpressionSeveralFixes() {
    checkIntentions("Cast", Set.of("Cast 1st parameter to Boolean", "Cast 1st parameter to String", "Cast 2nd parameter to Double"));
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }
}
