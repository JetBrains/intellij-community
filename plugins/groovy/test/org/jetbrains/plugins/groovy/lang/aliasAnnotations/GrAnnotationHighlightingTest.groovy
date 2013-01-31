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

@<error descr="'@ToString' not applicable to local variable">Alias</error>(excludes = ['a'])
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
}
