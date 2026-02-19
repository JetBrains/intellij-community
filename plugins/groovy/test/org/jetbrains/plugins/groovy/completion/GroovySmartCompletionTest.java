// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.StaticallyImportable;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.testFramework.UsefulTestCase;
import junit.framework.TestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

public class GroovySmartCompletionTest extends GroovyCompletionTestBase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/completion/smart";
  }

  public void testSmartCompletionAfterNewInDeclaration() throws Throwable {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    myFixture.complete(CompletionType.SMART);
    UsefulTestCase.assertOrderedEquals(myFixture.getLookupElementStrings(), "Bar", "Foo");
  }

  public void testCaretAfterSmartCompletionAfterNewInDeclaration() throws Throwable { doSmartTest(); }

  public void testSmartCompletionAfterNewInDeclarationWithArray() throws Throwable { doSmartTest(); }

  public void testSmartCompletionAfterNewInDeclarationWithIntArray() throws Throwable { doSmartTest(); }

  public void testShortenNamesInSmartCompletionAfterNewInDeclaration() throws Throwable { doSmartTest(); }

  public void testSmartAfterNewInCall() throws Throwable { doSmartTest(); }

  public void testInnerClassInStaticMethodCompletion() throws Throwable {
    doVariantableTest(null, "", CompletionType.SMART, CompletionResult.notContain, "Inner");
  }

  public void testSmartCompletionInAssignmentExpression() throws Throwable { doSmartTest(); }

  public void testSimpleMethodParameter() throws Throwable {
    doSmartCompletion("d1", "d2");
  }

  public void testReturnStatement() throws Exception {
    doSmartCompletion("b", "b1", "b2", "foo");
  }

  public void testIncSmartCompletion() throws Exception {
    doSmartCompletion("a", "b");
  }

  public void testInheritConstructorsAnnotation() throws Throwable {
    myFixture.addFileToProject("groovy/transform/InheritConstructors.java", """
      package groovy.transform;
      
      import java.lang.annotation.ElementType;
      import java.lang.annotation.Retention;
      import java.lang.annotation.RetentionPolicy;
      import java.lang.annotation.Target;@Retention(RetentionPolicy.SOURCE)
      @Target({ElementType.TYPE})
      public @interface InheritConstructors {
      }""");
    doSmartTest();
  }

  public void testSmartCastCompletion() { doSmartTest(); }

  public void testSmartCastCompletionWithoutRParenth() { doSmartTest(); }

  public void testSmartCastCompletionWithRParenth() { doSmartTest(); }

  public void testDontCompletePrivateMembers() { doSmartCompletion("foo1", "foo2", "getFoo1", "getFoo2"); }

  public void testEnumMembersInAssignment() { doSmartCompletion("IN_STOCK", "NOWHERE", "ORDERED", "valueOf"); }

  public void testEnumMembersInAssignmentInsideEnum() {
    doSmartCompletion("IN_STOCK", "NOWHERE", "ORDERED", "next", "previous", "valueOf");
  }

  public void testPreferVarargElement() {
    doCompletionTest(null, null, "\n", CompletionType.SMART);
  }

  public void testGlobalStaticMembers() {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    myFixture.complete(CompletionType.SMART, 2);
    UsefulTestCase.assertOrderedEquals(myFixture.getLookupElementStrings(), "SUBSTRING", "createExpected", "createSubGeneric", "SUB_RAW");
  }

  public void testGlobalStaticMembersForString() {
    myFixture.configureByText("a.groovy", """
      'class Foo {
        public static String string(){}
        public static Comparator<String> comparator(){}
        public static String foo(){}
      }
      
      String s = <caret>
      """);

    myFixture.complete(CompletionType.SMART, 2);
    UsefulTestCase.assertOrderedEquals(myFixture.getLookupElementStrings(), "foo", "string");
  }

  public void testGlobalListCreators() {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    myFixture.complete(CompletionType.SMART, 2);
    UsefulTestCase.assertOrderedEquals(myFixture.getLookupElementStrings(), "createGenericList", "createStringList");
  }

  public void testNativeList() { doSmartCompletion("a1", "a2"); }

  public void testMembersImportStatically() {
    myFixture.addClass("""
                         class Expected {
                           public static final Expected fooField;
                           public static Expected fooMethod() {}
                         }
                         """);
    myFixture.configureByText("a.groovy", "Expected exp = fo<caret>");
    LookupElement[] items = myFixture.complete(CompletionType.SMART);
    assertEquals(List.of("fooField", "fooMethod"), myFixture.getLookupElementStrings());
    assertTrue(items[0].as(StaticallyImportable.CLASS_CONDITION_KEY).canBeImported());
    assertTrue(items[1].as(StaticallyImportable.CLASS_CONDITION_KEY).canBeImported());
    items[0].as(StaticallyImportable.CLASS_CONDITION_KEY).setShouldBeImported(true);
    myFixture.type("\n");
    myFixture.checkResult("""
                            import static Expected.fooField
                            
                            Expected exp = fooField""");
  }

  public void testThrow() {
    myFixture.configureByText("_a.groovy", """
      throw new RunEx<caret>
      """);
    myFixture.complete(CompletionType.SMART);
    myFixture.checkResult("""
                            throw new RuntimeException()
                            """);
  }

  public void testInnerClassReferenceWithoutQualifier() {
    doSmartTest();
  }

  public void testAnonymousClassCompletion() {
    doSmartTest("""
                  Runnable r = new Run<caret>
                  """, """
                  Runnable r = new Runnable() {
                      @Override
                      void run() {<caret><selection></selection>
                  
                      }
                  }
                  """);
  }

  public void testReassignedVar() {
    doSmartTest("""
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

  public void testFieldWithInstanceof() {
    addCompileStatic();
    doSmartTest("""
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

  public void testFieldWithAssignment() {
    addCompileStatic();
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
    TestCase.assertEquals(List.of(), myFixture.getLookupElementStrings());
  }

  public void testQualifiedNameAfterNew() {
    myFixture.addClass("""
                         package foo;
                         public class User<T> {}
                         """);
    doSmartTest(""" 
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

  public void testBinaryExpr() {
    doSmartTest("""
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
