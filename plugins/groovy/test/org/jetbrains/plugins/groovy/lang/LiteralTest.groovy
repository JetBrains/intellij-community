// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.util.LiteralUtilKt
import org.jetbrains.plugins.groovy.util.BaseTest
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.jetbrains.plugins.groovy.util.TestUtils
import org.junit.Assert
import org.junit.Test

import static com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER
import static com.intellij.psi.CommonClassNames.JAVA_LANG_LONG
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.JAVA_MATH_BIG_INTEGER

@CompileStatic
class LiteralTest extends GroovyLatestTest implements BaseTest {

  @Test
  void 'literal value'() {
    def data = [
      '42'      : 42,
      '0b101010': 0b101010,
      '0B101010': 0B101010,
      '052'     : 052,
      '0x2a'    : 0x2a,
      '0X2a'    : 0X2a,
      '42i'     : 42i,
      '42I'     : 42I,
      '42l'     : 42l,
      '42L'     : 42L,
      '42g'     : 42g,
      '42G'     : 42G,
      '42f'     : 42f,
      '42F'     : 42F,
      '42d'     : 42d,
      '42D'     : 42D,
      '42.42'   : 42.42,
      '42.0g'   : 42.0g,
      '42.0G'   : 42.0G,
    ]
    for (entry in data) {
      def literal = elementUnderCaret(entry.key, GrLiteral)
      Assert.assertEquals(entry.key, entry.value, literal.value)
    }
  }

  @Test
  void 'zero literals'() {
    def data = [
      '0',
      '0b0', '0B0',
      '00',
      '0x0', '0X0',
      '0i', '0I',
      '0l', '0L',
      '0g', '0G',
      '0f', '0F',
      '0d', '0D',
      '0.0g', '0.0G',
    ]
    for (string in data) {
      assert LiteralUtilKt.isZero(elementUnderCaret(string, GrLiteral))
    }
  }

  @Test
  void 'integer literals without suffix'() {
    def data = [
      "2147483647"                                                        : JAVA_LANG_INTEGER,
      "0b1111111111111111111111111111111"                                 : JAVA_LANG_INTEGER,
      "017777777777"                                                      : JAVA_LANG_INTEGER,
      "0x7FFFFFFF"                                                        : JAVA_LANG_INTEGER,
      "2147483648"                                                        : JAVA_LANG_LONG,
      "0b10000000000000000000000000000000"                                : JAVA_LANG_LONG,
      "020000000000"                                                      : JAVA_LANG_LONG,
      "0x80000000"                                                        : JAVA_LANG_LONG,
      "9223372036854775807"                                               : JAVA_LANG_LONG,
      "0b111111111111111111111111111111111111111111111111111111111111111" : JAVA_LANG_LONG,
      "0777777777777777777777"                                            : JAVA_LANG_LONG,
      "0x7FFFFFFFFFFFFFFF"                                                : JAVA_LANG_LONG,
      "9223372036854775808"                                               : JAVA_MATH_BIG_INTEGER,
      "0b1000000000000000000000000000000000000000000000000000000000000000": JAVA_MATH_BIG_INTEGER,
      "01000000000000000000000"                                           : JAVA_MATH_BIG_INTEGER,
      "0x8000000000000000"                                                : JAVA_MATH_BIG_INTEGER,
    ]
    TestUtils.runAll(data) { literalText, literalType ->
      def literal = elementUnderCaret(literalText, GrLiteral)
      Assert.assertEquals(literalText, literalType, literal.type.canonicalText)
      def value = literal.value
      Assert.assertNotNull(literalText, value)
      Assert.assertEquals(value.class.name, literalType)
    }
  }
}
