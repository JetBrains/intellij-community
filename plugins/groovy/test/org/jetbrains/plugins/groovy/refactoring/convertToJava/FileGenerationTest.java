// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.intentions.style.inference.MethodParameterAugmenter;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * @author Maxim.Medvedev
 */
@TestApplication
public class FileGenerationTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "refactoring/convertGroovyToJava/file");


  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String testName = myFixture.getTestName(true);
    final PsiFile file = myFixture.configureByFile(testName + ".groovy");
    EdtTestUtil.runInEdtAndWait(() -> {
      assertInstanceOf(GroovyFile.class, file);

      new ConvertToJavaProcessor(myFixture.getProject(), (GroovyFile)file).run();

      myFixture.checkResultByFile(testName + ".java");
    });
  }

  @Test
  public void testForLoops() { doTest(); }

  @Test
  public void testLiterals() { doTest(); }

  @Test
  public void testIncrementAndDecrement() { doTest(); }

  @Test
  public void testPlusPlus() { doTest(); }

  @Test
  public void testEnum() { doTest(); }

  @Test
  public void testGrScript() { doTest(); }

  @Test
  public void testConstructor() { doTest(); }

  @Test
  public void testReturns() { doTest(); }

  @Test
  public void testReturn2() { doTest(); }

  @Test
  public void testGenericTypes() { doTest(); }

  @Test
  public void testStaticProperty() { doTest(); }

  @Test
  public void testResolveMethodInsideClosure() { doTest(); }

  @Test
  public void testAnno() { doTest(); }

  @Test
  public void testAnno1() { doTest(); }

  @Test
  public void testUseAnno() { doTest(); }

  @Test
  public void testRemoveTransformAnno() { doTest(); }

  @Test
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

  @Test
  public void testHash() { doTest(); }

  @Test
  public void testAnonymous() { doTest(); }

  @Test
  public void testAnonymous2() { doTest(); }

  @Test
  public void testRefInAnonymous() { doTest(); }

  @Test
  public void testRefInClosureInMethod() { doTest(); }

  @Test
  public void testRefInClosureInScript() { doTest(); }

  @Test
  public void testMethodParamsInClosures() { doTest(); }

  @Test
  public void testClosureParamInInnerClosure() { doTest(); }

  @Test
  public void testMethodParamInClosureImplicitReturn() { doTest(); }

  @Test
  public void testField() { doTest(); }

  @Test
  public void testIntPropAssignment() { doTest(); }

  @Test
  public void testPropAssignment() { doTest(); }

  @Test
  public void testStaticPropAssignment() { doTest(); }

  @Test
  public void testArrayAccess() { doTest(); }

  @Test
  public void testClosureInUse() { doTest(); }

  @Test
  public void testDynamicPropertiesAccess() { doTest(); }

  @Test
  public void testDynamicMethodsAccess() { doTest(); }

  public void _testArg() { doTest(); }

  @Test
  public void testCasts() { doTest(); }

  @Test
  public void testReferenceExpressionsToClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package foo;
                         
                         public enum A {
                           Const
                         }
                         """);
    doTest();
  }

  @Test
  public void testTupleInReturn() {doTest(); }

  @Test
  public void testStaticMethods() { doTest(); }

  @Test
  public void testImplementGroovyObject() { doTest(); }

  @Test
  public void testFinalMethodParameterUsedInAnonymous() { doTest(); }

  @Test
  public void testMethodWithUntypedParameterInitializedWithNull() { doTest(); }

  @Test
  public void testGroovyDoc() { doTest(); }

  @Test
  public void testCopyright() { doTest(); }

  @Test
  public void testReflectedMethodWithEllipsis() { doTest(); }

  @Test
  public void testMapAccess2() { doTest(); }

  @Test
  public void testMutualCasts() { doTest(); }

  @Test
  public void testNonJavaIdentifiers() { doTest(); }
}
