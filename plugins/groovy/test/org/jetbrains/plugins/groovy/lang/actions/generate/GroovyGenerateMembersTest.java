// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.actions.generate;

import com.intellij.codeInsight.generation.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.actions.generate.constructors.GroovyGenerateConstructorHandler;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.ArrayList;
import java.util.List;

public class GroovyGenerateMembersTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "generate";
  }

  public void testConstructorAtOffset() {
    doConstructorTest();
  }

  public void testConstructorAtEnd() {
    doConstructorTest();
  }

  public void testLonelyConstructor() {
    doConstructorTest();
  }

  public void testConstructorInJavaInheritor() {
    myFixture.configureByText("GrBase.groovy", """
      
      abstract class GrBase {
          GrBase(int i) { }
      }
      """);
    myFixture.configureByText("Inheritor.java", """
      
      class Inheritor extends GrBase {
          <caret>
      }
      """);
    generateConstructor(true);
    myFixture.checkResult("""
                            
                            class Inheritor extends GrBase {
                                public Inheritor(int i) {
                                    super(i);
                                }
                            }
                            """);
  }

  public void testExplicitArgumentTypes() {
    myFixture.configureByText("a.groovy", """
      
      class Super {
        def Super(a, int b) {}
      }
      
      class Foo extends Super {
        int c
        Object d
        final e
        <caret>
      }
      """);
    generateConstructor();
    myFixture.checkResult("""
                            
                            class Super {
                              def Super(a, int b) {}
                            }
                            
                            class Foo extends Super {
                              int c
                              Object d
                              final e
                            
                                Foo(a, int b, int c, Object d, e) {
                                    super(a, b)
                                    this.c = c
                                    this.d = d
                                    this.e = e
                                }
                            }
                            """);
  }

  public void testSubstitutionInConstructor() {
    myFixture.configureByText("a.groovy", """
      
      class Super<E> {
        def Super(Collection<E> c) {}
      }
      
      class X {}
      
      class Foo extends Super<X> {
        <caret>
      }
      """);
    generateConstructor();
    myFixture.checkResult("""
                            
                            class Super<E> {
                              def Super(Collection<E> c) {}
                            }
                            
                            class X {}
                            
                            class Foo extends Super<X> {
                                Foo(Collection<X> c) {
                                    super(c)
                                }
                            }
                            """);
  }

  public void testGetter1() {
    myFixture.configureByText("a.groovy", """
      
      class Test {
          def foo
          <caret>
      }""");
    generateGetter();

    myFixture.checkResult("""
                            
                            class Test {
                                def foo
                            
                                def getFoo() {
                                    return foo
                                }
                            }""");
  }

  public void testGetter2() {
    myFixture.configureByText("a.groovy", """
      
        class Test {
            int foo
            <caret>
        }\
      """);
    generateGetter();

    myFixture.checkResult("""
                            
                              class Test {
                                  int foo
                            
                                  int getFoo() {
                                      return foo
                                  }
                              }\
                            """);
  }

  public void testGetter3() {
    myFixture.configureByText("a.groovy", """
      
        class Test {
            static foo
            <caret>
        }\
      """);
    generateGetter();

    myFixture.checkResult("""
                            
                              class Test {
                                  static foo
                            
                                  static getFoo() {
                                      return foo
                                  }
                              }\
                            """);
  }

  public void testGetter4() {
    myFixture.addFileToProject("org/jetbrains/annotations/Nullable.java",
                               "package org.jetbrains.annotations; public @interface Nullable {}");

    myFixture.configureByText("a.groovy", """
      
        import org.jetbrains.annotations.Nullable
      
        class Test {
            @Nullable
            def foo
            <caret>
        }\
      """);
    generateGetter();

    myFixture.checkResult("""
                            
                              import org.jetbrains.annotations.Nullable
                            
                              class Test {
                                  @Nullable
                                  def foo
                            
                                  @Nullable
                                  getFoo() {
                                      return foo
                                  }
                              }\
                            """);
  }

  public void testSetter1() {
    myFixture.configureByText("a.groovy", """
      
      class Test {
          def foo
          <caret>
      }""");

    generateSetter();

    myFixture.checkResult("""
                            
                            class Test {
                                def foo
                            
                                void setFoo(foo) {
                                    this.foo = foo
                                }
                            }""");
  }

  public void testSetter2() {
    myFixture.configureByText("a.groovy", """
      
      class Test {
          int foo
          <caret>
      }""");

    generateSetter();

    myFixture.checkResult("""
                            
                            class Test {
                                int foo
                            
                                void setFoo(int foo) {
                                    this.foo = foo
                                }
                            }""");
  }

  public void testSetter3() {
    myFixture.configureByText("a.groovy", """
      
      class Test {
          static foo
          <caret>
      }""");

    generateSetter();

    myFixture.checkResult("""
                            
                            class Test {
                                static foo
                            
                                static void setFoo(foo) {
                                    Test.foo = foo
                                }
                            }""");
  }

  public void testSetter4() {
    myFixture.addFileToProject("org/jetbrains/annotations/Nullable.java",
                               "package org.jetbrains.annotations; public @interface Nullable {}");

    myFixture.configureByText("a.groovy", """
      
      import org.jetbrains.annotations.Nullable
      
      class Test {
          @Nullable
          def foo
          <caret>
      }""");

    generateSetter();

    myFixture.checkResult("""
                            
                            import org.jetbrains.annotations.Nullable
                            
                            class Test {
                                @Nullable
                                def foo
                            
                                void setFoo(@Nullable foo) {
                                    this.foo = foo
                                }
                            }""");
  }

  public void testConstructorInTheMiddle() {
    doConstructorTest("""
                        class Foo {
                            def foo() {}
                        
                            <caret>
                        
                            def bar() {}
                        }
                        """, """
                        class Foo {
                            def foo() {}
                        
                            Foo() {
                            }
                        
                            def bar() {}
                        }
                        """);
  }

  public void testConstructorWithOptionalParameter() {
    doConstructorTest("""
                        class Base {
                          Base(int x = 0){}
                        }
                        
                        class Inheritor extends Base {
                          <caret>
                        }
                        """, """
                        class Base {
                          Base(int x = 0){}
                        }
                        
                        class Inheritor extends Base {
                            Inheritor(int x) {
                                super(x)
                            }
                        }
                        """);
  }

  public void testGetterInTheEnd() {
    myFixture.configureByText("a.groovy", """
      
      class GrImportStatementStub {
          private final String myAlias;
          private final String mySymbolName;
      
          protected GrImportStatementStub(String symbolName, String alias) {
          }
          <caret>
      }
      """);
    generateGetter();

    myFixture.checkResult("""
                            
                            class GrImportStatementStub {
                                private final String myAlias;
                                private final String mySymbolName;
                            
                                protected GrImportStatementStub(String symbolName, String alias) {
                                }
                            
                                String getMyAlias() {
                                    return myAlias
                                }
                            
                                String getMySymbolName() {
                                    return mySymbolName
                                }
                            }
                            """);
  }

  private void generateGetter() {
    new GenerateGetterHandler() {
      @Override
      protected ClassMember[] chooseMembers(ClassMember[] members,
                                            boolean allowEmptySelection,
                                            boolean copyJavadocCheckbox,
                                            Project project,
                                            Editor editor) {
        return members;
      }
    }.invoke(getProject(), myFixture.getEditor(), myFixture.getFile());
  }

  private void generateSetter() {
    new GenerateSetterHandler() {
      @Override
      protected ClassMember[] chooseMembers(ClassMember[] members,
                                            boolean allowEmptySelection,
                                            boolean copyJavadocCheckbox,
                                            Project project,
                                            Editor editor) {
        return members;
      }
    }.invoke(getProject(), myFixture.getEditor(), myFixture.getFile());
  }

  private void doConstructorTest(String before, String after) {
    if (before != null) {
      myFixture.configureByText("_a.groovy", before);
    }
    else {
      myFixture.configureByFile(getTestName(false) + ".groovy");
    }

    generateConstructor();
    if (after != null) {
      myFixture.checkResult(after);
    }
    else {
      myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
    }
  }

  private void doConstructorTest() {
    doConstructorTest(null, null);
  }

  private void generateConstructor(boolean javaHandler) {
    GenerateMembersHandlerBase handler;
    if (javaHandler) {
      handler = new GenerateConstructorHandler() {
        @Override
        protected ClassMember[] chooseMembers(ClassMember[] members,
                                              boolean allowEmptySelection,
                                              boolean copyJavadocCheckbox,
                                              Project project,
                                              Editor editor) {
          return members;
        }
      };
    }
    else {
      handler = new GroovyGenerateConstructorHandler() {
        @Override
        protected ClassMember[] chooseOriginalMembersImpl(PsiClass aClass, Project project) {
          List<ClassMember> members = new ArrayList<>(aClass.getFields().length + 1);
          for (PsiField field : aClass.getFields()) {
            members.add(new PsiFieldMember(field));
          }
          members.add(new PsiMethodMember(aClass.getSuperClass().getConstructors()[0]));
          return members.toArray(ClassMember.EMPTY_ARRAY);
        }
      };
    }


    handler.invoke(getProject(), myFixture.getEditor(), myFixture.getFile());
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
  }

  private void generateConstructor() {
    generateConstructor(false);
  }
}
