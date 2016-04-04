/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.refactoring.convertToJava

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Maxim.Medvedev
 */
class FileGenerationTest extends LightGroovyTestCase {
  final String basePath = TestUtils.testDataPath + 'refactoring/convertGroovyToJava/file'

  private void doTest() {
    final String testName = getTestName(true)
    final PsiFile file = myFixture.configureByFile("${testName}.groovy");
    assertInstanceOf file, GroovyFile

    new ConvertToJavaProcessor(project, file).run()

    myFixture.checkResultByFile("${testName}.java")
  }

  void testEnum() { doTest() }

  void testGrScript() { doTest() }

  void testConstructor() { doTest() }

  void testReturns() { doTest() }

  void testReturn2() { doTest() }

  void testGenericTypes() { doTest() }

  void testStaticProperty() { doTest() }

  void testResolveMethodInsideClosure() { doTest() }

  void testAnno() { doTest() }

  void testAnno1() { doTest() }

  void testUseAnno() { doTest() }

  void testConcurency() { doTest() }

  void testHash() { doTest() }

  void testAnonymous() { doTest() }

  void testAnonymous2() { doTest() }

  void testRefInAnonymous() { doTest() }

  void testRefInClosureInMethod() { doTest() }

  void testRefInClosureInScript() { doTest() }

  void testMethodParamsInClosures() { doTest() }

  void testClosureParamInInnerClosure() { doTest() }

  void testMethodParamInClosureImplicitReturn() { doTest() }

  void testField() { doTest() }

  void testIntPropAssignment() { doTest() }

  void testPropAssignment() { doTest() }

  void testStaticPropAssignment() { doTest() }

  void testArrayAccess() { doTest() }

  void testClosureInUse() { doTest() }

  void testDynamicPropertiesAccess() { doTest() }

  void testDynamicMethodsAccess() { doTest() }

  void _testArg() { doTest() }

  void testCasts() { doTest() }

  void testReferenceExpressionsToClass() {
    myFixture.addClass('''\
package foo;

public enum A {
  Const
}''')
    doTest()
  }

  void testTupleInReturn() { doTest() }

  void testStaticMethods() { doTest() }

  void testImplementGroovyObject() { doTest() }

  void testFinalMethodParameterUsedInAnonymous() { doTest() }

  void testMethodWithUntypedParameterInitializedWithNull() { doTest() }

  void testGroovyDoc() { doTest() }

  void testReflectedMethodWithEllipsis() { doTest() }

  void testMapAccess2() { doTest() }

  void testMutualCasts() { doTest() }
}
