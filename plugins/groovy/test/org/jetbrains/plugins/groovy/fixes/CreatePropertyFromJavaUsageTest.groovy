// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.fixes

import com.intellij.psi.impl.source.PostprocessReformattingAspect
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase
import org.jetbrains.plugins.groovy.util.TestUtils

@CompileStatic
class CreatePropertyFromJavaUsageTest extends GrHighlightingTestBase {
  private static final String BEFORE = "Before.groovy"
  private static final String AFTER = "After.groovy"
  private static final String JAVA = "Area.java"
  private static final String CREATE_PROPERTY = 'Create property'
  private static final String CREATE_RO_PROPERTY = 'Create read-only property'

  final String getBasePath() {
    return TestUtils.testDataPath + 'fixes/createPropertyFromJava/' + getTestName(true) + '/'
  }

  @Override
  void setUp(){
    super.setUp()
    fixture.configureByFiles(JAVA, BEFORE)
    fixture.enableInspections(customInspections)
  }

  private void doTest(String action = CREATE_PROPERTY, int actionCount = 1) {
    fixture.with {
      def fixes = filterAvailableIntentions(action)
      assert fixes.size() == actionCount
      if (actionCount == 0) return
      launchAction fixes.first()
      PostprocessReformattingAspect.getInstance(project).doPostponedFormatting()
      checkResultByFile(BEFORE, AFTER, true)
    }
  }

  void testFromSetter() {
    doTest()
  }

  void testFromGetter() {
    doTest()
  }

  void testFromGetterWithFirstLowerLetter() {
    doTest()
  }

  void testBooleanFromGetter() {
    doTest()
  }

  void testFromGetterReadOnly() {
    doTest(CREATE_RO_PROPERTY)
  }

  void testBooleanFromGetterReadOnly() {
    doTest(CREATE_RO_PROPERTY)
  }

  void testFromSetterReadOnly() {
    doTest(CREATE_RO_PROPERTY, 0)
  }

  void testPassedLambda() {
    doTest()
  }

  void testFromSetterWithExistGetter() {
    doTest(CREATE_PROPERTY, 0)
  }

  void testFromSetterWithFieldExist() {
    doTest(CREATE_PROPERTY, 0)
  }

  void testFromBooleanGetterWithExistSetter() {
    doTest(CREATE_PROPERTY, 0)
  }

  void testFromBooleanGetterWithArgs() {
    doTest(CREATE_PROPERTY, 0)
  }

  void testFromSetterWithoutArgs() {
    doTest(CREATE_PROPERTY, 0)
  }

  void testInInterface() {
    doTest(CREATE_PROPERTY, 0)
  }

  void testFromGetterWithLowercaseName() {
    doTest(CREATE_PROPERTY, 0)
  }
}