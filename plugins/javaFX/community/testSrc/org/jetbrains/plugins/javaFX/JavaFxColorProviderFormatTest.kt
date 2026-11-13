// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiExpression
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.javaFX.fxml.codeInsight.JavaFxColorProvider
import org.jetbrains.plugins.javaFX.fxml.javaFxFixture
import org.jetbrains.plugins.javaFX.fxml.javaFxProjectFixture
import org.junit.jupiter.api.Test
import java.awt.Color
import java.util.Locale

@TestApplication
class JavaFxColorProviderFormatTest {
  companion object {
    private val projectFixture = javaFxProjectFixture()
  }

  private val javaFx by javaFxFixture(projectFixture, PluginPathManager.getPluginHomePath("javaFX") + "/testData/colorProviderFormat")
  private val myFixture get() = javaFx.fixture

  @Test
  fun testLocaleWithDotAsNumberSeparator(): Unit = withLocale(Locale.US) { doTest("NumberComponentFormat") }

  @Test
  fun testLocaleWithCommaAsNumberSeparator(): Unit = withLocale(Locale.GERMANY) { doTest("NumberComponentFormat") }

  private fun withLocale(locale: Locale, doWork: () -> Unit) {
    val oldLocale = Locale.getDefault()
    try {
      Locale.setDefault(locale)
      doWork()
    }
    finally {
      Locale.setDefault(oldLocale)
    }
  }

  private fun doTest(testName: String): Unit = timeoutRunBlocking {
    myFixture.configureByFile("$testName.java")
    val colorExpressions = readAction {
      myFixture.editor.caretModel.allCarets
        .map { caret -> myFixture.file.findElementAt(caret.offset) }
        .map { element -> PsiTreeUtil.findFirstParent(element, { it?.parent !is PsiExpression }) as? PsiExpression }
    }

    val colorProvider = JavaFxColorProvider()
    val components = arrayOf(0, 0x40, 0x80, 0xFF)
    withContext(Dispatchers.EDT) {
      for ((i, expression) in colorExpressions.withIndex()) {
        val color = Color(components[i % 4], components[(i + 1) % 4], components[(i + 2) % 4], components[(i + 3) % 4])
        WriteAction.run<RuntimeException> {
          if (expression != null) {
            colorProvider.setColorTo(expression, color)
          }
        }
      }
    }
    myFixture.checkResultByFile(testName + "_after.java")
  }
}
