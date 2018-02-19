// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.fixes

import com.intellij.psi.impl.source.PostprocessReformattingAspect
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase
import org.jetbrains.plugins.groovy.util.TestUtils

@CompileStatic
class CreateMethodFromJavaUsageTest extends GrHighlightingTestBase {
  private static final String BEFORE = "Before.groovy"
  private static final String AFTER = "After.groovy"
  private static final String JAVA = "Area.java"

  final String getBasePath() {
    return TestUtils.testDataPath + 'fixes/createMethodFromJava/' + getTestName(true) + '/'
  }

  @Override
  void setUp() {
    super.setUp()
    fixture.configureByFiles(JAVA, BEFORE)
    fixture.enableInspections(customInspections)
  }

  private void doTest(String action = 'Create method', int actionCount = 1) {
    fixture.with {
      def fixes = filterAvailableIntentions(action)
      assert fixes.size() == actionCount
      if (actionCount == 0) return
      launchAction fixes.first()
      PostprocessReformattingAspect.getInstance(project).doPostponedFormatting()
      checkResultByFile(BEFORE, AFTER, true)
    }
  }

  void testSimple1() {
    doTest()
  }

  void testSimple2() {
    doTest()
  }

  void testSimple3() {
    doTest()
  }

  void testSimple4() {
    doTest()
  }

  void testAbstract() {
    doTest('Create abstract method')
  }

  void testAbstractStatic() {
    doTest('Create abstract method', 0)
  }

  void testAbstractInNonAbstract() {
    doTest('Create abstract method', 0)
  }

  void testAbstractInInterface() {
    doTest('Create abstract method')
  }

  void testArrayParam() {
    doTest()
  }

  void testAssertDescription() {
    doTest()
  }

  void _testGeneric() {
    doTest()
  }

  void testMultiMap() {
    doTest()
  }

  void testLambda() {
    doTest()
  }

  void testMethodReference() {
    doTest()
  }

  void testSeveralReturnTypes() {
    doTest()
  }

  void testCapturedWildcard() {
    doTest()
  }

  void testParameterNameSuggestion() {
    doTest()
  }

  void testPolyadicExpression() {
    doTest()
  }

  void testNestedExpression() {
    doTest()
  }

  void testInAnonymousClass() {
    doTest()
  }

  void testTypeParameterFromWildcard() {
    doTest()
  }

  void testUnresolvedArg() {
    doTest('Create method', 0)
  }

  void testIntegerCast() {
    doTest()
  }

  void testSeveralArguments() {
    doTest()
  }
}


