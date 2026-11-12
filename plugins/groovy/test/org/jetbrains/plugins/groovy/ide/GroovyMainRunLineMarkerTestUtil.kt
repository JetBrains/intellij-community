// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ide

import com.intellij.codeInsight.daemon.LineMarkerInfo.LineMarkerGutterIconRenderer
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.util.elementType
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mIDENT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Checks that the caret has no gutter mark.
 */
@TestOnly
fun CodeInsightTestFixture.doAntiRunLineMarkerTest() {
  val marks = findGuttersAtCaret()
  assertEquals(0, marks.size)
}

/**
 * Checks that the caret has one gutter mark: the run mark of the `main` method.
 */
@TestOnly
fun CodeInsightTestFixture.doRunLineMarkerTest() {
  val marks = findGuttersAtCaret()
  assertEquals(1, marks.size)
  val mark = marks[0]
  assertTrue(mark is LineMarkerGutterIconRenderer<*>)
  val gutterIconRenderer = mark as LineMarkerGutterIconRenderer<*>
  assertEquals(AllIcons.RunConfigurations.TestState.Run, gutterIconRenderer.icon)
  ReadAction.computeBlocking<Unit, RuntimeException> {
    val element = gutterIconRenderer.lineMarkerInfo.element
    assertTrue(element.elementType == mIDENT)
    assertEquals("main", element!!.text)
  }
}
