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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.completion.GroovyCompletionFixture.groovyCompletionFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.addCompileStatic;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class GroovySmartCompletionTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = groovyFixture(project, "groovy/completion/smart");
  private final TestFixture<GroovyCompletionTestFixture> completion = groovyCompletionFixture(groovy);

  @Test
  public void testSmartCompletionAfterNewInDeclaration() {
    groovy.get().configureByFile(groovy.get().getTestName(false) + ".groovy");
    groovy.get().complete(CompletionType.SMART);
    runInEdtAndWait(() -> assertEquals(List.of("Bar", "Foo"), groovy.get().getLookupElementStrings()));
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
    groovy.get().addFileToProject("groovy/transform/InheritConstructors.java", """
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
    groovy.get().configureByFile(groovy.get().getTestName(false) + ".groovy");
    groovy.get().complete(CompletionType.SMART, 2);
    runInEdtAndWait(() -> assertEquals(List.of("SUBSTRING", "createExpected", "createSubGeneric", "SUB_RAW"), groovy.get().getLookupElementStrings()));
  }

  @Test
  public void testGlobalStaticMembersForString() {
    groovy.get().configureByText("a.groovy", """
      'class Foo {
        public static String string(){}
        public static Comparator<String> comparator(){}
        public static String foo(){}
      }
      
      String s = <caret>
      """);

    groovy.get().complete(CompletionType.SMART, 2);
    runInEdtAndWait(() -> assertEquals(List.of("foo", "string"), groovy.get().getLookupElementStrings()));
  }

  @Test
  public void testGlobalListCreators() {
    groovy.get().configureByFile(groovy.get().getTestName(false) + ".groovy");
    groovy.get().complete(CompletionType.SMART, 2);
    runInEdtAndWait(() -> assertEquals(List.of("createGenericList", "createStringList"), groovy.get().getLookupElementStrings()));
  }

  @Test
  public void testNativeList() {
    completion.get().doSmartCompletion("a1", "a2");
  }

  @Test
  public void testMembersImportStatically() {
    groovy.get().addClass("""
                         class Expected {
                           public static final Expected fooField;
                           public static Expected fooMethod() {}
                         }
                         """);
    groovy.get().configureByText("a.groovy", "Expected exp = fo<caret>");
    LookupElement[] items = groovy.get().complete(CompletionType.SMART);
    runInEdtAndWait(() -> {
      assertEquals(List.of("fooField", "fooMethod"), groovy.get().getLookupElementStrings());
      assertTrue(items[0].as(StaticallyImportable.CLASS_CONDITION_KEY).canBeImported());
      assertTrue(items[1].as(StaticallyImportable.CLASS_CONDITION_KEY).canBeImported());
      items[0].as(StaticallyImportable.CLASS_CONDITION_KEY).setShouldBeImported(true);
    });
    groovy.get().type("\n");
    groovy.get().checkResult("""
                            import static Expected.fooField
                            
                            Expected exp = fooField""");
  }

  @Test
  public void testThrow() {
    groovy.get().configureByText("_a.groovy", """
      throw new RunEx<caret>
      """);
    groovy.get().complete(CompletionType.SMART);
    groovy.get().checkResult("""
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
    runInEdtAndWait(() -> addCompileStatic(groovy.get()));
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
    runInEdtAndWait(() -> addCompileStatic(groovy.get()));
    groovy.get().configureByText("a.groovy", """
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

    groovy.get().complete(CompletionType.SMART);
    runInEdtAndWait(() -> Assertions.assertEquals(List.of(), groovy.get().getLookupElementStrings()));
  }

  @Test
  public void testQualifiedNameAfterNew() {
    groovy.get().addClass("""
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
