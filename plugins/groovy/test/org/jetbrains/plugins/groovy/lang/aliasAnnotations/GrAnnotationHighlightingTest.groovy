/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.aliasAnnotations

import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase

/**
 * @author Max Medvedev
 */
class GrAnnotationHighlightingTest extends GrHighlightingTestBase {
  String getBasePath() {
    null
  }

  void testAnnotatedAliasIsCorrect() {
    testHighlighting('''\
import groovy.transform.*

@AnnotationCollector([ToString, EqualsAndHashCode, Immutable])
@interface Alias {}

@Alias(excludes = ["a"])
class F<caret>oo {
    Integer a, b
}
''')
  }

  void testAliasWithProperties() {
    testHighlighting('''\
import groovy.transform.*

@ToString(excludes = ['a', 'b'])
@AnnotationCollector([EqualsAndHashCode, Immutable])
@interface Alias {}

@Alias(excludes = ["a"])
class F<caret>oo {
    Integer a, b
}
''')
  }

  void testAliasWithMissedProperty() {
    testHighlighting('''\
import groovy.transform.*

@interface X {
  String[] excludes()
}

@ToString(excludes = ['a', 'b'])
@AnnotationCollector([X, Immutable])
@interface Alias {}

@<error descr="Missed attributes: excludes">Alias</error>
class F<caret>oo {
    Integer a, b
}
''')
  }

  void testInapplicableAlias() {
    testHighlighting('''\
import groovy.transform.*

@ToString(excludes = ['a', 'b'])
@AnnotationCollector([EqualsAndHashCode, Immutable])
@interface Alias {}

@<error descr="'@groovy.transform.EqualsAndHashCode' not applicable to local variable"><error descr="'@groovy.transform.Immutable' not applicable to local variable"><error descr="'@groovy.transform.ToString' not applicable to local variable">Alias</error></error></error>(excludes = ['a'])
int foo
''')
  }

  void testCorrectAnnotation() {
    testHighlighting('''\
import groovy.transform.*

@EqualsAndHashCode(excludes = ["a"])
@AnnotationCollector([ToString, Immutable])
@Field
@interface Alias {}
''')
  }

  void testInapplicableAttributeInAliasDeclaration() {
    testHighlighting('''\
import groovy.transform.*

@ToString(excludes = ['a', 'b'], <error descr="@interface 'groovy.transform.ToString' does not contain attribute 'foo'">foo</error> = 4)
@AnnotationCollector([EqualsAndHashCode, Immutable])
@interface Alias {}

@<error descr="@interface 'groovy.transform.ToString' does not contain attribute 'foo'">Alias</error>
class Foo{}
''')
  }

  void testUnknownAttributeInAliasUsage() {
    testHighlighting('''\
import groovy.transform.*

@ToString(excludes = ['a', 'b'])
@AnnotationCollector([EqualsAndHashCode, Immutable])
@interface Alias {}

@Alias(<error descr="@interface 'Alias' does not contain attribute 'foo'">foo</error> = 5)
class Foo {
    Integer a, b
}
''')
  }

  void testInapplicableAttributeInAliasUsage() {
    testHighlighting('''\
import groovy.transform.*

@ToString(excludes = ['a', 'b'])
@AnnotationCollector([EqualsAndHashCode, Immutable])
@interface Alias {}

@Alias(excludes = <error descr="Cannot assign 'Integer' to 'String[]'">5</error>)
class Foo {
    Integer a, b
}
''')
  }

  public void testAnnotationCollectorInterfaceWithAttrs() {
    testHighlighting('''\
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
''')
  }

  public void testAnnotationCollectorClass() {
    testHighlighting('''
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
''')
  }

  void testInapplicableAlias2() {
    testHighlighting('''\
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
''')
  }

  void testCompileDynamic() {
    testHighlighting('''\
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
''', GrUnresolvedAccessInspection)
  }

}
