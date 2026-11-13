// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.StaticallyImportable;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;

import java.util.List;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.completion.GroovyCompletionFixtureKt.groovyCompletionFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.addCompileStatic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class GroovySmartCompletionTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture();
  private final TestFixture<GroovyCompletionFixture> completion = groovyCompletionFixture(project, TestUtils.getAbsoluteTestDataPath() + "groovy/completion/smart");

  @Test
  public void testSmartCompletionAfterNewInDeclaration() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    myFixture.configureByFile(myFixture.getTestName(false) + ".groovy");
    myFixture.complete(CompletionType.SMART);
    runInEdtAndWait(() -> assertEquals(List.of("Bar", "Foo"), myFixture.getLookupElementStrings()));
  }

  @Test
  public void testCaretAfterSmartCompletionAfterNewInDeclaration() {
    completion.get().doSmartTest();
  }

  @Test
  public void testSmartCompletionAfterNewInDeclarationWithArray() {
    completion.get().doSmartTest();
  }

  @Test
  public void testSmartCompletionAfterNewInDeclarationWithIntArray() {
    completion.get().doSmartTest();
  }

  @Test
  public void testShortenNamesInSmartCompletionAfterNewInDeclaration() {
    completion.get().doSmartTest();
  }

  @Test
  public void testSmartAfterNewInCall() {
    completion.get().doSmartTest();
  }

  @Test
  public void testInnerClassInStaticMethodCompletion() {
    completion.get().doVariantableTest(null, "", CompletionType.SMART, CompletionResult.notContain, "Inner");
  }

  @Test
  public void testSmartCompletionInAssignmentExpression() {
    completion.get().doSmartTest();
  }

  @Test
  public void testSimpleMethodParameter() {
    completion.get().doSmartCompletion("d1", "d2");
  }

  @Test
  public void testReturnStatement() {
    completion.get().doSmartCompletion("b", "b1", "b2", "foo");
  }

  @Test
  public void testIncSmartCompletion() {
    completion.get().doSmartCompletion("a", "b");
  }

  @Test
  public void testInheritConstructorsAnnotation() {
    completion.get().getFixture().addFileToProject("groovy/transform/InheritConstructors.java", """
      package groovy.transform;
      
      import java.lang.annotation.ElementType;
      import java.lang.annotation.Retention;
      import java.lang.annotation.RetentionPolicy;
      import java.lang.annotation.Target;@Retention(RetentionPolicy.SOURCE)
      @Target({ElementType.TYPE})
      public @interface InheritConstructors {
      }""");
    completion.get().doSmartTest();
  }

  @Test
  public void testSmartCastCompletion() {
    completion.get().doSmartTest();
  }

  @Test
  public void testSmartCastCompletionWithoutRParenth() {
    completion.get().doSmartTest();
  }

  @Test
  public void testSmartCastCompletionWithRParenth() {
    completion.get().doSmartTest();
  }

  @Test
  public void testDontCompletePrivateMembers() {
    completion.get().doSmartCompletion("foo1", "foo2", "getFoo1", "getFoo2");
  }

  @Test
  public void testEnumMembersInAssignment() {
    completion.get().doSmartCompletion("IN_STOCK", "NOWHERE", "ORDERED", "valueOf");
  }

  @Test
  public void testEnumMembersInAssignmentInsideEnum() {
    completion.get().doSmartCompletion("IN_STOCK", "NOWHERE", "ORDERED", "next", "previous", "valueOf");
  }

  @Test
  public void testEnumMembersInComparison() {
    completion.get().doSmartCompletion("status", "IN_STOCK", "NOWHERE", "ORDERED", "valueOf");
  }

  @Test
  public void testPreferVarargElement() {
    completion.get().doCompletionTest(null, null, "\n", CompletionType.SMART);
  }

  @Test
  public void testGlobalStaticMembers() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    myFixture.configureByFile(myFixture.getTestName(false) + ".groovy");
    myFixture.complete(CompletionType.SMART, 2);
    runInEdtAndWait(() -> assertEquals(List.of("SUBSTRING", "createExpected", "createSubGeneric", "SUB_RAW"), myFixture.getLookupElementStrings()));
  }

  @Test
  public void testGlobalStaticMembersForString() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    myFixture.configureByText("a.groovy", """
      'class Foo {
        public static String string(){}
        public static Comparator<String> comparator(){}
        public static String foo(){}
      }
      
      String s = <caret>
      """);

    myFixture.complete(CompletionType.SMART, 2);
    runInEdtAndWait(() -> assertEquals(List.of("foo", "string"), myFixture.getLookupElementStrings()));
  }

  @Test
  public void testGlobalListCreators() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    myFixture.configureByFile(myFixture.getTestName(false) + ".groovy");
    myFixture.complete(CompletionType.SMART, 2);
    runInEdtAndWait(() -> assertEquals(List.of("createGenericList", "createStringList"), myFixture.getLookupElementStrings()));
  }

  @Test
  public void testNativeList() {
    completion.get().doSmartCompletion("a1", "a2");
  }

  @Test
  public void testMembersImportStatically() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    myFixture.addClass("""
                         class Expected {
                           public static final Expected fooField;
                           public static Expected fooMethod() {}
                         }
                         """);
    myFixture.configureByText("a.groovy", "Expected exp = fo<caret>");
    LookupElement[] items = myFixture.complete(CompletionType.SMART);
    runInEdtAndWait(() -> {
      assertEquals(List.of("fooField", "fooMethod"), myFixture.getLookupElementStrings());
      assertTrue(items[0].as(StaticallyImportable.CLASS_CONDITION_KEY).canBeImported());
      assertTrue(items[1].as(StaticallyImportable.CLASS_CONDITION_KEY).canBeImported());
      items[0].as(StaticallyImportable.CLASS_CONDITION_KEY).setShouldBeImported(true);
    });
    myFixture.type("\n");
    myFixture.checkResult("""
                            import static Expected.fooField
                            
                            Expected exp = fooField""");
  }

  @Test
  public void testThrow() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    myFixture.configureByText("_a.groovy", """
      throw new RunEx<caret>
      """);
    myFixture.complete(CompletionType.SMART);
    myFixture.checkResult("""
                            throw new RuntimeException()
                            """);
  }

  @Test
  public void testInnerClassReferenceWithoutQualifier() {
    completion.get().doSmartTest();
  }

  @Test
  public void testAnonymousClassCompletion() {
    completion.get().doSmartTest("""
                  Runnable r = new Run<caret>
                  """, """
                  Runnable r = new Runnable() {
                      @Override
                      void run() {<caret><selection></selection>
                  
                      }
                  }
                  """);
  }

  @Test
  public void testReassignedVar() {
    completion.get().doSmartTest("""
                  def foo(int x){}
                  def aaaa = '123'
                  aaaa = 123
                  
                  foo(aaa<caret>)
                  """, """
                  def foo(int x){}
                  def aaaa = '123'
                  aaaa = 123
                  
                  foo(aaaa<caret>)
                  """);
  }

  @Test
  public void testFieldWithInstanceof() {
    addCompileStatic(completion.get().getFixture());
    completion.get().doSmartTest("""
                  @CompileStatic
                  class A {
                    def field
                    def foo(int x) {}
                    def bar() {
                      if (field instanceof Integer) {
                        foo(fiel<caret>)
                      }
                    }
                  }
                  """, """
                  @CompileStatic
                  class A {
                    def field
                    def foo(int x) {}
                    def bar() {
                      if (field instanceof Integer) {
                        foo(field)
                      }
                    }
                  }
                  """);
  }

  @Test
  public void testFieldWithAssignment() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    addCompileStatic(myFixture);
    myFixture.configureByText("a.groovy", """
      import groovy.transform.CompileStatic
      @CompileStatic
      class A {
        def field
        def foo(int x) {}
       \s
        def bar() {
          field = 1
          foo(fiel<caret>)
        }
      }
      """);

    myFixture.complete(CompletionType.SMART);
    runInEdtAndWait(() -> Assertions.assertEquals(List.of(), myFixture.getLookupElementStrings()));
  }

  @Test
  public void testQualifiedNameAfterNew() {
    completion.get().getFixture().addClass("""
                         package foo;
                         public class User<T> {}
                         """);
    completion.get().doSmartTest(""" 
                  class User {
                  }
                  def a(foo.User<String> f){}
                  
                  a(new Us<caret>)
                  """, """
                  class User {
                  }
                  def a(foo.User<String> f){}
                  
                  a(new foo.User<String>()<caret>)
                  """);
  }

  @Test
  public void testBinaryExpr() {
    completion.get().doSmartTest("""
                  class A {
                    def plus(String s) {}
                  }
                  
                  print new A() + new <caret>
                  """, """
                  class A {
                    def plus(String s) {}
                  }
                  
                  print new A() + new String(<caret>)
                  """);
  }
}
