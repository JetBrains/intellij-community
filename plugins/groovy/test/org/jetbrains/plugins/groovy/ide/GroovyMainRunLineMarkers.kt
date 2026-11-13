// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GroovyMainRunLineMarkers")

package org.jetbrains.plugins.groovy.ide

import com.intellij.codeInsight.daemon.GutterMark
import com.intellij.codeInsight.daemon.LineMarkerInfo.LineMarkerGutterIconRenderer
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.elementType
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mIDENT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf

/**
 * Checks that the caret in [fixture] has no gutter mark.
 * It replaces a helper of the JUnit 3 base `GroovyMainRunLineMarkerTestBase`.
 */
@TestOnly
fun doAntiRunLineMarkerTest(fixture: JavaCodeInsightTestFixture) {
  assertEquals(0, findGuttersAtCaret(fixture).size)
}

/** Checks that the caret in [fixture] has one run gutter mark on the identifier `main`. */
@TestOnly
fun doRunLineMarkerTest(fixture: JavaCodeInsightTestFixture) {
  val marks = findGuttersAtCaret(fixture)
  assertEquals(1, marks.size)
  val gutterIconRenderer = assertInstanceOf(LineMarkerGutterIconRenderer::class.java, marks[0])
  assertEquals(AllIcons.RunConfigurations.TestState.Run, gutterIconRenderer.icon)
  val (type, text) = ReadAction.computeBlocking<Pair<IElementType?, String>, Throwable> {
    val element = gutterIconRenderer.lineMarkerInfo.element
    element.elementType to element!!.text
  }
  assertEquals(mIDENT, type)
  assertEquals("main", text)
}

/** Finds the gutter marks at the caret of [fixture]. The highlighting of the editor runs on the EDT. */
@TestOnly
fun findGuttersAtCaret(fixture: JavaCodeInsightTestFixture): List<GutterMark> = timeoutRunBlocking {
  withContext(Dispatchers.EDT) { fixture.findGuttersAtCaret() }
}

/** Finds all gutter marks of the file of [fixture]. The highlighting of the editor runs on the EDT. */
@TestOnly
fun findAllGutters(fixture: JavaCodeInsightTestFixture): List<GutterMark> = timeoutRunBlocking {
  withContext(Dispatchers.EDT) { fixture.findAllGutters() }
}
