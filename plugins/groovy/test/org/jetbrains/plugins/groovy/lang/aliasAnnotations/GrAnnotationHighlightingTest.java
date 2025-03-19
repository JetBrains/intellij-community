// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.aliasAnnotations;

import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase;

/**
 * @author Max Medvedev
 */
public class GrAnnotationHighlightingTest extends GrHighlightingTestBase {
  @Override
  public String getBasePath() {
    return null;
  }

  public void testAnnotatedAliasIsCorrect() {
    doTestHighlighting("""
                         import groovy.transform.*
                         
                         @AnnotationCollector([ToString, EqualsAndHashCode, Immutable])
                         @interface Alias {}
                         
                         @Alias(excludes = ["a"])
                         class F<caret>oo {
                             Integer a, b
                         }
                         """);
  }

  public void testAliasWithProperties() {
    doTestHighlighting("""
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

  public void testAliasWithMissedProperty() {
    doTestHighlighting("""
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

  public void testInapplicableAlias() {
    doTestHighlighting("""
                         import groovy.transform.*
                         
                         @ToString(excludes = ['a', 'b'])
                         @AnnotationCollector([EqualsAndHashCode, Immutable])
                         @interface Alias {}
                         
                         @<error descr="'@groovy.transform.EqualsAndHashCode' not applicable to local variable"><error descr="'@groovy.transform.Immutable' not applicable to local variable"><error descr="'@groovy.transform.ToString' not applicable to local variable">Alias</error></error></error>(excludes = ['a'])
                         int foo
                         """);
  }

  public void testCorrectAnnotation() {
    doTestHighlighting("""
                         import groovy.transform.*
                         
                         @EqualsAndHashCode(excludes = ["a"])
                         @AnnotationCollector([ToString, Immutable])
                         @Field
                         @interface Alias {}
                         """);
  }

  public void testInapplicableAttributeInAliasDeclaration() {
    doTestHighlighting("""
                         import groovy.transform.*
                         
                         @ToString(excludes = ['a', 'b'], <error descr="@interface 'groovy.transform.ToString' does not contain attribute 'foo'">foo</error> = 4)
                         @AnnotationCollector([EqualsAndHashCode, Immutable])
                         @interface Alias {}
                         
                         @Alias<error descr="@interface 'groovy.transform.ToString' does not contain attribute 'foo'"></error>
                         class Foo{}
                         """);
  }

  public void testUnknownAttributeInAliasUsage() {
    doTestHighlighting("""
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

  public void testInapplicableAttributeInAliasUsage() {
    doTestHighlighting("""
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

  public void testAnnotationCollectorInterfaceWithAttrs() {
    doTestHighlighting("""
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

  public void testAnnotationCollectorClass() {
    doTestHighlighting("""
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

  public void testInapplicableAlias2() {
    doTestHighlighting("""
                         import groovy.transform.AnnotationCollector
                         import groovy.transform.Immutable
                         import groovy.transform.ToString
                         
                         @AnnotationCollector([Immutable, ToString])
                         @interface Alias4 {}
                         
                         @Immutable
                         @ToString
                         @AnnotationCollector
                         @interface Alias5 {}
                         
                         @<error descr="'@groovy.transform.Immutable' not applicable to method"><error descr="'@groovy.transform.ToString' not applicable to method">Alias4</error></error>
                         def aaa() {}
                         
                         @<error descr="'@groovy.transform.Immutable' not applicable to method"><error descr="'@groovy.transform.ToString' not applicable to method">Alias5</error></error>
                         def bbb() {}
                         """);
  }

  public void testCompileDynamic() {
    doTestHighlighting("""
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
