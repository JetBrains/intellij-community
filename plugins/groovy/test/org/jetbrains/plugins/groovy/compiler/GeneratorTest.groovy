/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.compiler

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.refactoring.convertToJava.GroovyToJavaGenerator
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Dmitry.Krasilschikov
 * @since 06.06.2007
 */
class GeneratorTest extends LightGroovyTestCase {
  final String basePath = TestUtils.testDataPath + "groovy/stubGenerator"

  public void testArrayType1() throws Throwable { doTest(); }
  public void testAtInterface() throws Throwable { doTest(); }
  public void testDefInInterface() throws Throwable { doTest(); }
  public void testExtends1() throws Throwable { doTest(); }
  public void testExtendsImplements() throws Throwable { doTest(); }
  public void testGetterAlreadyDefined() throws Throwable { doTest(); }
  public void testScriptWithMain() {doTest();}
  public void testGrvy1098() throws Throwable { doTest(); }
  public void testGrvy118() throws Throwable { doTest(); }
  public void testGrvy1358() throws Throwable { doTest(); }
  public void testGrvy1376() throws Throwable { doTest(); }
  public void testGrvy170() throws Throwable { doTest(); }
  public void testGrvy903() throws Throwable { doTest(); }
  public void testGrvy908() throws Throwable { doTest(); }
  public void testGRVY915() throws Throwable { doTest(); }
  public void testImplements1() throws Throwable { doTest(); }
  public void testKireyev() throws Throwable { doTest(); }
  public void testMethodTypeParameters() throws Throwable { doTest(); }
  public void testOptionalParameter() throws Throwable { doTest(); }
  public void testOverrideFinalGetter() throws Throwable { doTest(); }
  public void testPackage1() throws Throwable { doTest(); }
  public void testScript() throws Throwable { doTest(); }
  public void testSetterAlreadyDefined1() throws Throwable { doTest(); }
  public void testSetUpper1() throws Throwable { doTest(); }
  public void testSingletonConstructor() throws Throwable { doTest(); }
  public void testStringMethodName() throws Throwable { doTest(); }
  public void testSuperInvocation() throws Throwable { doTest(); }
  public void testSuperInvocation1() throws Throwable { doTest(); }
  public void testToGenerate() throws Throwable { doTest(); }
  public void testToGenerate1() throws Throwable { doTest(); }
  public void testVararg1() throws Throwable { doTest(); }
  public void testInaccessibleConstructor() throws Throwable { doTest(); }
  public void testSynchronizedProperty() throws Throwable { doTest(); }
  public void testVarargs() throws Throwable { doTest(); }
  public void testThrowsCheckedException() throws Throwable { doTest(); }
  public void testSubclassProperty() throws Throwable { doTest(); }
  public void testFinalProperty() throws Throwable { doTest(); }
  public void testDefaultConstructorArguments() throws Throwable { doTest(); }
  public void testDelegateWithStaticMethod() throws Throwable { doTest(); }

  public void testParameterReturnType() throws Throwable {
    myFixture.addClass("public interface GwtActionService {\n" +
                       "    <T extends CharSequence> T execute(java.util.List<T> action);\n" +
                       "}");
    doTest();
  }

  public void testRawReturnTypeInImplementation() throws Throwable { doTest(); }

  public void testDelegationGenerics() throws Throwable {
    myFixture.addClass("package groovy.lang; public @interface Delegate { boolean interfaces() default true; }");
    doTest();
  }

  public void testCheckedExceptionInConstructorDelegate() throws Throwable {
    myFixture.addClass("package foo;" +
                       "public class SuperClass {" +
                       "  public SuperClass(String s) throws java.io.IOException {}" +
                       "}");
    doTest();
  }

  public void testInaccessiblePropertyType() throws Throwable {
    myFixture.addClass("package foo; class Hidden {}");
    doTest();
  }

  public void testImmutableAnno() throws Throwable {
    myFixture.addClass("package groovy.lang; public @interface Immutable {}");
    doTest();
  }

  public void testTupleConstructorAnno() throws Throwable {
    myFixture.addClass("package groovy.transform; public @interface TupleConstructor {}");
    doTest();
  }

  public void testDelegateAnno() throws Throwable {
    myFixture.addClass("package groovy.lang; public @interface Delegate {}");
    doTest();
  }
  public void testAutoCloneAnno() throws Throwable {
    myFixture.addClass("package groovy.transform; public @interface AutoClone {}");
    doTest();
  }
  
  public void testDelegateToMethodWithTypeParams() {
    doTest();
  }
  
  public void testMethodsWithTypeParamsAndOptionalParams() {
    doTest();
  }
  
  public void testMethodWithItsOwnTypeParams(){
    doTest();
  }

  public void testErasedOverloadedMethodInDelegate() {
    myFixture.addClass("package groovy.lang; public @interface Delegate {}");
    doTest();
  }

  public void testFinalMethods() {
    doTest();
  }

  public void testInheritConstructors() {
    myFixture.addClass('package groovy.transform; public @interface InheritConstructors{}')
    myFixture.addClass('public class Base {public Base(String s){}}')
    doTest()
  }

  public void doTest() {
    final String relTestPath = getTestName(true) + ".test";
    final List<String> data = TestUtils.readInput("$testDataPath/$relTestPath");

    final String testName = StringUtil.trimEnd(relTestPath, ".test");
    PsiFile psiFile = TestUtils.createPseudoPhysicalFile(project, testName + ".groovy", data.get(0));
    final StringBuilder builder = GroovyToJavaGenerator.generateStubs(psiFile);

    assertEquals(data.get(1).trim(), builder.toString().trim());
  }
}
