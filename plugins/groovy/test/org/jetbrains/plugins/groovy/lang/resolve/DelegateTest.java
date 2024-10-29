// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.psi.*;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.transformations.TransformationUtilKt;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.Assert;

import java.util.Arrays;
import java.util.Collection;

/**
 * @author Max Medvedev
 */
public class DelegateTest extends GroovyResolveTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "resolve/delegate/";
  }

  private <T> T doTest(String text, Class<T> clazz) {
    PsiReference ref = configureByText(text);
    PsiElement resolved = ref.resolve();

    if (clazz != null) {
      Assert.assertNotNull(resolved);
      return UsefulTestCase.assertInstanceOf(resolved, clazz);
    }
    else {
      Assert.assertNull(resolved);
    }

    return null;
  }

  private PsiMirrorElement doTest(String text) {
    return doTest(text, PsiMirrorElement.class);
  }

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

    PsiMethod prototype = (PsiMethod)resolved.getPrototype();
    PsiClass cc = prototype.getContainingClass();
    Assert.assertEquals("A1", cc.getName());
  }

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

    PsiMethod prototype = (PsiMethod)resolved.getPrototype();
    PsiClass cc = prototype.getContainingClass();
    Assert.assertEquals("A2", cc.getName());
  }

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

    PsiMethod prototype = (PsiMethod)resolved.getPrototype();
    PsiClass cc = prototype.getContainingClass();
    Assert.assertEquals("A2", cc.getName());
  }

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

    PsiMethod prototype = (PsiMethod)resolved.getPrototype();
    PsiClass cc = prototype.getContainingClass();
    Assert.assertEquals("A2", cc.getName());
  }

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

    PsiMethod method = UsefulTestCase.assertInstanceOf(resolved, PsiMethod.class);
    PsiParameter[] parameters = method.getParameterList().getParameters();
    Assert.assertEquals("java.util.List<java.lang.String>", parameters[0].getType().getCanonicalText());
  }

  public void testCycle() {
    TransformationUtilKt.disableAssertOnRecursion(getTestRootDisposable());
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

    Collection<GrStatement> result = Arrays.stream(file.getStatements())
      .filter(t -> t instanceof GrMethodCall call && call.resolveMethod() == null).toList();

    Assert.assertTrue(result.size() <= 1);
  }

  public void testJavaDelegate() {
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

  public void testInheritanceCycle() {
    TransformationUtilKt.disableAssertOnRecursion(getTestRootDisposable());
    GroovyFile file = (GroovyFile)myFixture.configureByText("a.groovy", """
      
      class Base {
        @Delegate A a
      }
      class A extends Base {
        void foo(){}
      }
      
      new Base().foo()
      new A().foo()""");

    for (GrStatement statement : file.getStatements()) {
      Assert.assertNotNull(((GrMethodCall)statement).resolveMethod());
    }
  }

  public void testCompoundHierarchy() {
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
    GroovyFile file = (GroovyFile)myFixture.configureByText(fileName, text);

    Assert.assertNotNull(file);
    final PsiClass clazz = file.getClasses()[0];
    Assert.assertNotNull(clazz);
    UsefulTestCase.assertEmpty(OverrideImplementExploreUtil.getMethodSignaturesToImplement(clazz));
  }

  public void testDeprecatedFalse() {
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

  public void testDelegateWithGenerics() {
    assertAllMethodsImplemented("a.groovy",
                                """
                                  class MyClass {
                                      @Delegate
                                      HashMap<String, Integer> map = new HashMap<String, Integer>()
                                  }
                                  """);
  }

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
