package com.intellij.driver.sdk.idea.java

import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.elements.dialog
import com.intellij.openapi.util.SystemInfo
import java.awt.event.KeyEvent

val IdeaFrameUI.javaGenerateSteps get() = JavaGenerateSteps(this)

class JavaGenerateSteps(private val ideFrame: IdeaFrameUI) {
  fun callGenerateMenuOption(option: OptionsOfGenerate) {
    ideFrame.run {
      step("Open 'Generate' -> '${option.value}'") {
        if (SystemInfo.isMac) {
          keyboard { hotKey(KeyEvent.VK_META, KeyEvent.VK_N) }
        }
        else {
          keyboard { hotKey(KeyEvent.VK_ALT, KeyEvent.VK_INSERT) }
        }
        val generateList = x("//div[@class='MyList']")

        if (option == OptionsOfGenerate.TEST) {
          generateList.waitOneContainsText(text = option.value).click()
          dialog(title = "No Test Roots Found").pressButton("OK")
        }
        else
          generateList.waitOneText(text = option.value).click()
      }
    }
  }
}

