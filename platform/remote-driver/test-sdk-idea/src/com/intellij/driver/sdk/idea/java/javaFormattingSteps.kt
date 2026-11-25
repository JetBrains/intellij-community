package com.intellij.driver.sdk.idea.java

import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.editor
import com.intellij.driver.sdk.ui.shouldBe
import java.awt.event.KeyEvent

val IdeaFrameUI.javaFormattingSteps get() = JavaFormattingSteps(this)

class JavaFormattingSteps(private val ideaFrameUI: IdeaFrameUI) {
  fun compareFormatting(expectedFileName: String, actualFileName: String) {
    ideaFrameUI.run {
      driver.openFile("src/$expectedFileName")
      val expectedText = editor().text.replace(
        expectedFileName.substring(expectedFileName.indexOf("/") + 1, expectedFileName.lastIndexOf(".")),
        actualFileName.substring(actualFileName.indexOf("/") + 1, actualFileName.lastIndexOf("."))
      )
      driver.openFile("src/$actualFileName")
      editor {
        applyFormatting()
        shouldBe {
          expectedText == text
        }
      }
    }
  }

  private fun JEditorUiComponent.applyFormatting() {
    step("Apply formatting") {
      click()
      keyboard { hotKeyWithDefaultModifierKey(KeyEvent.VK_ALT, KeyEvent.VK_L) }
    }
  }
}

