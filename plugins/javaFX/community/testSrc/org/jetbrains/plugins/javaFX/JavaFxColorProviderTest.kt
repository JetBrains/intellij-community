// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX

import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiExpression
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.javaFX.fxml.codeInsight.JavaFxColorProvider
import org.jetbrains.plugins.javaFX.fxml.javaFxFixture
import org.jetbrains.plugins.javaFX.fxml.javaFxProjectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test


@TestApplication
class JavaFxColorProviderTest {
  companion object {
    private val projectFixture = javaFxProjectFixture()
  }

  private val javaFx by javaFxFixture(projectFixture, PluginPathManager.getPluginHomePath("javaFX") + "/testData/colorProvider")
  private val myFixture get() = javaFx.fixture

  @Test
  fun testColor(): Unit = doTest()
  @Test
  fun testColor2(): Unit = doTest(false)

  @Test
  fun testRgb(): Unit = doTest()
  @Test
  fun testRgb2(): Unit = doTest(false)

  @Test
  fun testHsb(): Unit = doTest()
  @Test
  fun testHsb2(): Unit = doTest(false)


  private fun doTest(isNotNull: Boolean = true): Unit = timeoutRunBlocking {
    myFixture.configureByFile(javaFx.getTestName(false) + ".java")
    readAction {
      val carets = myFixture.editor.caretModel.allCarets
      for (caret in carets) {
        val element = myFixture.file.findElementAt(caret.offset)
        assertNotNull(element, "Element at caret:$caret")

        val expression = PsiTreeUtil.findFirstParent(element?.parent, { it?.parent !is PsiExpression }) as? PsiExpression
        assertNotNull(expression, "Expression at caret:${element?.text}")

        val type = expression?.type?.canonicalText
        assertEquals("javafx.scene.paint.Color", type, "Expression type:${expression?.text}")

        val color = JavaFxColorProvider().getColorFrom(element ?: return@readAction)
        if (isNotNull)
          assertNotNull(color, "Color expected:${expression?.text}")
        else
          assertNull(color, "Color is not expected:${expression?.text}")
      }
    }
  }
}
