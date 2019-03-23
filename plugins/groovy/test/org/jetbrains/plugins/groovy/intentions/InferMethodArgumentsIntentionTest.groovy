// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions

import org.jetbrains.plugins.groovy.util.TestUtils


/**
 * @author knisht
 */
class InferMethodArgumentsIntentionTest extends GrIntentionTestCase {

  InferMethodArgumentsIntentionTest() {
    super("Infer method arguments")
  }


  final String basePath = TestUtils.testDataPath + 'refactoring/inferMethodArguments'

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

}
