// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * Created by Max Medvedev on 10/02/14
 */
abstract class TypeInferenceTestBase extends GroovyResolveTestCase {
  final String basePath = TestUtils.testDataPath + "resolve/inference/"

  @Override
  void setUp() {
    super.setUp()

    myFixture.addClass("package java.math; public class BigDecimal extends Number implements Comparable<BigDecimal> {}")
  }

  protected void doTest(String text, @Nullable String type) {
    def file = myFixture.configureByText('_.groovy', text)
    def ref = file.findReferenceAt(myFixture.editor.caretModel.offset) as GrReferenceExpression
    def actual = ref.type
    assertType(type, actual)
  }

  protected void doExprTest(@Language("Groovy") String text, @Nullable String expectedType) {
    GroovyFile file = myFixture.configureByText('_.groovy', text) as GroovyFile
    GrStatement lastStatement = file.statements.last()
    assertInstanceOf lastStatement, GrExpression
    assertType(expectedType, (lastStatement as GrExpression).type)
  }

  protected void doCSExprTest(@Language("Groovy") String text, @Nullable String expectedType) {
    GroovyFile file = myFixture.configureByText('_.groovy', """
import groovy.transform.CompileStatic
@CompileStatic 
def m() {
  $text<caret>
}
""") as GroovyFile
    def ref = file.findReferenceAt(myFixture.editor.caretModel.offset - 1) as GrReferenceExpression
    def actual = ref.type
    assertType(expectedType, actual)
  }
}
