// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions

import org.jetbrains.plugins.groovy.util.TestUtils


/**
 * @author knisht
 */
class InferMethodParametersTypesIntentionTest extends GrIntentionTestCase {

  InferMethodParametersTypesIntentionTest() {
    super("Infer method parameters types")
  }


  final String basePath = TestUtils.testDataPath + 'refactoring/inferMethodParametersTypes'

  void testStringInference() {
    doTest(true)
  }

  void testSeveralArguments() {
    doTest(true)
  }

  void testNoInferenceInMethodBody() {
    doTest(false)
  }

  void testNoInferenceWithoutNontypedArguments() {
    doTest(false)
  }

  void testNoResolveForTypeParameters() {
    doTest(true)
  }

  void testCustomInheritance() {
    doTest(true)
  }

  void testResolvedMethodCallInside() {
    doTest(true)
  }

  void testDependencyOnTypeParameter() {
    doTest(true)
  }

  void testOperatorInference() {
    doTest(true)
  }

  void testSearchEverywhere() {
    doTest(true)
  }

  void testUpperBoundByTypeParameter() {
    doTest(true)
  }

  void testInferWildcardsDependencies() {
    doTest(true)
  }

  void testRelatedWildcards() {
    doTest(true)
  }

  void testDeepEqualityOfWildcards() {
    doTest(true)
  }

  void testDeepDependencyOfWildcards() {
    doTest(true)
  }

  void testDeepDependencyOfWildcards2() {
    doTest(true)
  }


  void testAppearedWildcard() {
    doTest(true)
  }

  void testConstructor() {
    doTest(true)
  }

  void testRedundantTypeParameter() {
    doTest(true)
  }
}
