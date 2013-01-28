/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * @author Max Medvedev
 */
abstract class GrHighlightingTestBase extends LightGroovyTestCase {
  String getBasePath() {
    TestUtils.testDataPath + 'highlighting/'
  }

  InspectionProfileEntry[] getCustomInspections() {[]}

  void doTest(boolean checkWarnings = true, boolean checkInfos = false, boolean checkWeakWarnings = true, InspectionProfileEntry... tools) {
    myFixture.enableInspections(tools);
    myFixture.enableInspections(customInspections)
    myFixture.testHighlighting(checkWarnings, checkInfos, checkWeakWarnings, getTestName(false) + ".groovy");
  }

  void doRefTest(boolean checkWarnings = true, boolean checkInfos = false, boolean checkWeakWarnings = true, InspectionProfileEntry... tools) {
    myFixture.enableInspections(new GrUnresolvedAccessInspection())
    myFixture.enableInspections(tools)
    myFixture.enableInspections(customInspections)
    myFixture.testHighlighting(checkWarnings, checkInfos, checkWeakWarnings, getTestName(false) + '.groovy')
  }

  void testHighlighting(String text, boolean checkWarnings = true, boolean checkInfos = false, boolean checkWeakWarnings = true, Class<? extends LocalInspectionTool>... inspections) {
    myFixture.configureByText('_.groovy', text)
    myFixture.enableInspections(inspections)
    myFixture.enableInspections(customInspections)
    myFixture.testHighlighting(checkWarnings, checkInfos, checkWeakWarnings)
  }

}
