package com.intellij.driver.sdk.python.steps

import com.intellij.driver.sdk.python.runProfilerToolWindow
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.mainToolbar
import com.intellij.driver.sdk.ui.components.common.toolwindows.profilerToolWindow
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.ui.shouldBe
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val IdeaFrameUI.profiler get() = ProfilerSteps(this)

class ProfilerSteps(private val ideFrame: IdeaFrameUI) {

  fun startProfilerFromMainToolbar() {
    step("Start profiler from main toolbar") {
      ideFrame.run {
        mainToolbar.moreButton.click()
        popup().waitFound()
        popup().waitOneContainsText("Profile").click()
        config.selectInterpreterInEditConfigPopup("_endpoints", "Profile")
      }
    }
  }

  fun stopProfilerFromMainToolbar() {
    step("Stop profiler from main toolbar") {
      ideFrame.run {
        mainToolbar.stopButton.click()
      }
    }
  }

  fun openProfilerTab(tabName: String) {
    step("Open profiler tab '$tabName'") {
      ideFrame.run {
        profilerToolWindow {
          val tab = x { and(byAccessibleName(tabName), byClass("SimpleColoredComponent")) }
          tab.shouldBe("$tabName tab is not present") { present() }
          tab.click()
        }
      }
    }
  }

  fun checkProfilerToolWindow(texts: List<String>, button: String? = null) {
    step("Check profiler tool window") {
      ideFrame.run {
        profilerToolWindow {
          if (button != null) {
            val button = x { byAccessibleName(button) }
            button.shouldBe("$button button is not present") { present() }
            button.click()
          }
          waitFor<String>(
            message = "Profiler tool window contains $texts",
            timeout = 15.seconds,
            getter = { getAllTexts().joinToString() },
            checker = { content -> texts.all { it in content } },
          )
        }
      }
    }
  }

  fun checkRunProfilerToolWindow(texts: List<String>, timeout: Duration = 1.minutes) {
    step("Check profiler tool window") {
      ideFrame.run {
        runProfilerToolWindow {
          shouldBe("Profiler tool window is not present") { present() }
          var content = ""
          waitFor("Profiler tool window contains $texts", timeout = timeout,
                  errorMessage = { "Profiler tool window does not contain ${texts.filterNot { it in content }}: $content" }) {
            content = getAllTexts().joinToString()
            texts.all { it in content }
          }
        }
      }
    }
  }
}

