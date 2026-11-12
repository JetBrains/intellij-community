// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.aliasAnnotations;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixtureKt.grHighlightingFixture;

/**
 * @author Max Medvedev
 */
@TestApplication
public class GrAnnotationHighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<GrHighlightingFixture> highlighting = grHighlightingFixture(projectFixture);

  @Test
  public void testAnnotatedAliasIsCorrect() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.*
                         
                         @AnnotationCollector([ToString, EqualsAndHashCode, Immutable])
                         @interface Alias {}
                         
                         @Alias(excludes = ["a"])
                         class F<caret>oo {
                             Integer a, b
                         }
                         """);
  }

  @Test
  public void testAliasWithProperties() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.*
                         
                         @ToString(excludes = ['a', 'b'])
                         @AnnotationCollector([EqualsAndHashCode, Immutable])
                         @interface Alias {}
                         
                         @Alias(excludes = ["a"])
                         class F<caret>oo {
                             Integer a, b
                         }
                         """);
  }

  @Test
  public void testAliasWithMissedProperty() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.*
                         
                         @interface X {
                           String[] excludes()
                         }
                         
                         @ToString(excludes = ['a', 'b'])
                         @AnnotationCollector([X, Immutable])
                         @interface Alias {}
                         
                         @Alias<error descr="Missed attributes: excludes"></error>
                         class F<caret>oo {
                             Integer a, b
                         }
                         """);
  }

  @Test
  public void testInapplicableAlias() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.*
                         
                         @ToString(excludes = ['a', 'b'])
                         @AnnotationCollector([EqualsAndHashCode, Immutable])
                         @interface Alias {}
                         
                         @<error descr="'@EqualsAndHashCode' annotation not allowed on local variable"><error descr="'@Immutable' annotation not allowed on local variable"><error descr="'@ToString' annotation not allowed on local variable">Alias</error></error></error>(excludes = ['a'])
                         int foo
                         """);
  }

  @Test
  public void testCorrectAnnotation() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.*
                         
                         @EqualsAndHashCode(excludes = ["a"])
                         @AnnotationCollector([ToString, Immutable])
                         @Field
                         @interface Alias {}
                         """);
  }

  @Test
  public void testInapplicableAttributeInAliasDeclaration() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.*
                         
                         @ToString(excludes = ['a', 'b'], <error descr="@interface 'groovy.transform.ToString' does not contain attribute 'foo'">foo</error> = 4)
                         @AnnotationCollector([EqualsAndHashCode, Immutable])
                         @interface Alias {}
                         
                         @Alias<error descr="@interface 'groovy.transform.ToString' does not contain attribute 'foo'"></error>
                         class Foo{}
                         """);
  }

  @Test
  public void testUnknownAttributeInAliasUsage() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.*
                         
                         @ToString(excludes = ['a', 'b'])
                         @AnnotationCollector([EqualsAndHashCode, Immutable])
                         @interface Alias {}
                         
                         @Alias(<error descr="@interface 'Alias' does not contain attribute 'foo'">foo</error> = 5)
                         class Foo {
                             Integer a, b
                         }
                         """);
  }

  @Test
  public void testInapplicableAttributeInAliasUsage() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.*
                         
                         @ToString(excludes = ['a', 'b'])
                         @AnnotationCollector([EqualsAndHashCode, Immutable])
                         @interface Alias {}
                         
                         @Alias(excludes = <error descr="Cannot assign 'Integer' to 'String[]'">5</error>)
                         class Foo {
                             Integer a, b
                         }
                         """);
  }

  @Test
  public void testAnnotationCollectorInterfaceWithAttrs() {
    highlighting.get().doTestHighlighting("""
                         @interface Foo {
                           int foo()
                         }
                         
                         @groovy.transform.AnnotationCollector
                         @Foo
                         @interface <error descr="Annotation type annotated with @AnnotationCollector cannot have attributes">A</error> {
                           int bar()
                         }
                         
                         @A(foo = 2)
                         class X{}
                         """);
  }

  @Test
  public void testAnnotationCollectorClass() {
    highlighting.get().doTestHighlighting("""
                         @interface Foo {
                           int foo()
                         }
                         
                         @groovy.transform.AnnotationCollector
                         @Foo
                         class A {
                           int bar() {}
                         }
                         
                         class B{}
                         
                         @A(foo = 2)
                         @<error descr="'B' is not an annotation">B</error>
                         class X {}
                         """);
  }

  @Test
  public void testInapplicableAlias2() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.AnnotationCollector
                         import groovy.transform.Immutable
                         import groovy.transform.ToString
                         
                         @AnnotationCollector([Immutable, ToString])
                         @interface Alias4 {}
                         
                         @Immutable
                         @ToString
                         @AnnotationCollector
                         @interface Alias5 {}
                         
                         @<error descr="'@Immutable' annotation not allowed on method"><error descr="'@ToString' annotation not allowed on method">Alias4</error></error>
                         def aaa() {}
                         
                         @<error descr="'@Immutable' annotation not allowed on method"><error descr="'@ToString' annotation not allowed on method">Alias5</error></error>
                         def bbb() {}
                         """);
  }

  @Test
  public void testCompileDynamic() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.CompileDynamic
                         import groovy.transform.CompileStatic
                         
                         @CompileStatic
                         class B {
                             B() {
                                 println <error>x</error>
                             }
                         
                             @CompileDynamic
                             def foo() {
                                 println <warning>y</warning>
                             }
                         }
                         """, GrUnresolvedAccessInspection.class);
  }
}
