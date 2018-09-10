// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import groovy.transform.CompileStatic
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression

import static com.intellij.testFramework.UsefulTestCase.assertInstanceOf
import static org.jetbrains.plugins.groovy.LightGroovyTestCase.assertType

@CompileStatic
trait TypingTest {

  abstract CodeInsightTestFixture getFixture()

  void typingTest(@Language("Groovy") String text, @Nullable String expectedType) {
    def file = (GroovyFile)fixture.configureByText('_.groovy', text)
    def lastStatement = file.statements.last()
    assertInstanceOf lastStatement, GrExpression
    assertType expectedType, ((GrExpression)lastStatement).type
  }
}
