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

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition

/**
 * @author Max Medvedev
 */
class GrAnnotationCollectorTest extends LightGroovyTestCase {
  final String basePath = null

  void testAnnotatedAlias() {
    doTest('''\
import groovy.transform.*

@AnnotationCollector([ToString, EqualsAndHashCode, Immutable])
@interface Alias {}

@Alias(excludes = ["a"])
class F<caret>oo {
    Integer a, b
}
''', '''\
@ToString(excludes = ["a"])
@EqualsAndHashCode(excludes = ["a"])
@Immutable
''')
  }

  void testAliasWithProperties() {
    doTest('''\
import groovy.transform.*

@AnnotationCollector([ToString, EqualsAndHashCode, Immutable])
@interface Alias {}

@Alias(excludes = ["a"])
class F<caret>oo {
    Integer a, b
}
''', '''\
@ToString(excludes = ["a"])
@EqualsAndHashCode(excludes = ["a"])
@Immutable
''')
  }

  void testMixedProperties() {
    doTest('''\
import groovy.transform.*

@ToString(excludes = ['a', 'b'])
@AnnotationCollector([EqualsAndHashCode, Immutable])
@interface Alias {}

@Alias(excludes = ["a"])
class F<caret>oo {
    Integer a, b
}
''', '''\
@EqualsAndHashCode(excludes = ["a"])
@Immutable
@ToString(excludes = ["a"])
''')
  }

  void testAliasDeclarationWithoutParams() {
    doTest('''\
@interface X {}
@interface Y {}

@X @Y
@groovy.transform.AnnotationCollector
@interface Alias {}

@Alias
class F<caret>oo {}
''', '''\
@X
@Y
''')
  }


  void doTest(@NotNull String text, @NotNull String expectedAnnotations) {
    addAnnotationCollector()
    myFixture.configureByText('_a.groovy', text)
    final atCaret = myFixture.file.findElementAt(myFixture.editor.caretModel.offset)
    final clazz = PsiTreeUtil.getParentOfType(atCaret, GrTypeDefinition.class)
    assertNotNull(clazz)

    final actual = getActualAnnotations(clazz)

    assertEquals(expectedAnnotations, actual)
  }

  @NotNull
  @NonNls
  static String getActualAnnotations(@NotNull GrTypeDefinition clazz) {
    StringBuilder buffer = new StringBuilder()

    for (GrAnnotation annotation : clazz.modifierList.annotations) {
      buffer << '@' << annotation.shortName

      final GrAnnotationNameValuePair[] attributes = annotation.parameterList.attributes
      if (attributes.length > 0) {
        buffer << '('

        for (GrAnnotationNameValuePair pair : attributes) {
          final name = pair.name
          if (name != null) {
            buffer << name << " = "
          }

          buffer << pair.value.text
        }

        buffer << ')'
      }
      buffer << '\n'
    }

    return buffer.toString()
  }
}
