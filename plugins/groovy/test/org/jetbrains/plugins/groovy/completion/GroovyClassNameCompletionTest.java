// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.StaticallyImportable;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author Maxim.Medvedev
 */
public class GroovyClassNameCompletionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/completion/classNameCompletion";
  }

  private void doTest() {
    addClassToProject("a", "FooBar");
    myFixture.configureByFile(getTestName(false) + ".groovy");
    complete();
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  private void addClassToProject(@Nullable String packageName, @NotNull String name) {
    myFixture.addClass("package " + packageName + "; public class " + name + " {}");
  }

  public void testInFieldDeclaration() { doTest(); }

  public void testInFieldDeclarationNoModifiers() { doTest(); }

  public void testInParameter() { doTest(); }

  public void testInImport() {
    addClassToProject("a", "FooBar");
    myFixture.configureByFile(getTestName(false) + ".groovy");
    complete();
    myFixture.type("\n");
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  public void testWhenClassExistsInSamePackage() {
    addClassToProject("a", "FooBar");
    myFixture.configureByFile(getTestName(false) + ".groovy");
    complete();
    LookupEx lookup = LookupManager.getActiveLookup(myFixture.getEditor());
    lookup.setCurrentItem(lookup.getItems().get(1));
    myFixture.type("\n");
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  public void testInComment() { doTest(); }

  public void testInTypeElementPlace() { doTest(); }

  public void testWhenImportExists() { doTest(); }

  public void testFinishByDot() {
    addClassToProject("a", "FooBar");
    myFixture.configureByText("a.groovy", "FB<caret>a");
    complete();
    myFixture.type(".");
    myFixture.checkResult("""
                            import a.FooBar
                            
                            FooBar.<caret>a""");
  }

  private LookupElement[] complete() {
    return myFixture.complete(CompletionType.BASIC, 2);
  }

  public void testDelegateBasicToClassName() {
    addClassToProject("a", "FooBarGooDoo");
    myFixture.configureByText("a.groovy", "FBGD<caret>a");
    myFixture.complete(CompletionType.BASIC, 2);
    myFixture.type(".");
    myFixture.checkResult("""
                            import a.FooBarGooDoo
                            
                            FooBarGooDoo.<caret>a""");
  }

  public void testDelegateBasicToClassNameAutoinsert() {
    addClassToProject("a", "FooBarGooDoo");
    myFixture.configureByText("a.groovy", "FBGD<caret>");
    myFixture.complete(CompletionType.BASIC, 2);
    myFixture.checkResult("""
                            import a.FooBarGooDoo
                            
                            FooBarGooDoo<caret>""");
  }

  public void testImportedStaticMethod() {
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

  public void testImportedStaticField() {
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

  public void testImportedInterfaceConstant() {
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

  public void testQualifiedStaticMethod() {
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

  public void testQualifiedStaticMethodIfThereAreAlreadyStaticImportsFromThatClass() {
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
    LookupElement element = UsefulTestCase.assertOneElement(new LookupElement[]{complete()[0]});

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
    return LookupElementPresentation.renderElement(element);
  }

  public void testNewClassName() {
    addClassToProject("foo", "Fxoo");
    myFixture.configureByText("a.groovy", "new Fxo<caret>\n");
    myFixture.complete(CompletionType.BASIC, 2);
    myFixture.checkResult("""
                            import foo.Fxoo
                            
                            new Fxoo()<caret>
                            """);
  }

  public void testNewImportedClassName() {
    myFixture.configureByText("a.groovy", "new ArrayIndexOut<caret>\n");
    myFixture.completeBasic();
    myFixture.checkResult("new ArrayIndexOutOfBoundsException(<caret>)\n");
  }

  public void testOnlyAnnotationsAfterAt() {
    myFixture.addClass("class AbcdClass {}; @interface AbcdAnno {}");
    myFixture.configureByText("a.groovy", "@Abcd<caret>");
    complete();
    assertEquals("AbcdAnno", myFixture.getLookupElementStrings().get(0));
  }

  public void testOnlyExceptionsInCatch() {
    myFixture.addClass("class AbcdClass {}; class AbcdException extends Throwable {}");
    myFixture.configureByText("a.groovy", "try {} catch (Abcd<caret>");
    complete();
    assertEquals("AbcdException", myFixture.getLookupElementStrings().get(0));
  }

  public void testClassNameInMultilineString() {
    myFixture.configureByText("a.groovy", "def s = \"\"\"a\nAIOOBE<caret>\na\"\"\"");
    complete();
    myFixture.checkResult("def s = \"\"\"a\njava.lang.ArrayIndexOutOfBoundsException<caret>\na\"\"\"");
  }

  public void testDoubleClass() {
    myFixture.addClass("package foo; public class Zooooooo {}");
    myFixture.configureByText("a.groovy", """
      import foo.Zooooooo
      Zoooo<caret>x""");
    UsefulTestCase.assertOneElement(myFixture.completeBasic());
  }

  public void testClassOnlyOnce() {
    myFixture.addClass("class FooBarGoo {}");
    myFixture.configureByText("a.groovy", "FoBaGo<caret>");
    assertNull(complete());
    myFixture.checkResult("""
                            FooBarGoo<caret>""");
  }

  public void testMethodFromTheSameClass() {
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
    LookupManager.getActiveLookup(myFixture.getEditor()).setCurrentItem(fooItem);
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

  public void testInnerClassCompletion() {
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

  public void test_complete_class_within__in__package() {
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

  public void test_complete_class_within__def__package() {
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

  public void test_complete_package_name() {
    myFixture.addClass("package com.foo.bar; class C {}");
    myFixture.configureByText("_.groovy", "import com.<caret>\n");
    myFixture.complete(CompletionType.BASIC);
    myFixture.type("\t");
    myFixture.checkResult("import com.foo<caret>\n");
  }
}