// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyFixtures;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.util.HighlightingTest;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixtureKt.grHighlightingFixture;

@TestApplication
public class GroovyLambdaHighlightingTest implements HighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_3_0);
  private final TestFixture<GrHighlightingFixture> highlighting =
    grHighlightingFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "highlighting/lambda/", () -> new InspectionProfileEntry[]{new GrUnresolvedAccessInspection(), new GroovyAssignabilityCheckInspection()});

  @Override
  public @NotNull LightJavaCodeInsightTestFixture getFixture() {
    return highlighting.get().getFixture();
  }

  @Override
  public String getTestName() {
    return LightGroovyFixtures.groovyTestName(highlighting.get().getFixture());
  }

  @Test
  public void testLambdaAssignment() {
    highlightingTest("""
                       import groovy.transform.CompileStatic

                       @CompileStatic
                       def a() {
                           Closure cl = () -> {}
                       }
                       """);
  }

  @Test
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

  @Test
  public void testIncorrectSingleArgument() {
    highlightingTest("""
                       interface Consumer<T> {
                         void accept(T t);
                       }
                       Consumer<String> c =<error descr="<expression> expected, got 'final'"> </error>final x <error descr="'(' or ',' expected, got '->'">-></error> println x
                       """);
  }

  @Test
  public void testLambdaApplicability() { highlighting.get().doTest(); }

  @Test
  public void testCastLambdaToInterface() { highlighting.get().doTest(); }

  public void _testCastInsideLambda() { highlighting.get().doTest(); }

  @Test
  public void testAssignabilityOfCategoryMethod() { highlighting.get().doTest(); }

  @Test
  public void testPathCallIsNotApplicable() { highlighting.get().doTest(); }

  @Test
  public void testCallIsNotApplicable() { highlighting.get().doTest(); }

  @Test
  public void testCategoryWithPrimitiveType() { highlighting.get().doTest(); }

  @Test
  public void testCollectionAssignments() { highlighting.get().doTest(); }

  @Test
  public void testCurrying() { highlighting.get().doTest(); }

  @Test
  public void testAnotherCurrying() { highlighting.get().doTest(); }

  @Test
  public void testLambdaWithDefaultParameters() { highlighting.get().doTest(); }

  @Test
  public void testFromStringWithGenericPlaceholderFromClass() { highlighting.get().doTest(); }

  @Test
  public void testFromStringWithSimpleType() { highlighting.get().doTest(); }

  @Test
  public void testFromStringWithGeneric() { highlighting.get().doTest(); }

  @Test
  public void testFromStringWithGenericPlaceholderFromClassWithTwoGenericsAndPolymorphicSignature() { highlighting.get().doTest(); }

  @Test
  public void testFromStringWithDirectGenericPlaceholder() { highlighting.get().doTest(); }

  @Test
  public void testInferenceOnNonExtensionMethod() { highlighting.get().doTest(); }

  @Test
  public void testFromStringWithGenericPlaceholderFromClassWithTwoGenericsAndNoExplicitSignature() { highlighting.get().doTest(); }

  @Test
  public void testFromStringWithGenericPlaceholderFromClassWithTwoGenerics() { highlighting.get().doTest(); }

  @Test
  public void testFromStringWithGenericPlaceholderFromClassWithTwoGenericsAndNoExplicitSignatureAndNoFQN() { highlighting.get().doTest(); }

  @Test
  public void testFromStringWithGenericPlaceholder() { highlighting.get().doTest(); }

  @Test
  public void testFromStringWithGenericPlaceholderFromClassWithTwoGenericsAndNoExplicitSignatureAndNoFQNAndReferenceToSameUnitClass() { highlighting.get().doTest(); }

  @Test
  public void testFromStringWithGenericPlaceholderFromClassWithTwoGenericsAndNoExplicitSignatureAndNoFQNAndReferenceToSameUnitClassAndTwoArgs() {
    highlighting.get().doTest();
  }

  @Test
  public void testCastArgumentError() { highlighting.get().doTest(); }

  @Test
  public void testCastArgument() { highlighting.get().doTest(); }

  @Test
  public void testCastSeveralArgumentError() { highlighting.get().doTest(); }

  @Test
  public void testSeveralSignatures() { highlighting.get().doTest(); }

  @Test
  public void testFromStringWithNonFqnOptionsNoError() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    LightGroovyFixtures.addBigInteger(myFixture);
    myFixture.addClass("""
                         import groovy.lang.Closure;
                         import groovy.transform.stc.ClosureParams;
                         import groovy.transform.stc.FromString;

                         public class A {
                           public static void foo(@ClosureParams(value = FromString.class, options = "BigInteger") Closure c) {}
                         }
                         """);
    highlighting.get().doTest();
  }

  @Test
  public void testFromStringResolveDefaultPackageGeneric() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
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
    highlighting.get().doTest();
  }
}