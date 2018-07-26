// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression

@CompileStatic
trait BaseTest {

  abstract CodeInsightTestFixture getFixture()

  GroovyFile configureByText(String text) {
    (GroovyFile)fixture.configureByText('_.groovy', text)
  }

  GrExpression configureByExpression(String text) {
    (GrExpression)configureByText(text).statements.last()
  }
}
