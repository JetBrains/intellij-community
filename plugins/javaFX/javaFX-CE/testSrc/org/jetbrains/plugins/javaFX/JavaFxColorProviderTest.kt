package org.jetbrains.plugins.javaFX

import com.intellij.openapi.application.PluginPathManager
import com.intellij.psi.PsiExpression
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.javaFX.fxml.AbstractJavaFXTestCase
import org.jetbrains.plugins.javaFX.fxml.codeInsight.JavaFxColorProvider

/**
 * @author Pavel.Dolgov
 */

class JavaFxColorProviderTest : AbstractJavaFXTestCase() {
  override fun getTestDataPath() = PluginPathManager.getPluginHomePath("javaFX") + "/testData/colorProvider"

  fun testColor() = doTest()
  fun testColor2() = doTest(false)

  fun testRgb() = doTest()
  fun testRgb2() = doTest(false)

  fun testHsb() = doTest()
  fun testHsb2() = doTest(false)


  private fun doTest(isNotNull: Boolean = true) {
    myFixture.configureByFile(getTestName(false) + ".java")
    val carets = myFixture.editor.caretModel.allCarets
    for (caret in carets) {
      val element = myFixture.file.findElementAt(caret.offset)
      assertNotNull("Element at caret:$caret", element)

      val expression = PsiTreeUtil.findFirstParent(element?.parent, { it?.parent !is PsiExpression }) as? PsiExpression
      assertNotNull("Expression at caret:${element?.text}", expression)

      val type = expression?.type?.canonicalText
      assertEquals("Expression type:${expression?.text}", "javafx.scene.paint.Color", type)

      val color = JavaFxColorProvider().getColorFrom(element ?: return)
      if (isNotNull)
        assertNotNull("Color expected:${expression?.text}", color)
      else
        assertNull("Color is not expected:${expression?.text}", color)
    }
  }
}
