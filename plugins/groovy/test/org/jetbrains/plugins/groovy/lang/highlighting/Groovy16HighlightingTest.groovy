// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.util.TestUtils

@SuppressWarnings(["JUnitTestClassNamingConvention"])
class Groovy16HighlightingTest extends LightJavaCodeInsightFixtureTestCase {

  @NotNull
  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_1_6

  final String basePath = TestUtils.testDataPath + "highlighting/"

  private void doTest(LocalInspectionTool... tools) {
    myFixture.enableInspections(tools)
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".groovy")
  }

  void testInnerEnum() { doTest() }

  void testSuperWithNotEnclosingClass() { doTest() }

  void _testThisWithWrongQualifier() { doTest() }

  void testImplicitEnumCoercion1_6() { doTest(new GroovyAssignabilityCheckInspection()) }

  void testSlashyStrings() { doTest() }

  void testDiamonds() { doTest() }

  void 'test static modifier on toplevel definition is allowed'() {
    myFixture.with {
      configureByText '_.groovy', '''\
static class A {}
static interface I {} 
'''
      checkHighlighting()
    }
  }
}