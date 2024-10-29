// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.intentions.style.inference.MethodParameterAugmenter;
import org.jetbrains.plugins.groovy.util.TestUtils;

public class InferMethodParametersTypesIntentionTest extends GrIntentionTestCase {
  public InferMethodParametersTypesIntentionTest() {
    super("Add explicit types to parameters");
  }


  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST_REAL_JDK;
  }

  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "refactoring/inferMethodParametersTypes";
  }

  @Override
  public void setUp() throws Exception {
    Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).setValue(true);
    super.setUp();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).resetToDefault();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testStringInference() {
    doTest(true);
  }

  public void testSeveralArguments() {
    doTest(true);
  }

  public void testNoInferenceInMethodBody() {
    doTest(false);
  }

  public void testNoInferenceWithoutNontypedArguments() {
    doTest(false);
  }

  public void testNoResolveForTypeParameters() {
    doTest(true);
  }

  public void testLeaveUnusedTypeParameter() {
    doTest(true);
  }

  public void testCustomInheritance() {
    doTest(true);
  }

  public void testResolvedMethodCallInside() {
    doTest(true);
  }

  public void testDependencyOnTypeParameter() {
    doTest(true);
  }

  public void testOperatorInference() {
    doTest(true);
  }

  public void testSearchEverywhere() {
    doTest(true);
  }

  public void testUpperBoundByTypeParameter() {
    doTest(true);
  }

  public void testInferWildcardsDependencies() {
    doTest(true);
  }

  public void testRelatedWildcards() {
    doTest(true);
  }

  public void testDeepEqualityOfWildcards() {
    doTest(true);
  }

  public void testDeepDependencyOfWildcards() {
    doTest(true);
  }

  public void testDeepDependencyOfWildcards2() {
    doTest(true);
  }

  public void testDeepDependencyOfWildcards3() {
    doTest(true);
  }

  public void testAppearedWildcard() {
    doTest(true);
  }

  public void testAppearedWildcard2() {
    doTest(true);
  }

  public void testConstructor() {
    doTest(true);
  }

  public void testConstructor2() {
    doTest(true);
  }

  public void testConstructor3() {
    doTest(true);
  }

  public void testConstructor4() {
    doTest(true);
  }

  public void testRedundantTypeParameter() {
    doTest(true);
  }

  public void testTwoTypeParameters() {
    doTest(true);
  }

  public void testTwoSupertypes() {
    doTest(true);
  }

  public void testTwoSupertypes2() {
    doTest(true);
  }

  public void testTwoSupertypes3() {
    doTest(true);
  }

  public void testTwoSupertypes4() {
    doTest(true);
  }

  public void testTwoSupertypes5() {
    doTest(true);
  }

  public void testThreeSupertypes() {
    doTest(true);
  }

  public void testThreeSupertypes2() {
    doTest(true);
  }

  public void testCustomSetter() {
    doTest(true);
  }

  public void testCustomSetter2() {
    doTest(true);
  }

  public void testMultipleInterfaces() {
    doTest(true);
  }

  public void testMultipleInterfaces2() {
    doTest(true);
  }

  public void testRaw() {
    doTest(true);
  }

  public void testDefaultValues() {
    doTest(true);
  }

  public void testDefaultValues2() {
    doTest(true);
  }

  public void testDiamond() {
    doTest(true);
  }

  public void testParametrizedInterface() {
    doTest(true);
  }

  public void testParametrizedInterface2() {
    doTest(true);
  }

  public void testParametrizedInterface3() {
    doTest(true);
  }

  public void testIntersectionInArgument() {
    doTest(true);
  }

  public void testParametrizedMethod() {
    doTest(true);
  }

  public void testBasicClosure() {
    doTest(true);
  }

  public void testImplicitClosureParameter() {
    doTest(true);
  }

  public void testInterfaceInClosureSignature() {
    doTest(true);
  }

  public void testClosureParameterDependsOnMethodParameter() {
    doTest(true);
  }

  public void testClosureParameterDependsOnMethodParameter2() {
    doTest(true);
  }

  public void testTwoClosuresAsArgument() {
    doTest(true);
  }

  public void testDeepClosureDependency() {
    doTest(true);
  }

  public void testDeepClosureDependency2() {
    doTest(true);
  }

  public void testDeepClosureDependency3() {
    doTest(true);
  }

  public void testDeepClosureDependency4() {
    doTest(true);
  }

  public void testClosureSignatureFromInnerCalls() {
    doTest(true);
  }

  public void testArrayOfPrimitiveTypes() {
    doTest(true);
  }

  public void testParametrizedArray() {
    doTest(true);
  }

  public void testCallWithArrayParameter() {
    doTest(true);
  }

  public void testDifferentCallPlaces() {
    doTest(true);
  }

  public void testAuxiliaryStatements() {
    doTest(true);
  }

  public void testExplicitCallForClosureParameter() {
    doTest(true);
  }

  public void testSentClosure() {
    doTest(true);
  }

  public void testVariance() {
    doTest(true);
  }

  public void testVariance2() {
    doTest(true);
  }

  public void testVariance3() {
    doTest(true);
  }

  public void testMutualDependency() {
    doTest(true);
  }

  public void testMutualDependency2() {
    doTest(true);
  }

  public void testOverriddenMethod() {
    doTest(true);
  }

  public void testOverriddenMethodInClass() {
    doTest(true);
  }

  public void testOverriddenMethodWithoutOverrideAnnotation() {
    doTest(true);
  }

  public void testOverriddenMethodWithOtherOverloads() {
    doTest(true);
  }

  public void testOverriddenMethodWithErasure() {
    doTest(true);
  }

  public void testOverriddenMethodWithoutType() {
    doTest(true);
  }

  public void testAssignment() {
    doTest(true);
  }

  public void testAssignment2() {
    doTest(true);
  }

  public void testAssignment3() {
    doTest(true);
  }

  public void testVarargAcceptance() {
    doTest(true);
  }

  public void testVarargInference() {
    doTest(true);
  }

  public void testGenericConstructor() {
    doTest(true);
  }

  public void testClosureParamsInfluence() {
    doTest(true);
  }

  public void testClosureParamsInfluence2() {
    doTest(true);
  }

  public void testClosurePassedToDgm() {
    doTest(true);
  }

  public void testPreserveAnnotations() {
    doTest(true);
  }

  public void testInferClassParameter() {
    doTest(true);
  }

  public void testInferClassParameter2() {
    doTest(true);
  }

  public void testInferClassParameter3() {
    doTest(true);
  }

  public void testInferClassParameter4() {
    doTest(true);
  }

  public void testInferClassParameter5() {
    doTest(true);
  }

  public void testInferClassParameter6() {
    doTest(true);
  }

  public void testInferClassParameter7() {
    doTest(true);
  }

  public void testInferClassParameter8() {
    doTest(true);
  }

  public void testParameterDependencyInsideClosure() {
    doTest(true);
  }

  public void testSuperCallInConstructor() {
    doTest(true);
  }

  public void testParameterizedMethod2() {
    doTest(true);
  }

  public void testClosureAndSamInterface() {
    doTest(true);
  }

  public void testSuperWildcard() {
    doTest(true);
  }

  public void testCapturedWildcard() {
    doTest(true);
  }

  public void testCapturedWildcard2() {
    doTest(true);
  }

  public void testDelegatingClosure() {
    doTest(true);
  }

  public void testDelegateViaRehydrate() {
    doTest(true);
  }

  public void testDelegateToParameter() {
    doTest(true);
  }

  public void testDelegatesToNamedTarget() {
    doTest(true);
  }

  public void testDelegatesToWithStrategy() {
    doTest(true);
  }

  public void testDelegateInferenceFromInnerMethod() {
    doTest(true);
  }

  public void testDelegationFromDgm() {
    doTest(true);
  }

  public void testDelegationFromType() {
    doTest(true);
  }

  public void testReturnTypeInfluence() {
    doTest(true);
  }

  public void testDgm() {
    doTest(true);
  }

  public void testIndirectBoundConstraint() {
    doTest(true);
  }

  public void testIntersectionAsParameter() {
    doTest(true);
  }

  public void testSuperWildcardAsParameter() {
    doTest(true);
  }

  public void testWideSearch() {
    myFixture.addFileToProject("other.groovy", """
      AA.foo(1)
      """);
    doTextTest("""
                 class AA { static def fo<caret>o(a) {} }
                 """, """
                 class AA { static void fo<caret>o(Integer a) {} }
                 """);
  }

  public void testElvisOperator() {
    doTest(true);
  }

  public void testIndexPropertyWithSpecialSyntax() {
    doTest(true);
  }

  public void testWeirdClassName() {
    doTest(true);
  }

  public void testInferCommonClassType() {
    doTest(true);
  }

  public void testUnresolvedCode() {
    doTest(true);
  }

  public void testUnresolvedCode2() {
    doTest(true);
  }

  public void testCallWithInnerTypeParameter() {
    doTest(true);
  }

  public void testUnresolvedCode3() {
    doTest(true);
  }

  public void testPassClosureInClosure() {
    doTest(true);
  }

  public void testRequiredBoxingForPrimitiveType() {
    doTest(true);
  }

  public void testOffsetSensitiveMethodCreating() {
    doTest(true);
  }

  public void testAvoidInfiniteLoopForPlusAssignment() {
    doTest(true);
  }

  public void testOverriddenVarargParameter() {
    doTest(true);
  }

  public void testForInLoop() {
    doTest(true);
  }

  public void testForInLoopWithMap() {
    doTest(true);
  }

  public void testRecursiveCalls() {
    doTest(true);
  }
}
