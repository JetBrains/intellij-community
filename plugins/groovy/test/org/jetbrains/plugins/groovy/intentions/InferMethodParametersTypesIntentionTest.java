// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions

import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.intentions.style.inference.MethodParameterAugmenter
import org.jetbrains.plugins.groovy.util.TestUtils

class InferMethodParametersTypesIntentionTest extends GrIntentionTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST_REAL_JDK

  InferMethodParametersTypesIntentionTest() {
    super("Add explicit types to parameters")
  }

  @Override
  void setUp() {
    Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).setValue(true)
    super.setUp()
  }


  @Override
  void tearDown() {
    Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).resetToDefault()
    super.tearDown()
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

  void testLeaveUnusedTypeParameter() {
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

  void testDeepDependencyOfWildcards3() {
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

  void testConstructor3() {
    doTest(true)
  }

  void testConstructor4() {
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

  void testCustomSetter2() {
    doTest(true)
  }

  void testMultipleInterfaces() {
    doTest(true)
  }

  void testMultipleInterfaces2() {
    doTest(true)
  }

  void testRaw() {
    doTest(true)
  }

  void testDefaultValues() {
    doTest(true)
  }

  void testDefaultValues2() {
    doTest(true)
  }

  void testDiamond() {
    doTest(true)
  }

  void testParametrizedInterface() {
    doTest(true)
  }

  void testParametrizedInterface2() {
    doTest(true)
  }

  void testParametrizedInterface3() {
    doTest(true)
  }

  void testIntersectionInArgument() {
    doTest(true)
  }

  void testParametrizedMethod() {
    doTest(true)
  }

  void testBasicClosure() {
    doTest(true)
  }

  void testImplicitClosureParameter() {
    doTest(true)
  }

  void testInterfaceInClosureSignature() {
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

  void testDeepClosureDependency2() {
    doTest(true)
  }

  void testDeepClosureDependency3() {
    doTest(true)
  }

  void testDeepClosureDependency4() {
    doTest(true)
  }

  void testClosureSignatureFromInnerCalls() {
    doTest(true)
  }

  void testArrayOfPrimitiveTypes() {
    doTest(true)
  }

  void testParametrizedArray() {
    doTest(true)
  }

  void testCallWithArrayParameter() {
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

  void testSentClosure() {
    doTest(true)
  }

  void testVariance() {
    doTest(true)
  }

  void testVariance2() {
    doTest(true)
  }

  void testVariance3() {
    doTest(true)
  }

  void testMutualDependency() {
    doTest(true)
  }

  void testMutualDependency2() {
    doTest(true)
  }

  void testOverriddenMethod() {
    doTest(true)
  }

  void testOverriddenMethodInClass() {
    doTest(true)
  }

  void testOverriddenMethodWithoutOverrideAnnotation() {
    doTest(true)
  }

  void testOverriddenMethodWithOtherOverloads() {
    doTest(true)
  }

  void testOverriddenMethodWithErasure() {
    doTest(true)
  }

  void testOverriddenMethodWithoutType() {
    doTest(true)
  }

//  void testDeepCollectingOfCalls() {
//    doTest(true)
//  }

  void testAssignment() {
    doTest(true)
  }

  void testAssignment2() {
    doTest(true)
  }

  void testAssignment3() {
    doTest(true)
  }

  void testVarargAcceptance() {
    doTest(true)
  }

  void testVarargInference() {
    doTest(true)
  }

  void testGenericConstructor() {
    doTest(true)
  }

  void testClosureParamsInfluence() {
    doTest(true)
  }

  void testClosureParamsInfluence2() {
    doTest(true)
  }

  void testClosurePassedToDgm() {
    doTest(true)
  }

  void testPreserveAnnotations() {
    doTest(true)
  }

  void testInferClassParameter() {
    doTest(true)
  }

  void testInferClassParameter2() {
    doTest(true)
  }

  void testInferClassParameter3() {
    doTest(true)
  }

  void testInferClassParameter4() {
    doTest(true)
  }

  void testInferClassParameter5() {
    doTest(true)
  }

  void testInferClassParameter6() {
    doTest(true)
  }

  void testInferClassParameter7() {
    doTest(true)
  }

  void testInferClassParameter8() {
    doTest(true)
  }

  void testParameterDependencyInsideClosure() {
    doTest(true)
  }

  void testSuperCallInConstructor() {
    doTest(true)
  }

  void testParameterizedMethod2() {
    doTest(true)
  }

//  void testDeepEquality() {
//    doTest(true)
//  }

  void testClosureAndSamInterface() {
    doTest(true)
  }

  void testSuperWildcard() {
    doTest(true)
  }

  void testCapturedWildcard() {
    doTest(true)
  }

  void testCapturedWildcard2() {
    doTest(true)
  }

  void testDelegatingClosure() {
    doTest(true)
  }

  void testDelegateViaRehydrate() {
    doTest(true)
  }

  void testDelegateToParameter() {
    doTest(true)
  }

  void testDelegatesToNamedTarget() {
    doTest(true)
  }

  void testDelegatesToWithStrategy() {
    doTest(true)
  }

  void testDelegateInferenceFromInnerMethod() {
    doTest(true)
  }

  void testDelegationFromDgm() {
    doTest(true)
  }

  void testDelegationFromType() {
    doTest(true)
  }

  void testReturnTypeInfluence() {
    doTest(true)
  }

  void testDgm() {
    doTest(true)
  }

  void testIndirectBoundConstraint() {
    doTest(true)
  }

  void testIntersectionAsParameter() {
    doTest(true)
  }

  void testSuperWildcardAsParameter() {
    doTest(true)
  }

  void testWideSearch() {
    myFixture.addFileToProject 'other.groovy', '''
AA.foo(1)
'''
    doTextTest """
class AA { static def fo<caret>o(a) {} }
""", """
class AA { static void fo<caret>o(Integer a) {} }
"""
  }

  void testElvisOperator() {
    doTest(true)
  }

  void testIndexPropertyWithSpecialSyntax() {
    doTest(true)
  }

  void testWeirdClassName() {
    doTest(true)
  }

  void testInferCommonClassType() {
    doTest(true)
  }

  void testUnresolvedCode() {
    doTest(true)
  }

  void testUnresolvedCode2() {
    doTest(true)
  }

  void testCallWithInnerTypeParameter() {
    doTest(true)
  }

  void testUnresolvedCode3() {
    doTest(true)
  }

  void testPassClosureInClosure() {
    doTest(true)
  }

  void testRequiredBoxingForPrimitiveType() {
    doTest(true)
  }

  void testOffsetSensitiveMethodCreating() {
    doTest(true)
  }

  void testAvoidInfiniteLoopForPlusAssignment() {
    doTest(true)
  }

  void testOverriddenVarargParameter() {
    doTest(true)
  }

  void testForInLoop() {
    doTest(true)
  }

  void testForInLoopWithMap() {
    doTest(true)
  }

  void testRecursiveCalls() {
    doTest(true)
  }
}
