// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.overriding;

import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import java.util.List;

public class GroovyOverrideImplementTest extends LightGroovyTestCase {
  public void testInEmptyBraces() throws Exception {
    myFixture.configureByText("a.groovy", """
      class Test {<caret>}
      """);
    generateImplementation(findMethod(Object.class.getName(), "equals"));
    myFixture.checkResult("""
                            class Test {
                                @Override
                                boolean equals(Object obj) {
                                    return super.equals(obj)
                                }
                            }
                            """);
  }

  public void testConstructor() throws Exception {
    myFixture.configureByText("a.groovy", """
      class Test {<caret>}
      """);
    generateImplementation(findMethod(Object.class.getName(), "Object"));
    myFixture.checkResult("""
                            class Test {
                                Test() {
                                    super()
                                }
                            }
                            """);
  }

  public void testNoSuperReturnType() throws Exception {
    myFixture.addFileToProject("Foo.groovy", """
      class Foo {
        def foo() {
          true
        }
      }
      """);

    myFixture.configureByText("a.groovy", """
      class Test {<caret>}      
      """);
    generateImplementation(findMethod("Foo", "foo"));
    myFixture.checkResult(""" 
                            class Test {
                                @Override
                                def foo() {
                                    return super.foo()
                                }
                            }
                            """);
  }

  public void testMethodTypeParameters() {
    myFixture.addFileToProject("v.java", """
      class Base<E> {
        public <T> T[] toArray(T[] t) {return (T[])new Object[0];}
      }      
      """);
    myFixture.configureByText("a.groovy", """
      class Test<T> extends Base<T> {<caret>}
      """);
    generateImplementation(findMethod("Base", "toArray"));
    myFixture.checkResult("""
                            class Test<T> extends Base<T> {
                                @Override
                                def <T1> T1[] toArray(T1[] t) {
                                    return super.toArray(t)
                                }
                            }
                            """);
  }

  public void testThrowsList() {
    assertImplement("""
                      class X implements I {
                          <caret>
                      }
                      
                      interface I {
                          void foo() throws RuntimeException
                      }
                      """, "I", "foo", """
                      class X implements I {
                      
                          @Override
                          void foo() throws RuntimeException {
                      
                          }
                      }
                      
                      interface I {
                          void foo() throws RuntimeException
                      }
                      """);
  }

  private void assertImplement(String textBefore, String clazz, String name, String textAfter) {
    myFixture.configureByText("a.groovy", textBefore);
    generateImplementation(findMethod(clazz, name));
    myFixture.checkResult(textAfter);
  }

  public void testThrowsListWithImport() {
    myFixture.addClass("""
                         package pack;
                         public class Exc extends RuntimeException {}
                         """);

    myFixture.addClass("""
                         import pack.Exc;
                         
                         interface I {
                             void foo() throws Exc;
                         }
                         """);

    myFixture.configureByText("a.groovy", """
      class X implements I {
          <caret>
      }
      """);

    generateImplementation(findMethod("I", "foo"));

    myFixture.checkResult("""
                            import pack.Exc
                            
                            class X implements I {
                            
                                @Override
                                void foo() throws Exc {
                            
                                }
                            }
                            """);
  }

  public void testNullableParameter() {
    myFixture.addClass("""
                         package org.jetbrains.annotations;
                         public @interface Nullable{}                         
                         """);

    assertImplement("""
                      import org.jetbrains.annotations.Nullable
                      
                      class Inheritor implements I {
                        <caret>
                      }
                      
                      interface I {
                        def foo(@Nullable p)
                      }
                      """, "I", "foo", """
                      import org.jetbrains.annotations.Nullable
                      
                      class Inheritor implements I {
                      
                          @Override
                          def foo(@Nullable Object p) {
                              <caret>return null
                          }
                      }
                      
                      interface I {
                        def foo(@Nullable p)
                      }
                      """);
  }

  public void _testImplementIntention() {
    myFixture.configureByText("a.groovy", """
      class Base<E> {
        public <E> E fo<caret>o(E e){}
      }
      
      class Test extends Base<String> {
      }
      """);

    List<IntentionAction> fixes = myFixture.getAvailableIntentions();
    UsefulTestCase.assertSize(1, fixes);

    IntentionAction fix = fixes.get(0);
    fix.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
  }

  public void test_abstract_final_trait_properties() {
    myFixture.addFileToProject("T.groovy", """
      trait T {
        abstract foo
        abstract final bar
      }
      """);
    myFixture.configureByText("classes.groovy", """
      class <caret>A implements T {
      }
      """);
    myFixture.launchAction(myFixture.findSingleIntention("Implement methods"));
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    myFixture.checkResult("""
                            class A implements T {
                                @Override
                                Object getFoo() {
                                    return null
                                }
                            
                                @Override
                                void setFoo(Object foo) {
                            
                                }
                            
                                @Override
                                Object getBar() {
                                    return null
                                }
                            }
                            """);
  }

  private void generateImplementation(final PsiMethod method) {
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      GrTypeDefinition clazz = (GrTypeDefinition)((PsiClassOwner)myFixture.getFile()).getClasses()[0];
      OverrideImplementUtil.overrideOrImplement(clazz, method);
      PostprocessReformattingAspect.getInstance(myFixture.getProject()).doPostponedFormatting();
    });
    myFixture.getEditor().getSelectionModel().removeSelection();
  }

  public PsiMethod findMethod(String className, String methodName) {
    return JavaPsiFacade.getInstance(getProject()).findClass(className, GlobalSearchScope.allScope(getProject()))
      .findMethodsByName(methodName, false)[0];
  }
}
