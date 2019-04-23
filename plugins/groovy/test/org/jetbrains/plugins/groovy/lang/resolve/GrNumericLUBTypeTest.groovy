// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression

@CompileStatic
class GrNumericLUBTypeTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST

  @Override
  void setUp() throws Exception {
    super.setUp()
    fixture.addFileToProject '''constants.groovy''', '''\
interface Constants {
  Byte nB = 0
  Short nS = 0
  Integer nI = 0
  Long nL = 0
  Float nF = 0
  Double nD = 0
}
'''
  }

  private void doTest(String expressionText, String expectedType) {
    def file = fixture.configureByText('_.groovy', """\
import static Constants.*
$expressionText
""") as GroovyFile
    def expression = file.statements.last() as GrExpression
    assert expression.type.equalsToText(expectedType): "'$expression.text' : $expression.type"
  }

  void 'test elvis types'() {
    [
      // Byte
      'nB ?: nB': 'java.lang.Byte',
      'nB ?: nS': 'java.lang.Short',
      'nB ?: nI': 'java.lang.Integer',
      'nB ?: nL': 'java.lang.Long',
      'nB ?: nF': 'java.lang.Float',
      'nB ?: nD': 'java.lang.Double',

      // Short
      'nS ?: nB': 'java.lang.Short',
      'nS ?: nS': 'java.lang.Short',
      'nS ?: nI': 'java.lang.Integer',
      'nS ?: nL': 'java.lang.Long',
      'nS ?: nF': 'java.lang.Float',
      'nS ?: nD': 'java.lang.Double',

      // Integer
      'nI ?: nB': 'java.lang.Integer',
      'nI ?: nS': 'java.lang.Integer',
      'nI ?: nI': 'java.lang.Integer',
      'nI ?: nL': 'java.lang.Long',
      'nI ?: nF': 'java.lang.Float',
      'nI ?: nD': 'java.lang.Double',

      // Long
      'nL ?: nB': 'java.lang.Long',
      'nL ?: nS': 'java.lang.Long',
      'nL ?: nI': 'java.lang.Long',
      'nL ?: nL': 'java.lang.Long',
      'nL ?: nF': 'java.lang.Float',
      'nL ?: nD': 'java.lang.Double',

      // Float
      'nF ?: nB': 'java.lang.Float',
      'nF ?: nS': 'java.lang.Float',
      'nF ?: nI': 'java.lang.Float',
      'nF ?: nL': 'java.lang.Float',
      'nF ?: nF': 'java.lang.Float',
      'nF ?: nD': 'java.lang.Double',

      // Double
      'nD ?: nB': 'java.lang.Double',
      'nD ?: nS': 'java.lang.Double',
      'nD ?: nI': 'java.lang.Double',
      'nD ?: nL': 'java.lang.Double',
      'nD ?: nF': 'java.lang.Double',
      'nD ?: nD': 'java.lang.Double',
    ].each {
      doTest it.key, it.value
    }
  }

  // same as elvis
  void 'test ternary types'() {
    [
      '42 ? nB : nB': 'java.lang.Byte',
      '42 ? nB : nS': 'java.lang.Short',
      '42 ? nB : nI': 'java.lang.Integer',
      '42 ? nB : nL': 'java.lang.Long',
      '42 ? nB : nF': 'java.lang.Float',
      '42 ? nB : nD': 'java.lang.Double',
    ].each {
      doTest it.key, it.value
    }
  }

  // same as elvis
  void 'test if branches assignment'() {
    [
      'def a; if (42) { a = nB } else { a = nB }; a': 'java.lang.Byte',
      'def a; if (42) { a = nB } else { a = nS }; a': 'java.lang.Short',
      'def a; if (42) { a = nB } else { a = nI }; a': 'java.lang.Integer',
      'def a; if (42) { a = nB } else { a = nL }; a': 'java.lang.Long',
      'def a; if (42) { a = nB } else { a = nF }; a': 'java.lang.Float',
      'def a; if (42) { a = nB } else { a = nD }; a': 'java.lang.Double',
    ].each {
      doTest it.key, it.value
    }
  }
}
