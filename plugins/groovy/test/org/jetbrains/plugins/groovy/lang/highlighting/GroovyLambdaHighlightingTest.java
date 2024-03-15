// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.util.HighlightingTest;
import org.jetbrains.plugins.groovy.util.TestUtils;

public class GroovyLambdaHighlightingTest extends GrHighlightingTestBase implements HighlightingTest {
  public void testLambdaAssignment() {
    highlightingTest("""
                       import groovy.transform.CompileStatic

                       @CompileStatic
                       def a() {
                           Closure cl = () -> {}
                       }
                       """);
  }

  public void testLambdaArgument() {
    highlightingTest("""
                       import groovy.transform.CompileStatic
                       def m(Closure cl) {
                       }
                       @CompileStatic
                       def a() {
                          m( () -> {})
                       }
                       """);
  }

  public void testLambdaApplicability() { doTest(); }

  public void testCastLambdaToInterface() { doTest(); }

  public void _testCastInsideLambda() { doTest(); }

  public void testAssignabilityOfCategoryMethod() { doTest(); }

  public void testPathCallIsNotApplicable() { doTest(); }

  public void testCallIsNotApplicable() { doTest(); }

  public void testCategoryWithPrimitiveType() { doTest(); }

  public void testCollectionAssignments() { doTest(); }

  public void testCurrying() { doTest(); }

  public void testAnotherCurrying() { doTest(); }

  public void testLambdaWithDefaultParameters() { doTest(); }

  public void testFromStringWithGenericPlaceholderFromClass() { doTest(); }

  public void testFromStringWithSimpleType() { doTest(); }

  public void testFromStringWithGeneric() { doTest(); }

  public void testFromStringWithGenericPlaceholderFromClassWithTwoGenericsAndPolymorphicSignature() { doTest(); }

  public void testFromStringWithDirectGenericPlaceholder() { doTest(); }

  public void testInferenceOnNonExtensionMethod() { doTest(); }

  public void testFromStringWithGenericPlaceholderFromClassWithTwoGenericsAndNoExplicitSignature() { doTest(); }

  public void testFromStringWithGenericPlaceholderFromClassWithTwoGenerics() { doTest(); }

  public void testFromStringWithGenericPlaceholderFromClassWithTwoGenericsAndNoExplicitSignatureAndNoFQN() { doTest(); }

  public void testFromStringWithGenericPlaceholder() { doTest(); }

  public void testFromStringWithGenericPlaceholderFromClassWithTwoGenericsAndNoExplicitSignatureAndNoFQNAndReferenceToSameUnitClass() { doTest(); }

  public void testFromStringWithGenericPlaceholderFromClassWithTwoGenericsAndNoExplicitSignatureAndNoFQNAndReferenceToSameUnitClassAndTwoArgs() {
    doTest();
  }

  public void testCastArgumentError() { doTest(); }

  public void testCastArgument() { doTest(); }

  public void testCastSeveralArgumentError() { doTest(); }

  public void testSeveralSignatures() { doTest(); }

  public void testFromStringWithNonFqnOptionsNoError() {
    addBigInteger();
    myFixture.addClass("""
                         import groovy.lang.Closure;
                         import groovy.transform.stc.ClosureParams;
                         import groovy.transform.stc.FromString;

                         public class A {
                           public static void foo(@ClosureParams(value = FromString.class, options = "BigInteger") Closure c) {}
                         }
                         """);
    doTest();
  }

  public void testFromStringResolveDefaultPackageGeneric() {
    myFixture.addClass("""
                         package com.foo.baz;

                         import groovy.lang.Closure;
                         import groovy.transform.stc.ClosureParams;
                         import groovy.transform.stc.FromString;
                         
                         public class A<T> {
                             public void bar(@ClosureParams(value = FromString.class, options = "MyClass<T>") Closure c) {}
                         }
                         """);
    myFixture.addClass("""
                         public class MyClass<U> {}
                         """);
    doTest();
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_3_0;
  }

  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "highlighting/lambda/";
  }

  @Override
  public InspectionProfileEntry[] getCustomInspections() {
    return new InspectionProfileEntry[]{new GrUnresolvedAccessInspection(), new GroovyAssignabilityCheckInspection()};
  }
}