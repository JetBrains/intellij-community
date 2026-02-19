// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.codeInspection.GroovyUnusedDeclarationInspection;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyConstructorNamedArgumentsInspection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.util.HighlightingTest;
import org.jetbrains.plugins.groovy.util.TestUtils;

public class Groovy25HighlightingTest extends LightGroovyTestCase implements HighlightingTest {
  public void testDuplicatingNamedParams() { fileHighlightingTest(); }

  public void testDuplicatingNamedParamsWithSetter() { fileHighlightingTest(); }

  public void testTwoIdenticalNamedDelegates() { fileHighlightingTest(); }

  public void testDuplicatingNamedDelegatesWithUsualParameter() { fileHighlightingTest(); }

  public void testNamedParamsTypeCheck() { fileHighlightingTest(); }

  public void testNamedParamsTypeCheckWithSetter() { fileHighlightingTest(); }

  public void testNamedParamsUnusedCheck() {
    getFixture().enableInspections(new GroovyUnusedDeclarationInspection());
    fileHighlightingTest();
  }

  public void testNamedParamsRequired() { fileHighlightingTest(); }

  public void testSeveralAbsentRequiredNamedParams() { fileHighlightingTest(); }

  public void testRequiredNamedParamInNamedVariant() { fileHighlightingTest(); }

  public void testNamedDelegateWithoutProperties() { fileHighlightingTest(); }

  public void testImmutableFields() { fileHighlightingTest(); }

  public void testImmutableOptionsAbsentField() { fileHighlightingTest(); }

  public void testImmutableConstructor() {
    highlightingTest("""
                       import groovy.transform.Immutable

                       @Immutable
                       class C {
                           <error descr="Explicit constructors are not allowed for @Immutable class">C</error>(){}
                       }
                       """);
  }

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

  public void testCopyWithInImmutable() {
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

    PsiReference ref = getFixture().getFile().findReferenceAt(getEditor().getCaretModel().getOffset());
    assertNotNull(ref);
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrField);
  }

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

  public void testVisibilityOptionsForNamedVariant() {
    getFixture().addFileToProject("other.groovy", """
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

  public void testTraitAsAnonymous() {
    highlightingTest("""
                       trait T {}

                       new T(){}
                       """);
  }

  public void testSafeChainDot() {
    highlightingTest("""
                       a<error>??.</error>b
                       """);
  }

  public void testPutAtOperator() {
    highlightingTest("""
                       def summary = ['Imported rules': 0,
                                      'Ignored rules' : 0]
                       summary['Imported rules'] += 1""", GroovyAssignabilityCheckInspection.class);
  }

  public void testTupleDeclaration() {
    highlightingTest(
         """
         void f() {
             def x = 0
             def y = 1
             (x, y) = [-1, 0]
             <error descr="Tuple declaration should end with 'def' modifier">var</error> (Integer a, b) = [1, 2]
             def (Integer c, d) = [3, 4]
     
             <error descr="Tuple declaration should end with 'def' modifier">final</error> (Integer e, f) = [5, 6]
         }
         """
    );
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_2_5;
  }

  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "highlighting/v25/";
  }
}
