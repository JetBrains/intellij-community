// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.util.LiteralUtilKt
import org.jetbrains.plugins.groovy.util.BaseTest
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.junit.Assert
import org.junit.Test

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
}
