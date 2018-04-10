/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.inspections


import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Maxim.Medvedev
 */
class CastToTypeTest extends LightCodeInsightFixtureTestCase {
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

  void testSimple() {doTest('Cast to List<? extends Abc>')}
  void testInReturnType() {doTest('Cast to int')}
  void testInForCycle() {doTest('Cast to List<Integer>')}
}
