// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.covertToStatic

import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle
import org.jetbrains.plugins.groovy.refactoring.convertToStatic.ConvertToStaticProcessor
import org.jetbrains.plugins.groovy.util.TestUtils

class ConvertTest extends LightGroovyTestCase {
  final String basePath = TestUtils.testDataPath + 'refactoring/convertToStatic'

  private void doTest() {
    final String testName = getTestName(true)
    final GroovyFile file = myFixture.configureByFile("${testName}.groovy") as GroovyFile
    new ConvertToStaticProcessor(project, file).run()
    myFixture.checkResultByFile("${testName}_after.groovy")
  }

  private void doIntentionTest() {
    final String testName = getTestName(true)
    myFixture.configureByFile("${testName}.groovy") as GroovyFile
    myFixture.launchAction myFixture.findSingleIntention(GroovyRefactoringBundle.message("intention.converting.to.static"))
    myFixture.checkResultByFile("${testName}_after.groovy")
  }

  void testIgnoreMetaprogramming() {
    doTest()
  }

  void testAddSkippedTypes() {
    doTest()
  }

  void testAddNecessaryConverts() {
    doTest()
  }

  void testSpreadArguments() {
    doTest()
  }

  void testConvertGString() {
    doTest()
  }

  void testMultiAssignment() {
    doTest()
  }

  void testMarkupPrintScript() {
    doTest()
  }

  void testDontAddAnnotationTwice() {
    doTest()
  }

  void testCompileDynamicClass() {
    doTest()
  }

  void testNestedClasses() {
    doTest()
  }

  void testIntentionOnUnresolvedRefs() {
    doIntentionTest()
  }

  void testIntentionOnUnresolvedWithMethodQualifier() {
    doIntentionTest()
  }

  void testIntentionOnProperties() {
    doIntentionTest()
  }

  void testIntentionNecessaryConverts() {
    doIntentionTest()
  }
}
