// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiFile;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.intentions.style.inference.MethodParameterAugmenter;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author Maxim.Medvedev
 */
public class FileGenerationTest extends LightGroovyTestCase {
  private void doTest() {
    final String testName = getTestName(true);
    final PsiFile file = myFixture.configureByFile(testName + ".groovy");
    assertInstanceOf(file, GroovyFile.class);

    new ConvertToJavaProcessor(getProject(), (GroovyFile)file).run();

    myFixture.checkResultByFile(testName + ".java");
  }

  public void testForLoops() { doTest(); }

  public void testLiterals() { doTest(); }

  public void testIncrementAndDecrement() { doTest(); }

  public void testPlusPlus() { doTest(); }

  public void testEnum() { doTest(); }

  public void testGrScript() { doTest(); }

  public void testConstructor() { doTest(); }

  public void testReturns() { doTest(); }

  public void testReturn2() { doTest(); }

  public void testGenericTypes() { doTest(); }

  public void testStaticProperty() { doTest(); }

  public void testResolveMethodInsideClosure() { doTest(); }

  public void testAnno() { doTest(); }

  public void testAnno1() { doTest(); }

  public void testUseAnno() { doTest(); }

  public void testRemoveTransformAnno() { doTest(); }

  public void testConcurency() {
    boolean registryValue = Registry.is(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE);
    Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).setValue(true);
    try {
      doTest();
    }
    finally {
      Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).setValue(registryValue);
    }
  }

  public void testHash() { doTest(); }

  public void testAnonymous() { doTest(); }

  public void testAnonymous2() { doTest(); }

  public void testRefInAnonymous() { doTest(); }

  public void testRefInClosureInMethod() { doTest(); }

  public void testRefInClosureInScript() { doTest(); }

  public void testMethodParamsInClosures() { doTest(); }

  public void testClosureParamInInnerClosure() { doTest(); }

  public void testMethodParamInClosureImplicitReturn() { doTest(); }

  public void testField() { doTest(); }

  public void testIntPropAssignment() { doTest(); }

  public void testPropAssignment() { doTest(); }

  public void testStaticPropAssignment() { doTest(); }

  public void testArrayAccess() { doTest(); }

  public void testClosureInUse() { doTest(); }

  public void testDynamicPropertiesAccess() { doTest(); }

  public void testDynamicMethodsAccess() { doTest(); }

  public void _testArg() { doTest(); }

  public void testCasts() { doTest(); }

  public void testReferenceExpressionsToClass() {
    myFixture.addClass("""
                         package foo;
                         
                         public enum A {
                           Const
                         }
                         """);
    doTest();
  }

  public void testTupleInReturn() {doTest(); }

  public void testStaticMethods() { doTest(); }

  public void testImplementGroovyObject() { doTest(); }

  public void testFinalMethodParameterUsedInAnonymous() { doTest(); }

  public void testMethodWithUntypedParameterInitializedWithNull() { doTest(); }

  public void testGroovyDoc() { doTest(); }

  public void testCopyright() { doTest(); }

  public void testReflectedMethodWithEllipsis() { doTest(); }

  public void testMapAccess2() { doTest(); }

  public void testMutualCasts() { doTest(); }

  public void testNonJavaIdentifiers() { doTest(); }

  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "refactoring/convertGroovyToJava/file";
  }
}
