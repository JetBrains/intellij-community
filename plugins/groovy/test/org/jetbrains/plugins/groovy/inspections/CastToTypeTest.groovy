// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.inspections


import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Maxim.Medvedev
 */
class CastToTypeTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST

  @Override
  protected String getBasePath() {
    return TestUtils.testDataPath + 'groovy/inspections/castToType'
  }

  private void doTest(String name) {
    myFixture.configureByFile(getTestName(true) + '.groovy')
    myFixture.enableInspections(new GroovyAssignabilityCheckInspection())
    final IntentionAction quickFix = myFixture.findSingleIntention(name)
    assertNotNull(quickFix)
    myFixture.launchAction(quickFix)
    myFixture.checkResultByFile(getTestName(true) + '_after.groovy')
  }

  private void checkIntentions(String hint, Set<String> intentions) {
    myFixture.configureByFile(getTestName(true) + '.groovy')
    myFixture.enableInspections(new GroovyAssignabilityCheckInspection())
    assertEquals intentions, myFixture.filterAvailableIntentions(hint).collect {it.text}.toSet()
  }

  void testSimple() {doTest('Cast to List<? extends Abc>')}
  void testInReturnType() {doTest('Cast to int')}
  void testInForCycle() {doTest('Cast to List<Integer>')}

  void testInBinaryExpression() {doTest('Cast operand to String')}
  void testBinaryExpressionTwoIntentions() {checkIntentions('Cast', ['Cast operand to Integer', 'Cast operand to Double'].toSet())}

  void testConstructorInvocation() {
    doTest('Cast 1st parameter to Integer')
  }
  void testConstructorInvocationSeveralFixes() {
    checkIntentions('Cast', ['Cast 1st parameter to Boolean', "Cast 1st parameter to String", "Cast 2nd parameter to Double"].toSet())
  }

  void testNewExpression() {
    doTest('Cast 1st parameter to Integer')
  }
  void testNewExpressionSeveralFixes() {
    checkIntentions('Cast', ['Cast 1st parameter to Boolean', "Cast 1st parameter to String", "Cast 2nd parameter to Double"].toSet())
  }
}
