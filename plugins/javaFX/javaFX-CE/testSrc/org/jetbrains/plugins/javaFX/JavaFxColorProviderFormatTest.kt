package org.jetbrains.plugins.javaFX

import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.application.WriteAction
import com.intellij.psi.PsiExpression
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.javaFX.fxml.AbstractJavaFXTestCase
import org.jetbrains.plugins.javaFX.fxml.codeInsight.JavaFxColorProvider
import java.awt.Color
import java.util.*

/**
 * @author Pavel.Dolgov
 */
class JavaFxColorProviderFormatTest : AbstractJavaFXTestCase() {
  override fun getTestDataPath() = PluginPathManager.getPluginHomePath("javaFX") + "/testData/colorProviderFormat"

  fun testLocaleWithDotAsNumberSeparator() = withLocale(Locale.US) { doTest("NumberComponentFormat") }

  fun testLocaleWithCommaAsNumberSeparator() = withLocale(Locale.GERMANY) { doTest("NumberComponentFormat") }

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

  private fun doTest(testName: String) {
    myFixture.configureByFile(testName + ".java")
    val colorExpressions = myFixture.editor.caretModel.allCarets
      .map { caret -> myFixture.file.findElementAt(caret.offset) }
      .map { element -> PsiTreeUtil.findFirstParent(element, { it?.parent !is PsiExpression }) as? PsiExpression }

    val colorProvider = JavaFxColorProvider()
    val components = arrayOf(0, 0x40, 0x80, 0xFF)
    for ((i, expression) in colorExpressions.withIndex()) {
      val color = Color(components[i % 4], components[(i + 1) % 4], components[(i + 2) % 4], components[(i + 3) % 4])
      WriteAction.run<RuntimeException> {
        if (expression != null) {
          colorProvider.setColorTo(expression, color)
        }
      }
    }
    myFixture.checkResultByFile(testName + "_after.java")
  }
}