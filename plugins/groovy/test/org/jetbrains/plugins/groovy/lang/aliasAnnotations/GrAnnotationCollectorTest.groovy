// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  void 'test recursive alias'() {
    fixture.configureByText '_.groovy', '''\
@Alias
@groovy.transform.AnnotationCollector
@interface Alias {}

@Alias
class <caret>Usage {}
'''
    fixture.checkHighlighting()
    assert getActualAnnotations(fixture.findClass("Usage") as GrTypeDefinition).isEmpty()
  }
}
