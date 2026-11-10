// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.StaticallyImportable;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Maxim.Medvedev
 */
@TestApplication
public class GroovyClassNameCompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "groovy/completion/classNameCompletion");
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addClassToProject("a", "FooBar");
    myFixture.configureByFile(myFixture.getTestName(false) + ".groovy");
    complete();
    myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.groovy");
  }

  private void addClassToProject(@Nullable String packageName, @NotNull String name) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package " + packageName + "; public class " + name + " {}");
  }

  @Test
  public void testInFieldDeclaration() { doTest(); }

  @Test
  public void testInFieldDeclarationNoModifiers() { doTest(); }

  @Test
  public void testInParameter() { doTest(); }

  @Test
  public void testInImport() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addClassToProject("a", "FooBar");
    myFixture.configureByFile(myFixture.getTestName(false) + ".groovy");
    complete();
    myFixture.type("\n");
    myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.groovy");
  }

  @Test
  public void testInModuleImport() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.groovy", "import module j<caret>");
    complete();
    myFixture.type("\n");
    myFixture.checkResult("import module org.jetbrains.annotations");
  }

  @Test
  public void testWhenClassExistsInSamePackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addClassToProject("a", "FooBar");
    myFixture.configureByFile(myFixture.getTestName(false) + ".groovy");
    complete();
    LookupEx lookup = LookupManager.getActiveLookup(myFixture.getEditor());
    EdtTestUtil.runInEdtAndWait(() -> lookup.setCurrentItem(lookup.getItems().get(1)));
    myFixture.type("\n");
    myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.groovy");
  }

  @Test
  public void testInComment() { doTest(); }

  @Test
  public void testInTypeElementPlace() { doTest(); }

  @Test
  public void testWhenImportExists() { doTest(); }

  @Test
  public void testFinishByDot() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addClassToProject("a", "FooBar");
    myFixture.configureByText("a.groovy", "FB<caret>a");
    complete();
    myFixture.type(".");
    myFixture.checkResult("""
                            import a.FooBar
                            
                            FooBar.<caret>a""");
  }

  private LookupElement[] complete() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    return myFixture.complete(CompletionType.BASIC, 2);
  }

  @Test
  public void testDelegateBasicToClassName() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addClassToProject("a", "FooBarGooDoo");
    myFixture.configureByText("a.groovy", "FBGD<caret>a");
    myFixture.complete(CompletionType.BASIC, 2);
    myFixture.type(".");
    myFixture.checkResult("""
                            import a.FooBarGooDoo
                            
                            FooBarGooDoo.<caret>a""");
  }

  @Test
  public void testDelegateBasicToClassNameAutoinsert() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addClassToProject("a", "FooBarGooDoo");
    myFixture.configureByText("a.groovy", "FBGD<caret>");
    myFixture.complete(CompletionType.BASIC, 2);
    myFixture.checkResult("""
                            import a.FooBarGooDoo
                            
                            FooBarGooDoo<caret>""");
  }

  @Test
  public void testImportedStaticMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("b.groovy", """
    
      class Foo {
        static def abcmethod1(int a) {}
        static def abcmethod2(int a) {}
      }""");
    myFixture.configureByText("a.groovy", """
      def foo() {
        abcme<caret>
      }""");
    LookupElement item = complete()[0];

    LookupElementPresentation presentation = renderElement(item);
    assertEquals("Foo.abcmethod1", presentation.getItemText());
    assertEquals("(int a)", presentation.getTailText());

    ((StaticallyImportable)item).setShouldBeImported(true);
    myFixture.type("\n");
    myFixture.checkResult("""
                            import static Foo.abcmethod1
                          
                            def foo() {
                              abcmethod1(<caret>)
                            }""");
  }

  @Test
  public void testImportedStaticField() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("b.groovy", """
      
      class Foo {
        static def abcfield1
        static def abcfield2
      }""");
    myFixture.configureByText("a.groovy", """
      def foo() {
        abcfi<caret>
      }""");
    LookupElement item = complete()[0];
    ((StaticallyImportable)item).setShouldBeImported(true);
    myFixture.type("\n");
    myFixture.checkResult("""
                            import static Foo.abcfield1
                            
                            def foo() {
                              abcfield1<caret>
                            }""");
  }

  @Test
  public void testImportedInterfaceConstant() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("b.groovy", """
      
      interface Foo {
        static def abcfield1 = 2
        static def abcfield2 = 3
      }""");
    myFixture.configureByText("a.groovy", """
      def foo() {
        abcfi<caret>
      }""");
    LookupElement item = complete()[0];
    ((StaticallyImportable)item).setShouldBeImported(true);
    myFixture.type("\n");
    myFixture.checkResult("""
                            import static Foo.abcfield1
                            
                            def foo() {
                              abcfield1<caret>
                            }""");
  }

  @Test
  public void testQualifiedStaticMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("foo/b.groovy", """
      package foo
      class Foo {
        static def abcmethod(int a) {}
      }""");
    myFixture.configureByText("a.groovy", """
      def foo() {
        abcme<caret>
      }""");
    complete();
    myFixture.checkResult("""
                            import foo.Foo
                            
                            def foo() {
                              Foo.abcmethod(<caret>)
                            }""");
  }

  @Test
  public void testQualifiedStaticMethodIfThereAreAlreadyStaticImportsFromThatClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("foo/b.groovy", """
      package foo
      class Foo {
        static def abcMethod() {}
        static def anotherMethod() {}
      }""");
    myFixture.configureByText("a.groovy", """
    
      import static foo.Foo.anotherMethod
    
      anotherMethod()
      abcme<caret>x""");
    LookupElement element = assertThat(new LookupElement[]{complete()[0]}).singleElement().actual();

    LookupElementPresentation presentation = renderElement(element);
    assertEquals("abcMethod", presentation.getItemText());
    assertEquals("() in Foo (foo)", presentation.getTailText());

    myFixture.type("\t");
    myFixture.checkResult("""
                            import static foo.Foo.abcMethod
                            import static foo.Foo.anotherMethod
                          
                            anotherMethod()
                            abcMethod()<caret>""");
  }

  private static LookupElementPresentation renderElement(LookupElement element) {
    return ReadAction.computeBlocking(() -> LookupElementPresentation.renderElement(element));
  }

  @Test
  public void testNewClassName() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addClassToProject("foo", "Fxoo");
    myFixture.configureByText("a.groovy", "new Fxo<caret>\n");
    myFixture.complete(CompletionType.BASIC, 2);
    myFixture.checkResult("""
                            import foo.Fxoo
                            
                            new Fxoo()<caret>
                            """);
  }

  @Test
  public void testNewImportedClassName() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.groovy", "new ArrayIndexOut<caret>\n");
    myFixture.completeBasic();
    myFixture.checkResult("new ArrayIndexOutOfBoundsException(<caret>)\n");
  }

  @Test
  public void testOnlyAnnotationsAfterAt() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("class AbcdClass {}; @interface AbcdAnno {}");
    myFixture.configureByText("a.groovy", "@Abcd<caret>");
    complete();
    assertEquals("AbcdAnno", myFixture.getLookupElementStrings().get(0));
  }

  @Test
  public void testOnlyExceptionsInCatch() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("class AbcdClass {}; class AbcdException extends Throwable {}");
    myFixture.configureByText("a.groovy", "try {} catch (Abcd<caret>");
    complete();
    assertEquals("AbcdException", myFixture.getLookupElementStrings().get(0));
  }

  @Test
  public void testClassNameInMultilineString() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.groovy", "def s = \"\"\"a\nAIOOBE<caret>\na\"\"\"");
    complete();
    myFixture.checkResult("def s = \"\"\"a\njava.lang.ArrayIndexOutOfBoundsException<caret>\na\"\"\"");
  }

  @Test
  public void testDoubleClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package foo; public class Zooooooo {}");
    myFixture.configureByText("a.groovy", """
      import foo.Zooooooo
      Zoooo<caret>x""");
    assertThat(myFixture.completeBasic()).hasSize(1);
  }

  @Test
  public void testClassOnlyOnce() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("class FooBarGoo {}");
    myFixture.configureByText("a.groovy", "FoBaGo<caret>");
    assertNull(complete());
    myFixture.checkResult("""
                            FooBarGoo<caret>""");
  }

  @Test
  public void testMethodFromTheSameClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.groovy", """
    
      class A {
        static void foo() {}
    
        static void goo() {
          f<caret>
        }
      }
      """);
    LookupElement[] items = complete();
    LookupElement fooItem = ContainerUtil.find(items, element -> renderElement(element).getItemText().equals("foo"));
    EdtTestUtil.runInEdtAndWait(() -> LookupManager.getActiveLookup(myFixture.getEditor()).setCurrentItem(fooItem));
    myFixture.type("\n");
    myFixture.checkResult("""
                          
                            class A {
                              static void foo() {}
                          
                              static void goo() {
                                foo()<caret>
                              }
                            }
                            """);
  }

  @Test
  public void testInnerClassCompletion() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package foo;
                         
                         public class Upper {
                           public static class Inner {}
                         }
                         """);

    myFixture.configureByText("_.groovy", """
      import foo.Upper
      print new Inner<caret>
      """);
    myFixture.complete(CompletionType.BASIC);
    myFixture.type("\n");
    myFixture.checkResult("""
                            import foo.Upper
                            print new Upper.Inner()
                            """);
  }

  @Test
  public void test_complete_class_within__in__package() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package in.foo.com;
                         public class Foooo {}
                         """);
    myFixture.configureByText("_.groovy", "Fooo<caret>");
    myFixture.complete(CompletionType.BASIC);
    myFixture.type("\n");
    myFixture.checkResult("""
                            import in.foo.com.Foooo
                            
                            Foooo<caret>""");
  }

  @Test
  public void test_complete_class_within__def__package() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package def.foo.com;
                         public class Foooo {}
                         """);
    myFixture.configureByText("_.groovy", "Fooo<caret>");
    myFixture.complete(CompletionType.BASIC);
    myFixture.type("\n");
    myFixture.checkResult("""
                            import def.foo.com.Foooo
                            
                            Foooo<caret>""");
  }

  @Test
  public void test_complete_package_name() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package com.foo.bar; class C {}");
    myFixture.configureByText("_.groovy", "import com.<caret>\n");
    myFixture.complete(CompletionType.BASIC);
    myFixture.type("\t");
    myFixture.checkResult("import com.foo<caret>\n");
  }
}