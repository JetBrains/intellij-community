/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.resolve;


import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod
import org.jetbrains.plugins.groovy.util.TestUtils

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
    PsiReference ref = configureByFile("grvy641/A.groovy")
    PsiClass resolved = assertInstanceOf(ref.resolve(), PsiClass)
    if (!"List".equals(resolved.qualifiedName)) {
      println(myFixture.file.virtualFile.parent.children as List);
      println JavaPsiFacade.getInstance(project).findClass("List")
      fail(resolved.qualifiedName);
    }
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

  public void testClassVsPropertyGetter() {
    doTest();
  }

  public void testPackageVsProperty1() {
    myFixture.addFileToProject("foo/Foo.groovy", """package foo
class Referenced {
  static def foo = new X()
  static def bar = "bar"

}

class X {
  def referenced = 3
}
""");
    final PsiReference ref = configureByFile("packageVsProperty1/Test.groovy");
    final PsiElement resolved = ref.resolve();
    assertInstanceOf resolved, GrAccessorMethod;
  }

  public void testPackageVsProperty2() {
    myFixture.addFileToProject("foo/Foo.groovy", """package foo
class Referenced {
  static def foo = new X()
  static def bar = "bar"

}

class X {
  def referenced = 3
}
""");
    final PsiReference ref = configureByFile("packageVsProperty2/Test.groovy");
    final PsiElement resolved = ref.resolve();
    assertInstanceOf resolved, GrAccessorMethod;
  }

  public void testLowerCaseClassName() {
    doTest()
  }

  public void testInnerClassIsNotResolvedInAnonymous() {
    myFixture.addFileToProject "/p/Super.groovy", """
package p

interface Super {
  class Inner {
  }

  def foo(Inner i);
}"""
    assertNull resolve("A.groovy");
  }

  public void testInnerClassIsResolvedInAnonymous() {
    myFixture.addFileToProject "/p/Super.groovy", """
package p

interface Super {
  class Inner {
  }

  def foo(Inner i);
}"""
    assertInstanceOf resolve("A.groovy"), PsiClass;
  }

  public void testPreferImportsToInheritance() {
    myFixture.addClass("package java.util; public class MyMap { static interface Entry<K,V> {} } ")
    myFixture.addClass("package java.util; public class MainMap { static interface Entry<K,V> {} } ")

    myFixture.configureByText("a.groovy", """
import java.util.MainMap.Entry;

public class Test extends MyMap {
    public void m(E<caret>ntry<String, String> o) {}
}
""")
    def target = myFixture.getFile().findReferenceAt(myFixture.editor.caretModel.offset).resolve()
    assert assertInstanceOf(target, PsiClass).qualifiedName == 'java.util.MainMap.Entry'
  }

  void testPreferLastImportedAlias() {
    myFixture.addFileToProject "a/C1.groovy", "package a; class C1{}"
    myFixture.addFileToProject "a/C2.groovy", "package a; class C2{}"
    assertEquals "C2", ((PsiClass) resolve("A.groovy")).name
  }

  void testPreferImportsToImplicit() {
    myFixture.addFileToProject "a/C1.groovy", "package a; class Factory{}"
    assertEquals "a.Factory", ((PsiClass) resolve("A.groovy")).qualifiedName
  }

  void testPreferClassFromCurPackage() {
    myFixture.addFileToProject "a/Cl.groovy", "package a; class Cl{}"
    myFixture.addFileToProject "b/Cl.groovy", "package b; class Cl{}"
    assertEquals "a.Cl", resolve("a.groovy").qualifiedName
  }

  void testInnerClassInStaticImport() {
    myFixture.addClass("package x; public class X{public static class Inner{}}")
    def resolved = resolve("a.groovy")
    assertNotNull(resolved)
  }

  void testInnerClassImportedByStaticImport() {
    myFixture.addClass("""
package x;
public class X{
  public static class Inner{
  }
}""")
    assertNotNull(resolve("a.groovy"))
  }

  void testOnDemandJavaAwtVsJavUtilList() {
    myFixture.addClass('''package java.awt; public class Component{}''')
    myFixture.addClass('''package java.awt; public class List{}''')
    myFixture.configureByText('_.groovy', ''''\
import java.awt.*
import java.util.List

print Component
print Li<caret>st
''')
    def target = myFixture.getFile().findReferenceAt(myFixture.editor.caretModel.offset).resolve()
    assertEquals('java.util.List', target.getQualifiedName())
  }

  private void doTest() {
    doTest(getTestName(true) + "/" + getTestName(false) + ".groovy");
  }

  private void doTest(String fileName) {
    PsiReference ref = configureByFile(fileName);
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiClass);
  }
}
