// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ide

import com.intellij.codeInsight.daemon.LineMarkerInfo.LineMarkerGutterIconRenderer
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import junit.framework.TestCase
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mIDENT

abstract class GroovyMainRunLineMarkerTestBase : LightGroovyTestCase() {
  protected fun doAntiRunLineMarkerTest() {
    val marks = myFixture.findGuttersAtCaret()
    TestCase.assertEquals(0, marks.size)
  }

  protected fun doRunLineMarkerTest() {
    val marks = myFixture.findGuttersAtCaret()
    TestCase.assertEquals(1, marks.size)
    val mark = marks[0]
    assertTrue(mark is LineMarkerGutterIconRenderer<*>)
    val gutterIconRenderer = mark as LineMarkerGutterIconRenderer<*>
    val element: PsiElement? = gutterIconRenderer.lineMarkerInfo.element
    assertEquals(AllIcons.RunConfigurations.TestState.Run, gutterIconRenderer.icon)
    assertTrue(element.elementType == mIDENT)
    TestCase.assertEquals("main", element!!.text)
  }
}