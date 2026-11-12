// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.StaticallyImportable;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.testNameFixture;
import static org.jetbrains.plugins.groovy.completion.GroovyCompletionFixture.groovyCompletionFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.addCompileStatic;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.configureByExactName;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class GroovySmartCompletionTest {
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST, "groovy/completion/smart");
  private final TestFixture<GroovyCompletionTestFixture> completion = groovyCompletionFixture(groovy);
  private final TestFixture<String> testName = testNameFixture(false);

  @Test
  public void testSmartCompletionAfterNewInDeclaration() {
    runInEdtAndWait(() -> {
      groovy.get().configureByFile(testName.get() + ".groovy");
      groovy.get().complete(CompletionType.SMART);
      assertEquals(List.of("Bar", "Foo"), groovy.get().getLookupElementStrings());
    });
  }

  @Test
  public void testCaretAfterSmartCompletionAfterNewInDeclaration() { runInEdtAndWait(() -> { completion.get().doSmartTest(); }); }

  @Test
  public void testSmartCompletionAfterNewInDeclarationWithArray() { runInEdtAndWait(() -> { completion.get().doSmartTest(); }); }

  @Test
  public void testSmartCompletionAfterNewInDeclarationWithIntArray() { runInEdtAndWait(() -> { completion.get().doSmartTest(); }); }

  @Test
  public void testShortenNamesInSmartCompletionAfterNewInDeclaration() { runInEdtAndWait(() -> { completion.get().doSmartTest(); }); }

  @Test
  public void testSmartAfterNewInCall() { runInEdtAndWait(() -> { completion.get().doSmartTest(); }); }

  @Test
  public void testInnerClassInStaticMethodCompletion() {
    runInEdtAndWait(() -> {
      completion.get().doVariantableTest(null, "", CompletionType.SMART, CompletionResult.notContain, "Inner");
    });
  }

  @Test
  public void testSmartCompletionInAssignmentExpression() { runInEdtAndWait(() -> { completion.get().doSmartTest(); }); }

  @Test
  public void testSimpleMethodParameter() {
    runInEdtAndWait(() -> {
      completion.get().doSmartCompletion("d1", "d2");
    });
  }

  @Test
  public void testReturnStatement() {
    runInEdtAndWait(() -> {
      completion.get().doSmartCompletion("b", "b1", "b2", "foo");
    });
  }

  @Test
  public void testIncSmartCompletion() {
    runInEdtAndWait(() -> {
      completion.get().doSmartCompletion("a", "b");
    });
  }

  @Test
  public void testInheritConstructorsAnnotation() {
    runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testSmartCastCompletion() { runInEdtAndWait(() -> { completion.get().doSmartTest(); }); }

  @Test
  public void testSmartCastCompletionWithoutRParenth() { runInEdtAndWait(() -> { completion.get().doSmartTest(); }); }

  @Test
  public void testSmartCastCompletionWithRParenth() { runInEdtAndWait(() -> { completion.get().doSmartTest(); }); }

  @Test
  public void testDontCompletePrivateMembers() { runInEdtAndWait(() -> { completion.get().doSmartCompletion("foo1", "foo2", "getFoo1", "getFoo2"); }); }

  @Test
  public void testEnumMembersInAssignment() { runInEdtAndWait(() -> { completion.get().doSmartCompletion("IN_STOCK", "NOWHERE", "ORDERED", "valueOf"); }); }

  @Test
  public void testEnumMembersInAssignmentInsideEnum() {
    runInEdtAndWait(() -> {
      completion.get().doSmartCompletion("IN_STOCK", "NOWHERE", "ORDERED", "next", "previous", "valueOf");
    });
  }

  @Test
  public void testEnumMembersInComparison() { runInEdtAndWait(() -> { completion.get().doSmartCompletion("status", "IN_STOCK", "NOWHERE", "ORDERED", "valueOf"); }); }

  @Test
  public void testPreferVarargElement() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest(null, null, "\n", CompletionType.SMART);
    });
  }

  @Test
  public void testGlobalStaticMembers() {
    runInEdtAndWait(() -> {
      groovy.get().configureByFile(testName.get() + ".groovy");
      groovy.get().complete(CompletionType.SMART, 2);
      assertEquals(List.of("SUBSTRING", "createExpected", "createSubGeneric", "SUB_RAW"), groovy.get().getLookupElementStrings());
    });
  }

  @Test
  public void testGlobalStaticMembersForString() {
    runInEdtAndWait(() -> {
      configureByExactName(groovy.get(), "a.groovy", """
        'class Foo {
          public static String string(){}
          public static Comparator<String> comparator(){}
          public static String foo(){}
        }
      
        String s = <caret>
        """);

      groovy.get().complete(CompletionType.SMART, 2);
      assertEquals(List.of("foo", "string"), groovy.get().getLookupElementStrings());
    });
  }

  @Test
  public void testGlobalListCreators() {
    runInEdtAndWait(() -> {
      groovy.get().configureByFile(testName.get() + ".groovy");
      groovy.get().complete(CompletionType.SMART, 2);
      assertEquals(List.of("createGenericList", "createStringList"), groovy.get().getLookupElementStrings());
    });
  }

  @Test
  public void testNativeList() { runInEdtAndWait(() -> { completion.get().doSmartCompletion("a1", "a2"); }); }

  @Test
  public void testMembersImportStatically() {
    runInEdtAndWait(() -> {
      groovy.get().addClass("""
                           class Expected {
                             public static final Expected fooField;
                             public static Expected fooMethod() {}
                           }
                           """);
      configureByExactName(groovy.get(), "a.groovy", "Expected exp = fo<caret>");
      LookupElement[] items = groovy.get().complete(CompletionType.SMART);
      assertEquals(List.of("fooField", "fooMethod"), groovy.get().getLookupElementStrings());
      assertTrue(items[0].as(StaticallyImportable.CLASS_CONDITION_KEY).canBeImported());
      assertTrue(items[1].as(StaticallyImportable.CLASS_CONDITION_KEY).canBeImported());
      items[0].as(StaticallyImportable.CLASS_CONDITION_KEY).setShouldBeImported(true);
      groovy.get().type("\n");
      groovy.get().checkResult("""
                              import static Expected.fooField
                            
                              Expected exp = fooField""");
    });
  }

  @Test
  public void testThrow() {
    runInEdtAndWait(() -> {
      configureByExactName(groovy.get(), "_a.groovy", """
        throw new RunEx<caret>
        """);
      groovy.get().complete(CompletionType.SMART);
      groovy.get().checkResult("""
                              throw new RuntimeException()
                              """);
    });
  }

  @Test
  public void testInnerClassReferenceWithoutQualifier() {
    runInEdtAndWait(() -> {
      completion.get().doSmartTest();
    });
  }

  @Test
  public void testAnonymousClassCompletion() {
    runInEdtAndWait(() -> {
      completion.get().doSmartTest("""
                    Runnable r = new Run<caret>
                    """, """
                    Runnable r = new Runnable() {
                        @Override
                        void run() {<caret><selection></selection>
                  
                        }
                    }
                    """);
    });
  }

  @Test
  public void testReassignedVar() {
    runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testFieldWithInstanceof() {
    runInEdtAndWait(() -> {
      addCompileStatic(groovy.get());
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
    });
  }

  @Test
  public void testFieldWithAssignment() {
    runInEdtAndWait(() -> {
      addCompileStatic(groovy.get());
      configureByExactName(groovy.get(), "a.groovy", """
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
      Assertions.assertEquals(List.of(), groovy.get().getLookupElementStrings());
    });
  }

  @Test
  public void testQualifiedNameAfterNew() {
    runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testBinaryExpr() {
    runInEdtAndWait(() -> {
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
    });
  }
}
