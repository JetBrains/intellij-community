// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author knisht
 */
class InferMethodParametersTypesIntentionTest extends GrIntentionTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_3_0
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

  void testAppearedWildcard2() {
    doTest(true)
  }

  void testConstructor() {
    doTest(true)
  }

  void testConstructor2() {
    doTest(true)
  }

  void testRedundantTypeParameter() {
    doTest(true)
  }

  void testTwoTypeParameters() {
    doTest(true)
  }

  void testTwoSupertypes() {
    doTest(true)
  }

  void testTwoSupertypes2() {
    doTest(true)
  }

  void testTwoSupertypes3() {
    doTest(true)
  }

  void testTwoSupertypes4() {
    doTest(true)
  }

  void testTwoSupertypes5() {
    doTest(true)
  }

  void testThreeSupertypes() {
    doTest(true)
  }

  void testThreeSupertypes2() {
    doTest(true)
  }

  void testCustomSetter() {
    doTest(true)
  }

  void testMultipleInterfaces() {
    doTest(true)
  }

  void testRaw() {
    doTest(true)
  }

  void testDefaultValues() {
    doTest(true)
  }

  void testDiamond() {
    doTest(true)
  }

  void testParametrizedInterface() {
    doTest(true)
  }

  void testBasicClosure() {
    doTest(true)
  }

  void testImplicitClosureParameter() {
    doTest(true)
  }

  void testTypeParameterInClosureSignature() {
    doTest(true)
  }

  void testClosureParameterDependsOnMethodParameter() {
    doTest(true)
  }

  void testClosureParameterDependsOnMethodParameter2() {
    doTest(true)
  }

  void testTwoClosuresAsArgument() {
    doTest(true)
  }

  void testDeepClosureDependency() {
    doTest(true)
  }

  void testParametrizedArray() {
    doTest(true)
  }

  void testDifferentCallPlaces() {
    doTest(true)
  }

  void testAuxiliaryStatements() {
    doTest(true)
  }

  void testExplicitCallForClosureParameter() {
    doTest(true)
  }

}
