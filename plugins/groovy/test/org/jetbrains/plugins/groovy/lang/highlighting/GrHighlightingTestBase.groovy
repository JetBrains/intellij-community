// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    myFixture.enableInspections(tools)
    myFixture.enableInspections(customInspections)
    myFixture.testHighlighting(checkWarnings, checkInfos, checkWeakWarnings, getTestName(false) + ".groovy")
  }

  void doRefTest(boolean checkWarnings = true, boolean checkInfos = false, boolean checkWeakWarnings = true, InspectionProfileEntry... tools) {
    myFixture.enableInspections(new GrUnresolvedAccessInspection())
    myFixture.enableInspections(tools)
    myFixture.enableInspections(customInspections)
    myFixture.testHighlighting(checkWarnings, checkInfos, checkWeakWarnings, getTestName(false) + '.groovy')
  }

  void doTestHighlighting(String text, boolean checkWarnings = true, boolean checkInfos = false, boolean checkWeakWarnings = true, Class<? extends LocalInspectionTool>... inspections) {
    myFixture.configureByText('_.groovy', text)
    myFixture.enableInspections(inspections)
    myFixture.enableInspections(customInspections)
    myFixture.testHighlighting(checkWarnings, checkInfos, checkWeakWarnings)
  }
}
