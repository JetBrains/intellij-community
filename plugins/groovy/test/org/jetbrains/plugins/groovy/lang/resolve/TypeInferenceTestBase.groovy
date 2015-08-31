/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
  protected void setUp() {
    super.setUp()

    myFixture.addClass("package java.math; public class BigDecimal extends Number implements Comparable<BigDecimal> {}");
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
}
