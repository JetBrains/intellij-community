// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.intentions.style.inference.MethodParameterAugmenter;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.intentions.GrIntentionFixtureKt.grIntentionFixture;

@TestApplication
public class InferMethodParametersTypesIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST_REAL_JDK);
  private final TestFixture<GrIntentionFixture> grIntention =
    grIntentionFixture(projectFixture, "Add explicit types to parameters", TestUtils.getAbsoluteTestDataPath() + "refactoring/inferMethodParametersTypes");

  @BeforeEach
  void setUp() {
    Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).setValue(true);
  }

  @AfterEach
  void tearDown() {
    Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).resetToDefault();
  }

  @Test
  public void testStringInference() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testSeveralArguments() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testNoInferenceInMethodBody() {
    grIntention.get().doTest(false);
  }

  @Test
  public void testNoInferenceWithoutNontypedArguments() {
    grIntention.get().doTest(false);
  }

  @Test
  public void testNoResolveForTypeParameters() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testLeaveUnusedTypeParameter() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testCustomInheritance() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testResolvedMethodCallInside() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testDependencyOnTypeParameter() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testOperatorInference() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testSearchEverywhere() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testUpperBoundByTypeParameter() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testInferWildcardsDependencies() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testRelatedWildcards() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testDeepEqualityOfWildcards() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testDeepDependencyOfWildcards() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testDeepDependencyOfWildcards2() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testDeepDependencyOfWildcards3() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testAppearedWildcard() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testAppearedWildcard2() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testConstructor() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testConstructor2() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testConstructor3() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testConstructor4() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testRedundantTypeParameter() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testTwoTypeParameters() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testTwoSupertypes() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testTwoSupertypes2() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testTwoSupertypes3() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testTwoSupertypes4() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testTwoSupertypes5() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testThreeSupertypes() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testThreeSupertypes2() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testCustomSetter() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testCustomSetter2() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testMultipleInterfaces() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testMultipleInterfaces2() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testRaw() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testDefaultValues() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testDefaultValues2() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testDiamond() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testParametrizedInterface() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testParametrizedInterface2() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testParametrizedInterface3() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testIntersectionInArgument() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testParametrizedMethod() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testBasicClosure() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testImplicitClosureParameter() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testInterfaceInClosureSignature() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testClosureParameterDependsOnMethodParameter() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testClosureParameterDependsOnMethodParameter2() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testTwoClosuresAsArgument() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testDeepClosureDependency() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testDeepClosureDependency2() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testDeepClosureDependency3() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testDeepClosureDependency4() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testClosureSignatureFromInnerCalls() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testArrayOfPrimitiveTypes() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testParametrizedArray() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testCallWithArrayParameter() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testDifferentCallPlaces() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testAuxiliaryStatements() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testExplicitCallForClosureParameter() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testSentClosure() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testVariance() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testVariance2() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testVariance3() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testMutualDependency() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testMutualDependency2() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testOverriddenMethod() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testOverriddenMethodInClass() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testOverriddenMethodWithoutOverrideAnnotation() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testOverriddenMethodWithOtherOverloads() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testOverriddenMethodWithErasure() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testOverriddenMethodWithoutType() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testAssignment() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testAssignment2() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testAssignment3() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testVarargAcceptance() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testVarargInference() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testGenericConstructor() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testClosureParamsInfluence() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testClosureParamsInfluence2() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testClosurePassedToDgm() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testPreserveAnnotations() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testInferClassParameter() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testInferClassParameter2() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testInferClassParameter3() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testInferClassParameter4() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testInferClassParameter5() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testInferClassParameter6() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testInferClassParameter7() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testInferClassParameter8() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testParameterDependencyInsideClosure() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testSuperCallInConstructor() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testParameterizedMethod2() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testClosureAndSamInterface() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testSuperWildcard() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testCapturedWildcard() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testCapturedWildcard2() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testDelegatingClosure() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testDelegateViaRehydrate() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testDelegateToParameter() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testDelegatesToNamedTarget() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testDelegatesToWithStrategy() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testDelegateInferenceFromInnerMethod() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testDelegationFromDgm() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testDelegationFromType() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testReturnTypeInfluence() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testDgm() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testIndirectBoundConstraint() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testIntersectionAsParameter() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testSuperWildcardAsParameter() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testWideSearch() {
    LightJavaCodeInsightTestFixture myFixture = grIntention.get().getFixture();
    myFixture.addFileToProject("other.groovy", """
      AA.foo(1)
      """);
    grIntention.get().doTextTest("""
                 class AA { static def fo<caret>o(a) {} }
                 """, """
                 class AA { static void fo<caret>o(Integer a) {} }
                 """);
  }

  @Test
  public void testElvisOperator() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testIndexPropertyWithSpecialSyntax() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testWeirdClassName() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testInferCommonClassType() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testUnresolvedCode() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testUnresolvedCode2() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testCallWithInnerTypeParameter() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testUnresolvedCode3() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testPassClosureInClosure() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testRequiredBoxingForPrimitiveType() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testOffsetSensitiveMethodCreating() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testAvoidInfiniteLoopForPlusAssignment() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testOverriddenVarargParameter() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testForInLoop() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testForInLoopWithMap() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testRecursiveCalls() {
    grIntention.get().doTest(true);
  }
}
