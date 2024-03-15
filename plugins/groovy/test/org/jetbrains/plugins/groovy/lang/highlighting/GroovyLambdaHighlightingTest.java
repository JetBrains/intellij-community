// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.util.HighlightingTest
import org.jetbrains.plugins.groovy.util.TestUtils

@CompileStatic
class GroovyLambdaHighlightingTest extends GrHighlightingTestBase implements HighlightingTest {
  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_3_0
  final String basePath = TestUtils.testDataPath + 'highlighting/lambda/'

  InspectionProfileEntry[] customInspections = [new GrUnresolvedAccessInspection(), new GroovyAssignabilityCheckInspection()]

  void 'test lambda assignment'() {
    highlightingTest '''
import groovy.transform.CompileStatic

@CompileStatic
def a() {
    Closure cl = () -> {}
}
'''
  }

  void 'test lambda argument'() {
    highlightingTest '''
import groovy.transform.CompileStatic
def m(Closure cl) {
}
@CompileStatic
def a() {
   m( () -> {})
}
'''
  }

  void testLambdaApplicability() { doTest() }

  void testCastLambdaToInterface() { doTest() }

  void _testCastInsideLambda() { doTest() }

  void testAssignabilityOfCategoryMethod() { doTest() }

  void testPathCallIsNotApplicable() { doTest() }

  void testCallIsNotApplicable() { doTest() }

  void testCategoryWithPrimitiveType() { doTest() }

  void testCollectionAssignments() { doTest() }

  void testCurrying() { doTest() }

  void testAnotherCurrying() { doTest() }

  void testLambdaWithDefaultParameters() { doTest() }

  void testFromStringWithGenericPlaceholderFromClass() { doTest() }

  void testFromStringWithSimpleType() { doTest() }

  void testFromStringWithGeneric() { doTest() }

  void testFromStringWithGenericPlaceholderFromClassWithTwoGenericsAndPolymorphicSignature() { doTest() }

  void testFromStringWithDirectGenericPlaceholder() { doTest() }

  void testInferenceOnNonExtensionMethod() { doTest() }

  void testFromStringWithGenericPlaceholderFromClassWithTwoGenericsAndNoExplicitSignature() { doTest() }

  void testFromStringWithGenericPlaceholderFromClassWithTwoGenerics() { doTest() }

  void testFromStringWithGenericPlaceholderFromClassWithTwoGenericsAndNoExplicitSignatureAndNoFQN() { doTest() }

  void testFromStringWithGenericPlaceholder() { doTest() }

  void testFromStringWithGenericPlaceholderFromClassWithTwoGenericsAndNoExplicitSignatureAndNoFQNAndReferenceToSameUnitClass() { doTest() }

  void testFromStringWithGenericPlaceholderFromClassWithTwoGenericsAndNoExplicitSignatureAndNoFQNAndReferenceToSameUnitClassAndTwoArgs() {
    doTest()
  }


  //GrClosureParamsTest
  void testCastArgumentError() { doTest() }
  void testCastArgument() { doTest() }
  void testCastSeveralArgumentError() { doTest() }
  void testSeveralSignatures() { doTest() }
  void testFromStringWithNonFqnOptionsNoError() {
    addBigInteger()
    myFixture.addClass '''
import groovy.lang.Closure;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.FromString;

public class A {
  public static void foo(@ClosureParams(value = FromString.class, options = "BigInteger") Closure c) {}
}
'''
    doTest()
  }

  void testFromStringResolveDefaultPackageGeneric() {
    myFixture.addClass '''
package com.foo.baz;

import groovy.lang.Closure;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.FromString;

public class A<T> {
    public void bar(@ClosureParams(value = FromString.class, options = "MyClass<T>") Closure c) {}
}
'''
    myFixture.addClass '''
public class MyClass<U> {}
'''
  doTest()
  }
}
