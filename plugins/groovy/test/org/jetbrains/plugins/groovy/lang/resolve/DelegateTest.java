// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMirrorElement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.transformations.TransformationUtilKt;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveFixtureKt.groovyResolveFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Max Medvedev
 */
@TestApplication
public class DelegateTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<GroovyResolveFixture> resolve =
    groovyResolveFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "resolve/delegate/");

  private <T> T doTest(String text, Class<T> clazz) {
    PsiReference ref = resolve.get().configureByText(text);
    return ReadAction.computeBlocking(() -> {
      PsiElement resolved = ref.resolve();

      if (clazz != null) {
        assertNotNull(resolved);
        return assertInstanceOf(clazz, resolved);
      }
      else {
        assertNull(resolved);
      }

      return null;
    });
  }

  private PsiMirrorElement doTest(String text) {
    return doTest(text, PsiMirrorElement.class);
  }

  @Test
  public void testSimple() {
    doTest("""
             class A {
               def foo(){}
             }
             
             class B {
               @Delegate A a
             }
             
             new B().fo<caret>o()
             """);
  }

  @Test
  public void testSimple2() {
    doTest("""
             class A {
               def foo(){}
             }
             
             class B {
               @Delegate A getA(){return new A()}
             }
             
             new B().fo<caret>o()
             """);
  }

  @Test
  public void testMethodDelegateUnresolved() {
    doTest("""
             class A {
               def foo(){}
             }
             
             class B {
               @Delegate A getA(int i){return new A()}
             }
             
             new B().fo<caret>o()
             """, null);
  }

  @Test
  public void testInheritance() {
    doTest("""
             class Base {
               def foo(){}
             }
             
             class A extends Base {}
             
             class B {
               @Delegate A a
             }
             
             new B().fo<caret>o()
             """);
  }

  @Test
  public void testSelectFirst1() {
    PsiMirrorElement resolved = doTest(
      """
        class A1 {
          def foo(){}
        }
        
        class A2 {
          def foo(){}
        }
        
        class B {
          @Delegate A1 a1
          @Delegate A2 a2
        }
        
        new B().fo<caret>o()
        """);

    ReadAction.runBlocking(() -> {
      PsiMethod prototype = (PsiMethod)resolved.getPrototype();
      PsiClass cc = prototype.getContainingClass();
      assertEquals("A1", cc.getName());
    });
  }

  @Test
  public void testSelectFirst2() {
    PsiMirrorElement resolved = doTest(
      """
        class A1 {
          def foo(){}
        }
        
        class A2 {
          def foo(){}
        }
        
        
        class B {
          @Delegate A2 a2
          @Delegate A1 a1
        }
        
        new B().fo<caret>o()
        """);

    ReadAction.runBlocking(() -> {
      PsiMethod prototype = (PsiMethod)resolved.getPrototype();
      PsiClass cc = prototype.getContainingClass();
      assertEquals("A2", cc.getName());
    });
  }

  @Test
  public void testSelectFirst3() {
    PsiMirrorElement resolved = doTest(
      """
        class A1 {
          def foo(){}
        }
        
        class A2 {
          def foo(){}
        }
        
        
        class B {
          @Delegate A2 bar()
          @Delegate A1 bar2()
        }
        
        new B().fo<caret>o()
        """);

    ReadAction.runBlocking(() -> {
      PsiMethod prototype = (PsiMethod)resolved.getPrototype();
      PsiClass cc = prototype.getContainingClass();
      assertEquals("A2", cc.getName());
    });
  }

  @Test
  public void testSelectFirst4() {
    PsiMirrorElement resolved = doTest(
      """
        class A1 {
          def foo(){}
        }
        
        class A2 {
          def foo(){}
        }
        
        
        class B {
          @Delegate A1 bar2()
          @Delegate A2 bar // fields are processed before methods
        }
        
        new B().fo<caret>o()
        """);

    ReadAction.runBlocking(() -> {
      PsiMethod prototype = (PsiMethod)resolved.getPrototype();
      PsiClass cc = prototype.getContainingClass();
      assertEquals("A2", cc.getName());
    });
  }

  @Test
  public void testSubstitutor1() {
    PsiMirrorElement resolved = doTest("""
                                         
                                         class Base<T> {
                                           def foo(T t){}
                                         }
                                         
                                         class A<T> extends Base<List<T>> {
                                         }
                                         
                                         
                                         class B {
                                           @Delegate A<String> a2
                                         }
                                         
                                         new B().fo<caret>o([''])
                                         """);

    ReadAction.runBlocking(() -> {
      PsiMethod method = assertInstanceOf(PsiMethod.class, resolved);
      PsiParameter[] parameters = method.getParameterList().getParameters();
      assertEquals("java.util.List<java.lang.String>", parameters[0].getType().getCanonicalText());
    });
  }

  @Test
  public void testCycle() {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    TransformationUtilKt.disableAssertOnRecursion(myFixture.getTestRootDisposable());
    GroovyFile file = (GroovyFile)myFixture.configureByText("a.groovy", """
      class A {
          def foo() {}
          @Delegate C c
      }
      
      class B {
          def test() {}
          @Delegate A a
      }
      
      class C {
          def bar(){}
          @Delegate B b
      }
      
      new A().foo()
      new B().foo()
      new C().foo()
      
      new A().bar()
      new B().bar()
      new C().bar()
      
      new A().test()
      new B().test()
      new C().test()
      """);

    ReadAction.runBlocking(() -> {
      Collection<GrStatement> result = Arrays.stream(file.getStatements())
        .filter(t -> t instanceof GrMethodCall call && call.resolveMethod() == null).toList();

      assertTrue(result.size() <= 1);
    });
  }

  @Test
  public void testJavaDelegate() {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    myFixture.addFileToProject("A.java",
                               """
                                 class Base {
                                   public void foo(){}
                                 }
                                 class A extends Base{}
                                 """);

    doTest(
      """
        class B {
          @Delegate A a
        }
        
        new B().fo<caret>o()""");
  }

  @Test
  public void testInheritanceCycle() {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    TransformationUtilKt.disableAssertOnRecursion(myFixture.getTestRootDisposable());
    GroovyFile file = (GroovyFile)myFixture.configureByText("a.groovy", """
      
      class Base {
        @Delegate A a
      }
      class A extends Base {
        void foo(){}
      }
      
      new Base().foo()
      new A().foo()""");

    ReadAction.runBlocking(() -> {
      for (GrStatement statement : file.getStatements()) {
        assertNotNull(((GrMethodCall)statement).resolveMethod());
      }
    });
  }

  @Test
  public void testCompoundHierarchy() {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    myFixture.addFileToProject("Foo.java", """
      public interface Foo {
          String getA();
      
          @Deprecated
          String getB();
      }
      """);
    myFixture.addFileToProject("Bar.java", """
      public abstract class Bar implements Foo {
          public String getA() {
              return null;
          }
      
          @Deprecated
          public String getB() {
              return null;
          }
      }
      """);
    myFixture.addFileToProject("FooBar.java", """
      
      public class FooBar extends Bar implements Foo {
      }
      """);
    assertAllMethodsImplemented("Baz.groovy", """
      class Baz {
          @Delegate(deprecated = true)
          FooBar bare
      }
      """);
  }

  private void assertAllMethodsImplemented(String fileName, String text) {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    GroovyFile file = (GroovyFile)myFixture.configureByText(fileName, text);

    ReadAction.runBlocking(() -> {
      assertNotNull(file);
      final PsiClass clazz = file.getClasses()[0];
      assertNotNull(clazz);
      assertThat(OverrideImplementExploreUtil.getMethodSignaturesToImplement(clazz)).isNullOrEmpty();
    });
  }

  @Test
  public void testDeprecatedFalse() {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    myFixture.addFileToProject("Foo.groovy",
                               """
                                 interface Foo {
                                     @Deprecated
                                     void foo()
                                 }
                                 """);
    assertAllMethodsImplemented("text.groovy",
                                """
                                  class FooImpl implements Foo {
                                      @Delegate(deprecated = false) Foo delegate
                                  }
                                  """);
  }

  @Test
  public void testDelegateWithGenerics() {
    assertAllMethodsImplemented("a.groovy",
                                """
                                  class MyClass {
                                      @Delegate
                                      HashMap<String, Integer> map = new HashMap<String, Integer>()
                                  }
                                  """);
  }

  @Test
  public void testDelegateMethodWithGenerics() {
    assertAllMethodsImplemented("a.groovy",
                                """
                                  class MyClass {
                                      @Delegate
                                      HashMap<String, Integer> getMap() {return new HashMap<>}
                                  }
                                  """);
  }
}
