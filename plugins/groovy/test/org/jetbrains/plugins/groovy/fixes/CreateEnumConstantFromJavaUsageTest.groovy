// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.fixes

import com.intellij.psi.impl.source.PostprocessReformattingAspect
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase
import org.jetbrains.plugins.groovy.util.TestUtils

@CompileStatic
class CreateEnumConstantFromJavaUsageTest extends GrHighlightingTestBase {
  private static final String BEFORE = "Before.groovy"
  private static final String AFTER = "After.groovy"
  private static final String JAVA = "Area.java"
  final String getBasePath() {
    return TestUtils.testDataPath + 'fixes/createEnumConstantFromUsage/' + getTestName(true) + '/'
  }

  @Override
  void setUp(){
    super.setUp()
    fixture.configureByFiles(JAVA, BEFORE)
    fixture.enableInspections(customInspections)
  }

  private void doTest() {
    fixture.with {
      def fixes = filterAvailableIntentions('Create enum constant')
      assert fixes.size() == 1
      launchAction fixes.first()
      PostprocessReformattingAspect.getInstance(project).doPostponedFormatting()
      checkResultByFile(BEFORE, AFTER, true)
    }
  }

  void testSimple() {
    doTest()
  }

  void testSimple2() {
    doTest()
  }

  void testEmptyEnum() {
    doTest()
  }

  void testEmptyEnumWithMethods() {
    doTest()
  }

  void testWithConstructorArguments() {
    doTest()
  }

  void testWithStaticImport() {
    doTest()
  }

  void testWithSwitch() {
    doTest()
  }

  void testWithSwitch2() {
    doTest()
  }

  void testWithVarargs() {
    doTest()
  }
}


