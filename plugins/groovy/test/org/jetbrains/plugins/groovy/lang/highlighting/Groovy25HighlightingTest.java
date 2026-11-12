// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyFixtures;
import org.jetbrains.plugins.groovy.codeInspection.GroovyUnusedDeclarationInspection;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyConstructorNamedArgumentsInspection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.util.HighlightingTest;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class Groovy25HighlightingTest implements HighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_5);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "highlighting/v25/");

  @Override
  public @NotNull LightJavaCodeInsightTestFixture getFixture() {
    return codeInsightFixture.get();
  }

  @Override
  public String getTestName() {
    return LightGroovyFixtures.groovyTestName(codeInsightFixture.get());
  }


  @Test
  public void testVarDeclarations() { fileHighlightingTest(); }

  @Test
  public void testDuplicatingNamedParams() { fileHighlightingTest(); }

  @Test
  public void testDuplicatingNamedParamsWithSetter() { fileHighlightingTest(); }

  @Test
  public void testTwoIdenticalNamedDelegates() { fileHighlightingTest(); }

  @Test
  public void testDuplicatingNamedDelegatesWithUsualParameter() { fileHighlightingTest(); }

  @Test
  public void testNamedParamsTypeCheck() { fileHighlightingTest(); }

  @Test
  public void testNamedParamsTypeCheckWithSetter() { fileHighlightingTest(); }

  @Test
  public void testNamedParamsUnusedCheck() { fileHighlightingTest(GroovyUnusedDeclarationInspection.class); }

  @Test
  public void testNamedParamsRequired() { fileHighlightingTest(); }

  @Test
  public void testSeveralAbsentRequiredNamedParams() { fileHighlightingTest(); }

  @Test
  public void testRequiredNamedParamInNamedVariant() { fileHighlightingTest(); }

  @Test
  public void testNamedDelegateWithoutProperties() { fileHighlightingTest(); }

  @Test
  public void testImmutableFields() { fileHighlightingTest(); }

  @Test
  public void testImmutableOptionsAbsentField() { fileHighlightingTest(); }

  @Test
  public void testImmutableConstructor() {
    highlightingTest("""
                       import groovy.transform.Immutable

                       @Immutable
                       class C {
                           <error descr="Explicit constructors are not allowed for @Immutable class">C</error>(){}
                       }
                       """);
  }

  @Test
  public void testGetterInImmutable() {
    highlightingTest("""
                       import groovy.transform.Immutable

                       @Immutable
                       class A {
                         String immutable
                         private String mutable

                         String <error descr="Repetitive method name 'getImmutable'">getImmutable</error>() {immutable}
                         String getMutable() {mutable}
                       }
                       """);
  }

  @Test
  public void testGetterInImmutable2() {
    highlightingTest("""
                       import groovy.transform.Immutable

                       @Immutable
                       class A {
                         String immutable

                         int <error descr="Repetitive method name 'getImmutable'">getImmutable</error>() {1}
                       }
                       """);
  }

  @Test
  public void testTupleConstructorInImmutable() {
    highlightingTest("""
                       @groovy.transform.Immutable\s
                       class Foo { int a; String b }

                       @groovy.transform.CompileStatic
                       def m() {
                       //  new Foo() // TODO
                         new Foo<error descr="Constructor 'Foo' in 'Foo' cannot be applied to '(java.lang.Integer)'">(2)</error>
                         new Foo(2, "3")
                         new Foo<error descr="Constructor 'Foo' in 'Foo' cannot be applied to '(java.lang.Integer, java.lang.String, java.lang.Integer)'">(2, "3", 9)</error>
                       }""");
  }

  @Test
  public void testCopyWithInImmutable() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    highlightingTest("""
                       @groovy.transform.ImmutableBase(copyWith = true)
                       class CopyWith {
                         String stringProp
                         Integer integerProp
                       }

                       def usage(CopyWith cw) {
                         cw.copyWith(st<caret>ringProp: 'hello')
                       }
                       """);

    ReadAction.runBlocking(() -> {
      PsiReference ref = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
      assertNotNull(ref);
      PsiElement resolved = ref.resolve();
      assertTrue(resolved instanceof GrField);
    });
  }

  @Test
  public void testIDEA_218376() {
    highlightingTest("""
                       import groovy.transform.CompileStatic
                       import groovy.transform.NamedParam

                       @CompileStatic
                       void namedParams(@NamedParam(value = 'last', type = Integer) Map args, int i) {
                         print args.last
                       }

                       @CompileStatic
                       def m() {
                           namedParams(1, last: <error descr="Type of argument 'last' can not be 'String'">"1"</error>)
                       }

                       m()
                       """);
  }

  @Test
  public void testMapConstructorFromRawMap() {
    highlightingTest("""
                       @groovy.transform.MapConstructor
                       class Rr {
                           String actionType
                       }


                       static void main(String[] args) {
                           def x = [actionType: "kik"] as Rr
                           println x.actionType
                       }
                       """, GroovyConstructorNamedArgumentsInspection.class);
  }

  @Test
  public void testNamedVariant() {
    highlightingTest("""
                       class Rr {
                           @groovy.transform.NamedVariant
                           Rr(String s1, Integer s2) {
                              \s
                           }
                       }

                       @groovy.transform.CompileStatic
                       def foo() {
                           new Rr(s1: "a", s2: 10)
                       }
                       """);
  }

  @Test
  public void testNamedVariantWithAutoDelegate() {
    highlightingTest("""
                       class Foo {
                           int aaa
                           boolean bbb
                       }

                       @groovy.transform.NamedVariant(autoDelegate = true)
                       static def bar(Foo a) {}

                       @groovy.transform.CompileStatic
                       static def foo() {
                           bar(aaa: 10, bbb: true)
                       }""");
  }

  @Test
  public void testVisibilityOptionsForNamedVariant() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("other.groovy", """
      import groovy.transform.options.Visibility

      @groovy.transform.CompileStatic
      class Cde {
          @groovy.transform.NamedVariant
          @groovy.transform.VisibilityOptions(method = Visibility.PUBLIC)
          private static def foo(String s) {}
      }""");
    highlightingTest("""
                       class X {

                           @groovy.transform.CompileStatic
                           static void main(String[] args) {
                               Cde.foo(s : "")
                               Cde.<error>foo</error>("")
                           }

                       }""");
  }

  @Test
  public void testTraitAsAnonymous() {
    highlightingTest("""
                       trait T {}

                       new T(){}
                       """);
  }

  @Test
  public void testSafeChainDot() {
    highlightingTest("""
                       a<error>??.</error>b
                       """);
  }

  @Test
  public void testPutAtOperator() {
    highlightingTest("""
                       def summary = ['Imported rules': 0,
                                      'Ignored rules' : 0]
                       summary['Imported rules'] += 1""", GroovyAssignabilityCheckInspection.class);
  }

  @Test
  public void testTupleDeclaration() {
    highlightingTest(
         """
         void f() {
             def x = 0
             <error descr="'var' declarations are available in Groovy 3.0 or later">var</error> y = 1
             (x, y) = [-1, 0]
             <error descr="'var' declarations are available in Groovy 3.0 or later">var</error> (Integer a, b) = [1, 2]
             def (Integer c, d) = [3, 4]
     
             <error descr="Multiple assignment needs 'def' keyword">final</error> (Integer e, f) = [5, 6]
         }
         """
    );
  }
}
