/*
 *  Copyright 2000-2007 JetBrains s.r.o.
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author ven
 */
public class ResolveClassTest extends GroovyResolveTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "resolve/class/";
  }

  public void testInnerJavaClass() throws Exception {
    doTest("innerJavaClass/B.groovy");
  }

  public void testSamePackage() throws Exception {
    doTest("samePackage/B.groovy");
  }

  public void testImplicitImport() throws Exception {
    doTest("implicitImport/B.groovy");
  }

  public void testOnDemandImport() throws Exception {
    doTest("onDemandImport/B.groovy");
  }

  public void testSimpleImport() throws Exception {
    doTest("simpleImport/B.groovy");
  }

  public void testQualifiedName() throws Exception {
    doTest("qualifiedName/B.groovy");
  }

  public void testImportAlias() throws Exception {
    doTest("importAlias/B.groovy");
  }

  public void testQualifiedRefExpr() throws Exception {
    doTest("qualifiedRefExpr/A.groovy");
  }

  public void testGrvy102() throws Exception {
    doTest("grvy102/Test.groovy");
  }

  public void testClassVsProperty() throws Exception {
    doTest("classVsProperty/Test.groovy");
  }

  public void testGrvy901() throws Exception {
    doTest("grvy901/Test.groovy");
  }

  public void testGrvy641() throws Exception {
    PsiReference ref = configureByFile("grvy641/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiClass);
    assertEquals("List", ((PsiClass) resolved).getQualifiedName());
  }

  public void testGrvy1139() throws Exception {
    PsiReference ref = configureByFile("grvy1139/p/User.groovy");
    assertNull(ref.resolve());
  }

  public void testGrvy1420() throws Exception {
    PsiReference ref = configureByFile("grvy1420/Test.groovy");
    assertNull(ref.resolve());
  }

  public void testGrvy1420_1() throws Exception {
    PsiReference ref = configureByFile("grvy1420_1/Test.groovy");
    assertNull(ref.resolve());
  }

  public void testGrvy1461() throws Exception {
    PsiReference ref = configureByFile("grvy1461/AssertionUtils.groovy");
    assertNotNull(ref.resolve());
  }

  public void _testImportStaticFromJavaUtil() throws Throwable { doTest(); }
  public void testInnerEnum() throws Throwable { doTest(); }
  public void testInnerClass()throws Throwable {doTest();}
  public void testInnerClassInSubclass()throws Throwable {doTest();}
  public void testInnerClassUsageInsideOuterSubclass() throws Throwable{doTest();}

  public void testAliasedImportVsImplicitImport() throws Exception {
    PsiReference ref = configureByFile("aliasedImportVsImplicitImport/Test.groovy");
    final PsiElement resolved = ref.resolve();
    assertInstanceOf(resolved, PsiClass.class);
    assertEquals("java.util.ArrayList", ((PsiClass)resolved).getQualifiedName());
  }

  public void testNotQualifiedStaticImport() throws Exception {
    myFixture.addFileToProject("foo/A.groovy", "package foo \nclass Foo{ }");
    PsiReference ref = configureByFile("notQualifiedStaticImport/Test.groovy");
    final PsiElement resolved = ref.resolve();
    assertInstanceOf(resolved, PsiClass.class);
  }

  public void testEnumVsProperty() throws Exception {
    PsiReference ref = configureByFile("enumVsProperty/Test.groovy");
    final PsiElement resolved = ref.resolve();
    assertInstanceOf(resolved, PsiClass.class);
  }

  public void testTwoStaticImports() throws Exception {
    final PsiReference ref = configureByFile("twoStaticImports/Test.groovy");
    final PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
  }

  public void testAliasedImportedClassFromDefaultPackage() throws Exception {
    myFixture.addClass("class Foo{}");
    final PsiReference ref = configureByFile("aliasedImportedClassFromDefaultPackage/Test.groovy");
    final PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
  }

  private void doTest() throws Exception {
    doTest(getTestName(true) + "/" + getTestName(false) + ".groovy");
  }

  private void doTest(String fileName) throws Exception {
    PsiReference ref = configureByFile(fileName);
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiClass);
  }
}
