/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author ven
 */
public class ResolveClassTest extends GroovyResolveTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.testDataPath + "resolve/class/";
  }

  public void testInnerJavaClass() throws Exception {
    doTest("B.groovy");
  }

  public void testSamePackage() throws Exception {
    doTest("B.groovy");
  }

  public void testImplicitImport() throws Exception {
    doTest("B.groovy");
  }

  public void testOnDemandImport() throws Exception {
    doTest("B.groovy");
  }

  public void testSimpleImport() throws Exception {
    doTest("B.groovy");
  }

  public void testQualifiedName() throws Exception {
    doTest("B.groovy");
  }

  public void testImportAlias() throws Exception {
    doTest("B.groovy");
  }

  public void testQualifiedRefExpr() throws Exception {
    doTest("A.groovy");
  }

  public void testGrvy102() throws Exception {
    doTest("Test.groovy");
  }

  public void testClassVsProperty() throws Exception {
    doTest("Test.groovy");
  }

  public void testGrvy901() throws Exception {
    doTest("Test.groovy");
  }

  public void testGrvy641() throws Exception {
    PsiReference ref = configureByFile("grvy641/A.groovy")
    PsiClass resolved = assertInstanceOf(ref.resolve(), PsiClass)
    if (!"List".equals(resolved.qualifiedName)) {
      println(myFixture.file.virtualFile.parent.children as List);
      println JavaPsiFacade.getInstance(project).findClass("List", ref.resolveScope)
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
  public void testInnerClassUsageInsideOuterSubclass() throws Throwable { doTest() }
  public void testInnerClassOfInterface() { assertNull(resolve()) }
  public void testInnerClassOfClassInSubClass1() { assertNull(resolve()) }

  public void testAliasedImportVsImplicitImport() throws Exception {
    PsiReference ref = configureByFile("aliasedImportVsImplicitImport/Test.groovy");
    final PsiElement resolved = ref.resolve();
    assertInstanceOf(resolved, PsiClass.class);
    assertEquals("java.util.ArrayList", ((PsiClass)resolved).qualifiedName);
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
    assertInstanceOf(resolved, PsiField.class);
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

  public void testQualifiedRefToInnerClass() {
    myFixture.addFileToProject('A.groovy', 'class A {class Bb {}}')
    final PsiReference ref = configureByText('b.groovy', 'A.B<ref>b b = new A.Bb()')
    assertNotNull(ref.resolve())
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
    def target = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset).resolve()
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
    def target = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset).resolve()
    assert target instanceof PsiClass
    assertEquals('java.util.List', target.qualifiedName)
  }

  void testSuper() {
    resolveByText('''\
class Base {
}

class Inheritor {
  class Inner {
    def f = Inheritor.su<caret>per.className
  }
}
''', PsiClass)
  }

  void testInterfaceDoesNotResolveWithExpressionQualifier() {
    def ref = configureByText('''\
class Foo {
  interface Inner {
  }
}

new Foo().Inn<caret>er
''')

    assertNull(ref.resolve())
  }

  void testInnerClassOfInterfaceInsideItself() {
    resolveByText('''\
public interface OuterInterface {
    static enum InnerEnum {
        ONE, TWO
        public static Inne<caret>rEnum getSome() {
            ONE
        }
    }
}
''', PsiClass)
  }

  void testCollisionOfClassAndPackage() {
    myFixture.addFileToProject('foo/Bar.groovy', '''\
package foo

class Bar {
  static void xyz(){}
}
''')
    def ref = configureByText('foo.groovy', '''\
import foo.B<caret>ar

print new Bar()
''')

    assertNotNull(ref.resolve())
  }

  void testCollisionOfClassAndPackage2() {
    myFixture.addFileToProject('foo/Bar.groovy', '''\
package foo

class Bar {
  static void xyz(){}
}
''')
    def ref = configureByText('foo.groovy', '''\
import static foo.Bar.xyz

class foo {
  public static void main(args) {
    x<caret>yz()      //should resolve to inner class
  }

  static class Bar {
    static void xyz() {}
  }
}
''')

    PsiElement resolved = ref.resolve()
    assertInstanceOf(resolved, PsiMethod)

    PsiClass clazz = resolved.containingClass
    assertNotNull(clazz.containingClass)
  }


  void testCollisionOfClassAndPackage3() {
    myFixture.addFileToProject('foo/Bar.groovy', '''\
package foo

class Bar {
  static void xyz(){}
}
''')
    def ref = configureByText('foo.groovy', '''\
import static foo.Bar.xyz

x<caret>yz()
''')

    assertNotNull(ref.resolve())
  }

  void testCollisionOfClassAndPackage4() {
    myFixture.addFileToProject('foo/Bar.groovy', '''\
package foo

class Bar {
  static void xyz(){}
}
''')

    def ref = configureByText('foo.groovy', '''\
import static foo.Bar.xyz

class foo {
  public static void main(String[] args) {
    x<caret>yz()
  }
}
''')

    PsiElement resolved = ref.resolve()
    assertInstanceOf(resolved, PsiMethod)
  }

  void testSuperInTrait1() {
    def clazz = resolveByText('''
trait T1 {
    void on() {
        println "T1"
    }
}

trait T2 {
    void on() {
        println "T2"
    }
}

trait LoggingHandler extends T1 implements T2 {
    void on() {
        super.o<caret>n()
    }
}
''', PsiMethod).containingClass

    assertEquals("T1", clazz.qualifiedName)
  }

  void testSuperInTrait2() {
    def clazz = resolveByText('''
trait T1 {
    void on() {
        println "T1"
    }
}

trait T2 {
    void on() {
        println "T2"
    }
}

trait LoggingHandler implements T1, T2 {
    void on() {
        super.o<caret>n()
    }
}
''', PsiMethod).containingClass

    assertEquals("T2", clazz.qualifiedName)
  }

  void testSuperInTrait3() {
    def clazz = resolveByText('''
trait T1 {
    void on() {
        println "T1"
    }
}

trait LoggingHandler extends T1 {
    void on() {
        super.o<caret>n()
    }
}
''', PsiMethod).containingClass

    assertEquals("T1", clazz.qualifiedName)
  }

  void testSuperInTrait4() {
    def clazz = resolveByText('''
trait T1 {
    void on() {
        println "T1"
    }
}

trait LoggingHandler implements T1 {
    void on() {
        super.o<caret>n()
    }
}
''', PsiMethod).containingClass

    assertEquals("T1", clazz.qualifiedName)
  }

  void 'test class vs property uppercase'() {
    myFixture.addFileToProject('bar/Foo.groovy', '''\
package bar

class Foo {
    def UPPERCASE
}
''')
    resolveByText('''
def bar = new bar.Foo()
bar.UPPER<caret>CASE
''', GrAccessorMethod)

    myFixture.addFileToProject('bar/UPPERCASE.groovy', '''\
package bar

class UPPERCASE {}
''')
    resolveByText('''
def bar = new bar.Foo()
bar.UPPER<caret>CASE
''', GrTypeDefinition)
  }

  void 'test class vs property capitalized'() {
    myFixture.addFileToProject('bar/Foo.groovy', '''\
package bar

class Foo {
    def Capitalized
}
''')
    resolveByText('''
def bar = new bar.Foo()
bar.Capital<caret>ized
''', GrAccessorMethod)

    myFixture.addFileToProject('bar/Capitalized.groovy', '''\
package bar

class Capitalized {}
''')
    resolveByText('''
def bar = new bar.Foo()
bar.Capital<caret>ized
''', GrTypeDefinition)
  }

  void 'test class vs property lowercase'() {
    myFixture.addFileToProject('bar/Foo.groovy', '''\
package bar

class Foo {
    def lowercase
}
''')
    resolveByText('''
def bar = new bar.Foo()
bar.lower<caret>case
''', GrAccessorMethod)

    myFixture.addFileToProject('bar/lowercase.groovy', '''\
package bar

class lowercase {}
''')
    resolveByText('''
def bar = new bar.Foo()
bar.lower<caret>case
''', GrAccessorMethod)
  }

  void 'test class vs property capitalized with whitespaces and comments'() {
    myFixture.addFileToProject('bar/Foo.groovy', '''\
package bar

class Foo {
    def Capitalized
}
''')
    resolveByText('''
def bar = new bar.Foo()
bar/*comment*/
    .Capital<caret>ized
''', GrAccessorMethod)

    myFixture.addFileToProject('bar/Capitalized.groovy', '''\
package bar

class Capitalized {}
''')
    resolveByText('''
def bar = new bar.Foo()
bar/*comment*/
    .Capital<caret>ized
''', GrTypeDefinition)
  }

  private void doTest(String fileName = getTestName(false) + ".groovy") { resolve(fileName, PsiClass) }
}
