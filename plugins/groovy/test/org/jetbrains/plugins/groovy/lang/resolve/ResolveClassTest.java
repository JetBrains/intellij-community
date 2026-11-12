// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveFixtureKt.groovyResolveFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class ResolveClassTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<GroovyResolveFixture> resolveFixture =
    groovyResolveFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "resolve/class/");

  @Test
  public void testInnerJavaClass() {
    doTest("B.groovy");
  }

  @Test
  public void testSamePackage() {
    doTest("B.groovy");
  }

  @Test
  public void testImplicitImport() {
    doTest("B.groovy");
  }

  @Test
  public void testOnDemandImport() {
    doTest("B.groovy");
  }

  @Test
  public void testSimpleImport() {
    doTest("B.groovy");
  }

  @Test
  public void testQualifiedName() {
    doTest("B.groovy");
  }

  @Test
  public void testImportAlias() {
    doTest("B.groovy");
  }

  @Test
  public void testQualifiedRefExpr() {
    doTest("A.groovy");
  }

  @Test
  public void testGrvy102() {
    doTest("Test.groovy");
  }

  @Test
  public void testClassVsProperty() {
    doTest("Test.groovy");
  }

  @Test
  public void testGrvy901() {
    doTest("Test.groovy");
  }

  @Test
  public void testGrvy641() {
    PsiReference ref = resolveFixture.get().configureByFile("grvy641/A.groovy");
    ReadAction.runBlocking(() -> {
      PsiClass resolved = assertInstanceOf(PsiClass.class, ref.resolve());
      if (!"List".equals(resolved.getQualifiedName())) {
        fail(resolved.getQualifiedName());
      }
    });
  }

  @Test
  public void testGrvy1139() {
    PsiReference ref = resolveFixture.get().configureByFile("grvy1139/p/User.groovy");
    ReadAction.runBlocking(() -> {
      assertNull(ref.resolve());
    });
  }

  @Test
  public void testGrvy1420() {
    PsiReference ref = resolveFixture.get().configureByFile("grvy1420/Test.groovy");
    ReadAction.runBlocking(() -> {
      assertNull(ref.resolve());
    });
  }

  @Test
  public void testGrvy1420_1() {
    PsiReference ref = resolveFixture.get().configureByFile("grvy1420_1/Test.groovy");
    ReadAction.runBlocking(() -> {
      assertNull(ref.resolve());
    });
  }

  @Test
  public void testGrvy1461() {
    PsiReference ref = resolveFixture.get().configureByFile("grvy1461/AssertionUtils.groovy");
    ReadAction.runBlocking(() -> {
      assertNotNull(ref.resolve());
    });
  }

  public void _testImportStaticFromJavaUtil() { doTest(); }

  @Test
  public void testInnerEnum() { doTest(); }

  @Test
  public void testInnerClass() throws Throwable { doTest(); }

  @Test
  public void testInnerClassInSubclass() { doTest(); }

  @Test
  public void testInnerClassUsageInsideOuterSubclass() { doTest(); }

  @Test
  public void testInnerClassOfInterface() { doTest(); }

  @Test
  public void testInnerClassOfClassInSubClass1() { assertNull(resolveFixture.get().resolve()); }

  @Test
  public void testAliasedImportVsImplicitImport() {
    PsiReference ref = resolveFixture.get().configureByFile("aliasedImportVsImplicitImport/Test.groovy");
    ReadAction.runBlocking(() -> {
      final PsiElement resolved = ref.resolve();
      assertInstanceOf(PsiClass.class, resolved);
      assertEquals("java.util.ArrayList", ((PsiClass)resolved).getQualifiedName());
    });
  }

  @Test
  public void testNotQualifiedStaticImport() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addFileToProject("foo/A.groovy", """
      package foo
      class Foo{ }""");
    PsiReference ref = resolveFixture.get().configureByFile("notQualifiedStaticImport/Test.groovy");
    ReadAction.runBlocking(() -> {
      final PsiElement resolved = ref.resolve();
      assertInstanceOf(PsiClass.class, resolved);
    });
  }

  @Test
  public void testEnumVsProperty() {
    PsiReference ref = resolveFixture.get().configureByFile("enumVsProperty/Test.groovy");
    ReadAction.runBlocking(() -> {
      final PsiElement resolved = ref.resolve();
      assertInstanceOf(PsiField.class, resolved);
    });
  }

  @Test
  public void testTwoStaticImports() {
    final PsiReference ref = resolveFixture.get().configureByFile("twoStaticImports/Test.groovy");
    ReadAction.runBlocking(() -> {
      final PsiElement resolved = ref.resolve();
      assertNotNull(resolved);
    });
  }

  @Test
  public void testAliasedImportedClassFromDefaultPackage() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addClass("class Foo{}");
    final PsiReference ref = resolveFixture.get().configureByFile("aliasedImportedClassFromDefaultPackage/Test.groovy");
    ReadAction.runBlocking(() -> {
      final PsiElement resolved = ref.resolve();
      assertNotNull(resolved);
    });
  }

  @Test
  public void testQualifiedRefToInnerClass() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addFileToProject("A.groovy", "class A {class Bb {}}");
    final PsiReference ref = resolveFixture.get().configureByText("b.groovy", "A.B<ref>b b = new A.Bb()");
    ReadAction.runBlocking(() -> {
      assertNotNull(ref.resolve());
    });
  }

  @Test
  public void testClassVsPropertyGetter() {
    doTest();
  }

  @Test
  public void testPackageVsProperty1() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
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
    final PsiReference ref = resolveFixture.get().configureByFile("packageVsProperty1/Test.groovy");
    ReadAction.runBlocking(() -> {
      final PsiElement resolved = ref.resolve();
      assertInstanceOf(GrAccessorMethod.class, resolved);
    });
  }

  @Test
  public void testPackageVsProperty2() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
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
    final PsiReference ref = resolveFixture.get().configureByFile("packageVsProperty2/Test.groovy");
    ReadAction.runBlocking(() -> {
      final PsiElement resolved = ref.resolve();
      assertInstanceOf(GrAccessorMethod.class, resolved);
    });
  }

  @Test
  public void testLowerCaseClassName() {
    doTest();
  }

  @Test
  public void testInnerClassIsResolvedInAnonymous() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addFileToProject("/p/Super.groovy", """
      package p
      
      interface Super {
        class Inner {
        }
      
        def foo(Inner i);
      }""");
    assertInstanceOf(PsiClass.class, resolveFixture.get().resolve("A.groovy"));
  }

  /**
   * <a href="https://issues.apache.org/jira/browse/GROOVY-8364">GROOVY-8364</a>
   */
  @Test
  public void testPreferImportsToInheritance() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addClass("package java.util; public class MyMap { interface Entry<K,V> {} } ");
    myFixture.addClass("package java.util; public class MainMap { interface Entry<K,V> {} } ");

    myFixture.configureByText("a.groovy", """
      import java.util.MainMap.Entry;
      
      public class Test extends MyMap {
          public void m(E<caret>ntry<String, String> o) {}
      }
      """);
    ReadAction.runBlocking(() -> {
      PsiElement target = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset()).resolve();
      PsiClass psiClass = assertInstanceOf(PsiClass.class, target);
      assertEquals("java.util.MyMap.Entry", psiClass.getQualifiedName());
    });
  }

  @Test
  public void testPreferLastImportedAlias() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addFileToProject("a/C1.groovy", "package a; class C1{}");
    myFixture.addFileToProject("a/C2.groovy", "package a; class C2{}");
    assertEquals("C2", ((PsiClass)resolveFixture.get().resolve("A.groovy")).getName());
  }

  @Test
  public void testPreferImportsToImplicit() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addFileToProject("a/C1.groovy", "package a; class Factory{}");
    assertEquals("a.Factory", ((PsiClass)resolveFixture.get().resolve("A.groovy")).getQualifiedName());
  }

  @Test
  public void testPreferClassFromCurPackage() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addFileToProject("a/Cl.groovy", "package a; class Cl{}");
    myFixture.addFileToProject("b/Cl.groovy", "package b; class Cl{}");
    assertEquals("a.Cl", ((PsiClass)resolveFixture.get().resolve("a.groovy")).getQualifiedName());
  }

  @Test
  public void testInnerClassInStaticImport() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addClass("package x; public class X{public static class Inner{}}");
    PsiElement resolved = resolveFixture.get().resolve("a.groovy");
    assertNotNull(resolved);
  }

  @Test
  public void testInnerClassImportedByStaticImport() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addClass("""
                         package x;
                         public class X{
                           public static class Inner{
                           }
                         }""");
    assertNotNull(resolveFixture.get().resolve("a.groovy"));
  }

  @Test
  public void testOnDemandJavaAwtVsJavUtilList() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addClass("package java.awt; public class Component{}");
    myFixture.addClass("package java.awt; public class List{}");
    myFixture.configureByText("_.groovy", """
      import java.awt.*
      import java.util.List
      
      print Component
      print Li<caret>st
      """);
    ReadAction.runBlocking(() -> {
      PsiElement target = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset()).resolve();
      assertTrue(target instanceof PsiClass);
      assertEquals("java.util.List", ((PsiClass)target).getQualifiedName());
    });
  }

  @Test
  public void testSuper() {
    resolveFixture.get().resolveByText("""
                    class Base {
                    }
                    
                    class Inheritor {
                      class Inner {
                        def f = Inheritor.su<caret>per.className
                      }
                    }
                    """, PsiClass.class);
  }

  @Test
  public void testInterfaceDoesNotResolveWithExpressionQualifier() {
    PsiReference ref = resolveFixture.get().configureByText(
      """
        class Foo {
          interface Inner {
          }
        }
        
        new Foo().Inn<caret>er
        """);

    ReadAction.runBlocking(() -> {
      assertNull(ref.resolve());
    });
  }

  @Test
  public void testInnerClassOfInterfaceInsideItself() {
    resolveFixture.get().resolveByText(
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

  @Test
  public void testCollisionOfClassAndPackage() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addFileToProject("foo/Bar.groovy", """
      package foo
      
      class Bar {
        static void xyz(){}
      }
      """);
    PsiReference ref = resolveFixture.get().configureByText("foo.groovy", """
      import foo.B<caret>ar
      
      print new Bar()
      """);

    ReadAction.runBlocking(() -> {
      assertNotNull(ref.resolve());
    });
  }

  @Test
  public void testCollisionOfClassAndPackage2() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addFileToProject("foo/Bar.groovy", """
      package foo
      
      class Bar {
        static void xyz(){}
      }
      """);
    PsiReference ref = resolveFixture.get().configureByText("foo.groovy", """
      import static foo.Bar.xyz
      
      class foo {
        public static void main(args) {
          x<caret>yz()      //should resolve to different file
        }
      
        static class Bar {
          static void xyz() {}
        }
      }
      """);

    ReadAction.runBlocking(() -> {
      PsiElement resolved = ref.resolve();
      assertInstanceOf(PsiMethod.class, resolved);

      PsiClass clazz = ((PsiMember)resolved).getContainingClass();
      assertEquals("Bar.groovy", clazz.getContainingFile().getName());
    });
  }

  @Test
  public void testCollisionOfClassAndPackage3() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addFileToProject("foo/Bar.groovy", """
      package foo
      
      class Bar {
        static void xyz(){}
      }
      """);
    PsiReference ref = resolveFixture.get().configureByText("foo.groovy", """
      import static foo.Bar.xyz
      
      x<caret>yz()
      """);

    ReadAction.runBlocking(() -> {
      assertNotNull(ref.resolve());
    });
  }

  @Test
  public void testCollisionOfClassAndPackage4() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addFileToProject("foo/Bar.groovy", """
      package foo
      
      class Bar {
        static void xyz(){}
      }
      """);

    PsiReference ref = resolveFixture.get().configureByText("foo.groovy", """
      import static foo.Bar.xyz
      
      class foo {
        public static void main(String[] args) {
          x<caret>yz()
        }
      }
      """);

    ReadAction.runBlocking(() -> {
      PsiElement resolved = ref.resolve();
      assertInstanceOf(PsiMethod.class, resolved);
    });
  }

  @Test
  public void testSuperInTrait1() {
    PsiClass clazz = resolveFixture.get().resolveByText(
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

    ReadAction.runBlocking(() -> {
      assertEquals("T1", clazz.getQualifiedName());
    });
  }

  @Test
  public void testSuperInTrait2() {
    PsiClass clazz = resolveFixture.get().resolveByText(
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

    ReadAction.runBlocking(() -> {
      assertEquals("T2", clazz.getQualifiedName());
    });
  }

  @Test
  public void testSuperInTrait3() {
    PsiClass clazz = resolveFixture.get().resolveByText(
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

    ReadAction.runBlocking(() -> {
      assertEquals("T1", clazz.getQualifiedName());
    });
  }

  @Test
  public void testSuperInTrait4() {
    PsiClass clazz = resolveFixture.get().resolveByText(
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

    ReadAction.runBlocking(() -> {
      assertEquals("T1", clazz.getQualifiedName());
    });
  }

  @Test
  public void testClassVsPropertyUppercase() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addFileToProject("bar/Foo.groovy",
      """
      package bar
      
      class Foo {
          def UPPERCASE
      }
      """);
    resolveFixture.get().resolveByText("""
                    def bar = new bar.Foo()
                    bar.UPPER<caret>CASE
                    """, GrAccessorMethod.class);

    myFixture.addFileToProject("bar/UPPERCASE.groovy", """
      package bar
      
      class UPPERCASE {}
      """);
    resolveFixture.get().resolveByText("""
                    def bar = new bar.Foo()
                    bar.UPPER<caret>CASE
                    """, GrTypeDefinition.class);
  }

  @Test
  public void testClassVsPropertyCapitalized() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addFileToProject("bar/Foo.groovy", """
      package bar
      
      class Foo {
          def Capitalized
      }
      """);
    resolveFixture.get().resolveByText("""
                    
                    def bar = new bar.Foo()
                    bar.Capital<caret>ized
                    """, GrField.class);

    myFixture.addFileToProject("bar/Capitalized.groovy", """
      package bar
      
      class Capitalized {}
      """);
    resolveFixture.get().resolveByText("""
                    def bar = new bar.Foo()
                    bar.Capital<caret>ized
                    """, GrTypeDefinition.class);
  }

  @Test
  public void testClassVsPropertyLowercase() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addFileToProject("bar/Foo.groovy", """
      package bar
      
      class Foo {
          def lowercase
      }
      """);
    resolveFixture.get().resolveByText("""
                    def bar = new bar.Foo()
                    bar.lower<caret>case
                    """, GrAccessorMethod.class);

    myFixture.addFileToProject("bar/lowercase.groovy", """
      package bar
      
      class lowercase {}
      """);
    resolveFixture.get().resolveByText("""
                    def bar = new bar.Foo()
                    bar.lower<caret>case
                    """, GrAccessorMethod.class);
  }

  @Test
  public void testClassVsPropertyCapitalizedWithWhitespacesAndComments() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addFileToProject("bar/Foo.groovy", """
      package bar
      
      class Foo {
          def Capitalized
      }
      """);
    resolveFixture.get().resolveByText("""
                    def bar = new bar.Foo()
                    bar/*comment*/
                        .Capital<caret>ized
                    """, GrField.class);

    myFixture.addFileToProject("bar/Capitalized.groovy", """
      package bar
      
      class Capitalized {}
      """);
    resolveFixture.get().resolveByText("""
                    def bar = new bar.Foo()
                    bar/*comment*/
                        .Capital<caret>ized
                    """, GrTypeDefinition.class);
  }

  @Test
  public void testPreferAliasOverClassInTheSamePackage() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
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
    ReadAction.runBlocking(() -> {
      PsiElement resolved = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset()).resolve();
      assertTrue(resolved instanceof PsiClass);
      assertEquals("foo.Foo", ((PsiClass)resolved).getQualifiedName());
    });
  }

  @Test
  public void testPreferClassInTheSameFileOverAlias() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
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
    ReadAction.runBlocking(() -> {
      PsiElement resolved = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset()).resolve();
      assertTrue( resolved instanceof PsiClass);
      assertEquals("test.Bar", ((PsiClass)resolved).getQualifiedName());
    });
  }

  @Test
  public void testDontResolveToInnerClassOfAnonymousClass() {
    resolveFixture.get().resolveByText("""
                    new <caret>Foo() {
                      static class Foo {}
                    }
                    """, null);
  }

  @Test
  public void testResolveToInnerClassOfAnonymousContainingClass() {
    resolveFixture.get().resolveByText("""
                    class Foo {
                      def foo() {
                        new <caret>Bar() {}
                      }
                      private abstract static class Bar {}
                    }""", PsiClass.class);
  }

  @Test
  public void testResolveToInnerClassViaQualifiedReference() {
    resolveFixture.get().resolveByText("""
                    package xxx
                    class Outer { static class Inner {} }
                    println Outer.<caret>Inner
                    """, PsiClass.class);
  }

  @Test
  public void testResolveToInnerClassOfOuterClassOfAnonymousClass() {
    resolveFixture.get().resolveByText("""
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

  @Test
  public void testNoRecursionWhenResolvingTypeParameterBound() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    RecursionManager.assertOnRecursionPrevention(myFixture.getTestRootDisposable());
    resolveFixture.get().resolveByText("""
                    interface I {}
                    class A {
                      def <T extends <caret>I> T foo() {}
                    }
                    """, PsiClass.class);
  }

  @Test
  public void testNoRecursionWhenResolvingInnerClassInImplementsList() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    RecursionManager.assertOnRecursionPrevention(myFixture.getTestRootDisposable());
    myFixture.addClass("""
                            package com.foo;
                            public interface I {
                              interface Inner {}
                            }
                            """);
    resolveFixture.get().resolveByText("""
                    import com.foo.I;
                    class A implements I.<caret>Inner {}
                    """, PsiClass.class);
  }

  private void doTest(String fileName) { resolveFixture.get().resolve(fileName, PsiClass.class); }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    doTest(myFixture.getTestName(false) + ".groovy");
  }
}
