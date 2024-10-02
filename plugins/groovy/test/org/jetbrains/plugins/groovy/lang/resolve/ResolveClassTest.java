// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.Assert;

public class ResolveClassTest extends GroovyResolveTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "resolve/class/";
  }

  public void testInnerJavaClass() {
    doTest("B.groovy");
  }

  public void testSamePackage() {
    doTest("B.groovy");
  }

  public void testImplicitImport() {
    doTest("B.groovy");
  }

  public void testOnDemandImport() {
    doTest("B.groovy");
  }

  public void testSimpleImport() {
    doTest("B.groovy");
  }

  public void testQualifiedName() {
    doTest("B.groovy");
  }

  public void testImportAlias() {
    doTest("B.groovy");
  }

  public void testQualifiedRefExpr() {
    doTest("A.groovy");
  }

  public void testGrvy102() {
    doTest("Test.groovy");
  }

  public void testClassVsProperty() {
    doTest("Test.groovy");
  }

  public void testGrvy901() {
    doTest("Test.groovy");
  }

  public void testGrvy641() {
    PsiReference ref = configureByFile("grvy641/A.groovy");
    PsiClass resolved = UsefulTestCase.assertInstanceOf(ref.resolve(), PsiClass.class);
    if (!"List".equals(resolved.getQualifiedName())) {
      Assert.fail(resolved.getQualifiedName());
    }
  }

  public void testGrvy1139() {
    PsiReference ref = configureByFile("grvy1139/p/User.groovy");
    Assert.assertNull(ref.resolve());
  }

  public void testGrvy1420() {
    PsiReference ref = configureByFile("grvy1420/Test.groovy");
    Assert.assertNull(ref.resolve());
  }

  public void testGrvy1420_1() {
    PsiReference ref = configureByFile("grvy1420_1/Test.groovy");
    Assert.assertNull(ref.resolve());
  }

  public void testGrvy1461() {
    PsiReference ref = configureByFile("grvy1461/AssertionUtils.groovy");
    Assert.assertNotNull(ref.resolve());
  }

  public void _testImportStaticFromJavaUtil() { doTest(); }

  public void testInnerEnum() { doTest(); }

  public void testInnerClass() throws Throwable { doTest(); }

  public void testInnerClassInSubclass() { doTest(); }

  public void testInnerClassUsageInsideOuterSubclass() { doTest(); }

  public void testInnerClassOfInterface() { doTest(); }

  public void testInnerClassOfClassInSubClass1() { Assert.assertNull(resolve()); }

  public void testAliasedImportVsImplicitImport() {
    PsiReference ref = configureByFile("aliasedImportVsImplicitImport/Test.groovy");
    final PsiElement resolved = ref.resolve();
    UsefulTestCase.assertInstanceOf(resolved, PsiClass.class);
    Assert.assertEquals("java.util.ArrayList", ((PsiClass)resolved).getQualifiedName());
  }

  public void testNotQualifiedStaticImport() {
    myFixture.addFileToProject("foo/A.groovy", """
      package foo
      class Foo{ }""");
    PsiReference ref = configureByFile("notQualifiedStaticImport/Test.groovy");
    final PsiElement resolved = ref.resolve();
    UsefulTestCase.assertInstanceOf(resolved, PsiClass.class);
  }

  public void testEnumVsProperty() {
    PsiReference ref = configureByFile("enumVsProperty/Test.groovy");
    final PsiElement resolved = ref.resolve();
    UsefulTestCase.assertInstanceOf(resolved, PsiField.class);
  }

  public void testTwoStaticImports() {
    final PsiReference ref = configureByFile("twoStaticImports/Test.groovy");
    final PsiElement resolved = ref.resolve();
    Assert.assertNotNull(resolved);
  }

  public void testAliasedImportedClassFromDefaultPackage() {
    myFixture.addClass("class Foo{}");
    final PsiReference ref = configureByFile("aliasedImportedClassFromDefaultPackage/Test.groovy");
    final PsiElement resolved = ref.resolve();
    Assert.assertNotNull(resolved);
  }

  public void testQualifiedRefToInnerClass() {
    myFixture.addFileToProject("A.groovy", "class A {class Bb {}}");
    final PsiReference ref = configureByText("b.groovy", "A.B<ref>b b = new A.Bb()");
    Assert.assertNotNull(ref.resolve());
  }

  public void testClassVsPropertyGetter() {
    doTest();
  }

  public void testPackageVsProperty1() {
    myFixture.addFileToProject("foo/Foo.groovy", """
      package foo
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
    UsefulTestCase.assertInstanceOf(resolved, GrAccessorMethod.class);
  }

  public void testPackageVsProperty2() {
    myFixture.addFileToProject("foo/Foo.groovy", """
      package foo
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
    UsefulTestCase.assertInstanceOf(resolved, GrAccessorMethod.class);
  }

  public void testLowerCaseClassName() {
    doTest();
  }

  public void testInnerClassIsResolvedInAnonymous() {
    myFixture.addFileToProject("/p/Super.groovy", """
      package p
      
      interface Super {
        class Inner {
        }
      
        def foo(Inner i);
      }""");
    UsefulTestCase.assertInstanceOf(resolve("A.groovy"), PsiClass.class);
  }

  /**
   * <a href="https://issues.apache.org/jira/browse/GROOVY-8364">GROOVY-8364</a>
   */
  public void testPreferImportsToInheritance() {
    myFixture.addClass("package java.util; public class MyMap { interface Entry<K,V> {} } ");
    myFixture.addClass("package java.util; public class MainMap { interface Entry<K,V> {} } ");

    myFixture.configureByText("a.groovy", """
      import java.util.MainMap.Entry;
      
      public class Test extends MyMap {
          public void m(E<caret>ntry<String, String> o) {}
      }
      """);
    PsiElement target = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset()).resolve();
    PsiClass psiClass = UsefulTestCase.assertInstanceOf(target, PsiClass.class);
    Assert.assertEquals("java.util.MyMap.Entry", psiClass.getQualifiedName());
  }

  public void testPreferLastImportedAlias() {
    myFixture.addFileToProject("a/C1.groovy", "package a; class C1{}");
    myFixture.addFileToProject("a/C2.groovy", "package a; class C2{}");
    Assert.assertEquals("C2", ((PsiClass)resolve("A.groovy")).getName());
  }

  public void testPreferImportsToImplicit() {
    myFixture.addFileToProject("a/C1.groovy", "package a; class Factory{}");
    Assert.assertEquals("a.Factory", ((PsiClass)resolve("A.groovy")).getQualifiedName());
  }

  public void testPreferClassFromCurPackage() {
    myFixture.addFileToProject("a/Cl.groovy", "package a; class Cl{}");
    myFixture.addFileToProject("b/Cl.groovy", "package b; class Cl{}");
    Assert.assertEquals("a.Cl", ((PsiClass)resolve("a.groovy")).getQualifiedName());
  }

  public void testInnerClassInStaticImport() {
    myFixture.addClass("package x; public class X{public static class Inner{}}");
    PsiElement resolved = resolve("a.groovy");
    Assert.assertNotNull(resolved);
  }

  public void testInnerClassImportedByStaticImport() {
    myFixture.addClass("""
                         package x;
                         public class X{
                           public static class Inner{
                           }
                         }""");
    Assert.assertNotNull(resolve("a.groovy"));
  }

  public void testOnDemandJavaAwtVsJavUtilList() {
    myFixture.addClass("package java.awt; public class Component{}");
    myFixture.addClass("package java.awt; public class List{}");
    myFixture.configureByText("_.groovy", """
      import java.awt.*
      import java.util.List
      
      print Component
      print Li<caret>st
      """);
    PsiElement target = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset()).resolve();
    Assert.assertTrue(target instanceof PsiClass);
    Assert.assertEquals("java.util.List", ((PsiClass)target).getQualifiedName());
  }

  public void testSuper() {
    resolveByText("""
                    class Base {
                    }
                    
                    class Inheritor {
                      class Inner {
                        def f = Inheritor.su<caret>per.className
                      }
                    }
                    """, PsiClass.class);
  }

  public void testInterfaceDoesNotResolveWithExpressionQualifier() {
    PsiReference ref = configureByText(
      """
        class Foo {
          interface Inner {
          }
        }
        
        new Foo().Inn<caret>er
        """);

    Assert.assertNull(ref.resolve());
  }

  public void testInnerClassOfInterfaceInsideItself() {
    resolveByText(
      """
        public interface OuterInterface {
            static enum InnerEnum {
                ONE, TWO
                public static Inne<caret>rEnum getSome() {
                    ONE
                }
            }
        }
        """, PsiClass.class);
  }

  public void testCollisionOfClassAndPackage() {
    myFixture.addFileToProject("foo/Bar.groovy", """
      package foo
      
      class Bar {
        static void xyz(){}
      }
      """);
    PsiReference ref = configureByText("foo.groovy", """
      import foo.B<caret>ar
      
      print new Bar()
      """);

    Assert.assertNotNull(ref.resolve());
  }

  public void testCollisionOfClassAndPackage2() {
    myFixture.addFileToProject("foo/Bar.groovy", """
      package foo
      
      class Bar {
        static void xyz(){}
      }
      """);
    PsiReference ref = configureByText("foo.groovy", """
      import static foo.Bar.xyz
      
      class foo {
        public static void main(args) {
          x<caret>yz()      //should resolve to inner class
        }
      
        static class Bar {
          static void xyz() {}
        }
      }
      """);

    PsiElement resolved = ref.resolve();
    UsefulTestCase.assertInstanceOf(resolved, PsiMethod.class);

    PsiClass clazz = ((PsiMember)resolved).getContainingClass();
    Assert.assertNotNull(clazz.getContainingClass());
  }

  public void testCollisionOfClassAndPackage3() {
    myFixture.addFileToProject("foo/Bar.groovy", """
      package foo
      
      class Bar {
        static void xyz(){}
      }
      """);
    PsiReference ref = configureByText("foo.groovy", """
      import static foo.Bar.xyz
      
      x<caret>yz()
      """);

    Assert.assertNotNull(ref.resolve());
  }

  public void testCollisionOfClassAndPackage4() {
    myFixture.addFileToProject("foo/Bar.groovy", """
      package foo
      
      class Bar {
        static void xyz(){}
      }
      """);

    PsiReference ref = configureByText("foo.groovy", """
      import static foo.Bar.xyz
      
      class foo {
        public static void main(String[] args) {
          x<caret>yz()
        }
      }
      """);

    PsiElement resolved = ref.resolve();
    UsefulTestCase.assertInstanceOf(resolved, PsiMethod.class);
  }

  public void testSuperInTrait1() {
    PsiClass clazz = resolveByText(
      """
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
        """, PsiMethod.class).getContainingClass();

    Assert.assertEquals("T1", clazz.getQualifiedName());
  }

  public void testSuperInTrait2() {
    PsiClass clazz = resolveByText(
      """
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
        """, PsiMethod.class).getContainingClass();

    Assert.assertEquals("T2", clazz.getQualifiedName());
  }

  public void testSuperInTrait3() {
    PsiClass clazz = resolveByText(
      """
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
        """, PsiMethod.class).getContainingClass();

    Assert.assertEquals("T1", clazz.getQualifiedName());
  }

  public void testSuperInTrait4() {
    PsiClass clazz = resolveByText(
      """
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
        """, PsiMethod.class).getContainingClass();

    Assert.assertEquals("T1", clazz.getQualifiedName());
  }

  public void testClassVsPropertyUppercase() {
    myFixture.addFileToProject("bar/Foo.groovy",
      """
      package bar
      
      class Foo {
          def UPPERCASE
      }
      """);
    resolveByText("""
                    def bar = new bar.Foo()
                    bar.UPPER<caret>CASE
                    """, GrAccessorMethod.class);

    myFixture.addFileToProject("bar/UPPERCASE.groovy", """
      package bar
      
      class UPPERCASE {}
      """);
    resolveByText("""
                    def bar = new bar.Foo()
                    bar.UPPER<caret>CASE
                    """, GrTypeDefinition.class);
  }

  public void testClassVsPropertyCapitalized() {
    myFixture.addFileToProject("bar/Foo.groovy", """
      package bar
      
      class Foo {
          def Capitalized
      }
      """);
    resolveByText("""
                    
                    def bar = new bar.Foo()
                    bar.Capital<caret>ized
                    """, GrField.class);

    myFixture.addFileToProject("bar/Capitalized.groovy", """
      package bar
      
      class Capitalized {}
      """);
    resolveByText("""
                    def bar = new bar.Foo()
                    bar.Capital<caret>ized
                    """, GrTypeDefinition.class);
  }

  public void testClassVsPropertyLowercase() {
    myFixture.addFileToProject("bar/Foo.groovy", """
      package bar
      
      class Foo {
          def lowercase
      }
      """);
    resolveByText("""
                    def bar = new bar.Foo()
                    bar.lower<caret>case
                    """, GrAccessorMethod.class);

    myFixture.addFileToProject("bar/lowercase.groovy", """
      package bar
      
      class lowercase {}
      """);
    resolveByText("""
                    def bar = new bar.Foo()
                    bar.lower<caret>case
                    """, GrAccessorMethod.class);
  }

  public void testClassVsPropertyCapitalizedWithWhitespacesAndComments() {
    myFixture.addFileToProject("bar/Foo.groovy", """
      package bar
      
      class Foo {
          def Capitalized
      }
      """);
    resolveByText("""
                    def bar = new bar.Foo()
                    bar/*comment*/
                        .Capital<caret>ized
                    """, GrField.class);

    myFixture.addFileToProject("bar/Capitalized.groovy", """
      package bar
      
      class Capitalized {}
      """);
    resolveByText("""
                    def bar = new bar.Foo()
                    bar/*comment*/
                        .Capital<caret>ized
                    """, GrTypeDefinition.class);
  }

  public void testPreferAliasOverClassInTheSamePackage() {
    myFixture.addClass("""
                        package foo;
                        interface Foo {}
                        """);
    myFixture.addClass("""
                    package test;
                    class Bar {}
                    """);
    PsiFile file = myFixture.addFileToProject("test/a.groovy", """
      package test
      
      import foo.Foo as Bar
      
      new Ba<caret>r() {}
      """);
    myFixture.configureFromExistingVirtualFile(file.getContainingFile().getVirtualFile());
    PsiElement resolved = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset()).resolve();
    Assert.assertTrue(resolved instanceof PsiClass);
    Assert.assertEquals("foo.Foo", ((PsiClass)resolved).getQualifiedName());
  }

  public void testPreferClassInTheSameFileOverAlias() {
    myFixture.addClass("""
                        package foo;
                        interface Foo {}
                        """);
    PsiFile file = myFixture.addFileToProject("test/a.groovy", """
      package test
      import foo.Foo as Bar
      interface Bar {}
      new B<caret>ar() {}
      """);
    myFixture.configureFromExistingVirtualFile(file.getContainingFile().getVirtualFile());
    PsiElement resolved = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset()).resolve();
    Assert.assertTrue( resolved instanceof PsiClass);
    Assert.assertEquals("test.Bar", ((PsiClass)resolved).getQualifiedName());
  }

  public void testDontResolveToInnerClassOfAnonymousClass() {
    resolveByText("""
                    new <caret>Foo() {
                      static class Foo {}
                    }
                    """, null);
  }

  public void testResolveToInnerClassOfAnonymousContainingClass() {
    resolveByText("""
                    class Foo {
                      def foo() {
                        new <caret>Bar() {}
                      }
                      private abstract static class Bar {}
                    }""", PsiClass.class);
  }

  public void testResolveToInnerClassViaQualifiedReference() {
    resolveByText("""
                    package xxx
                    class Outer { static class Inner {} }
                    println Outer.<caret>Inner
                    """, PsiClass.class);
  }

  public void testResolveToInnerClassOfOuterClassOfAnonymousClass() {
    resolveByText("""
                    class Foobar {
                      private static class Quuz {}
                      void foo() {
                        new Runnable() {
                          void run() {
                            new <caret>Quuz()
                          }
                        }
                      }
                    }
                    """, PsiClass.class);
  }

  public void testNoRecursionWhenResolvingTypeParameterBound() {
    RecursionManager.assertOnRecursionPrevention(getTestRootDisposable());
    resolveByText("""
                    interface I {}
                    class A {
                      def <T extends <caret>I> T foo() {}
                    }
                    """, PsiClass.class);
  }

  public void testNoRecursionWhenResolvingInnerClassInImplementsList() {
    RecursionManager.assertOnRecursionPrevention(getTestRootDisposable());
    getFixture().addClass("""
                            package com.foo;
                            public interface I {
                              interface Inner {}
                            }
                            """);
    resolveByText("""
                    import com.foo.I;
                    class A implements I.<caret>Inner {}
                    """, PsiClass.class);
  }

  private void doTest(String fileName) { resolve(fileName, PsiClass.class); }

  private void doTest() {
    doTest(getTestName(false) + ".groovy");
  }
}
