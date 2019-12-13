// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.fixes

import com.intellij.psi.impl.source.PostprocessReformattingAspect
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase
import org.jetbrains.plugins.groovy.util.TestUtils

@CompileStatic
class CreateMethodFromUsageTest extends GrHighlightingTestBase {
  private static final String BEFORE = "Before.groovy"
  private static final String AFTER = "After.groovy"
  private static final String USAGE = "script.groovy"

  public static final String CREATE_METHOD = 'Create method'
  public static final String CREATE_ABSTRACT_METHOD = 'Create abstract method'
  public static final String CREATE_CONSTRUCTOR = 'Create constructor'

  final String getBasePath() {
    return TestUtils.testDataPath + 'fixes/createMethodFromUsage/' + getTestName(true) + '/'
  }

  @Override
  void setUp() {
    super.setUp()
    fixture.enableInspections(GrUnresolvedAccessInspection, GroovyAssignabilityCheckInspection)
  }

  private void doTest(String action = CREATE_METHOD, int actionCount = 1, String[] files = [USAGE, BEFORE]) {
    fixture.with {
      configureByFiles(files)
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

  void _testSimple2() {
    doTest()
  }

  void _testSimple3() {
    doTest()
  }

  void testSimple4() {
    doTest()
  }

  void testApplicationStatement() {
    doTest(CREATE_METHOD, 1, BEFORE)
  }

  void testInapplicableApplicationStatement() {
    doTest(CREATE_METHOD, 1, BEFORE)
  }

  void testAbstract() {
    doTest(CREATE_ABSTRACT_METHOD)
  }

  void testAbstractStatic() {
    doTest(CREATE_ABSTRACT_METHOD, 0)
  }

  void testAbstractInNonAbstract() {
    doTest(CREATE_ABSTRACT_METHOD, 0)
  }

  void testAbstractInInterface() {
    doTest()
  }

  void testArrayParam() {
    doTest()
  }

  void testAssertDescription() {
    doTest()
  }

  void testGeneric() {
    doTest()
  }

  void testMultiMap() {
    doTest()
  }

  void testClosureArgument() {
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

  void _testParameterNameSuggestion() {
    doTest()
  }

  void _testPolyadicExpression() {
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
    doTest(CREATE_METHOD, 1)
  }

  void testIntegerCast() {
    doTest()
  }

  void testSeveralArguments() {
    doTest()
  }

  void testConstructor1() {
    doTest(CREATE_CONSTRUCTOR)
  }

  void testConstructor2() {
    doTest(CREATE_CONSTRUCTOR)
  }

  void testConstructorAnon() {
    doTest(CREATE_CONSTRUCTOR)
  }

  void testConstructorInterface() {
    doTest(CREATE_CONSTRUCTOR, 0)
  }

  void testConstructorTrait() {
    doTest(CREATE_CONSTRUCTOR, 0)
  }

  void testConstructorEnum() {
    doTest(CREATE_CONSTRUCTOR, 0)
  }

  void testConstructorInvocation() {
    doTest(CREATE_CONSTRUCTOR, 1, BEFORE)
  }

  void testSuperConstructorInvocation() {
    doTest(CREATE_CONSTRUCTOR, 1, BEFORE)
  }
}


