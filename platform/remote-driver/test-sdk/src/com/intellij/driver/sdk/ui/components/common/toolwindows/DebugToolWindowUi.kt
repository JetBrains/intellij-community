package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.elements.JButtonUiComponent
import com.intellij.driver.sdk.ui.components.elements.accessibleList
import com.intellij.driver.sdk.ui.components.elements.button
import com.intellij.driver.sdk.ui.shouldBe
import com.intellij.driver.sdk.ui.ui

fun IdeaFrameUI.debugToolWindow(func: DebugToolWindowUi.() -> Unit = {}): DebugToolWindowUi =
  x(DebugToolWindowUi::class.java) { componentWithChild(byClass("InternalDecoratorImpl"), byAccessibleName("Threads & Variables")) }.apply(func)

fun DebugToolWindowUi.languageChooser(func: LanguageChooser.() -> Unit = {}): LanguageChooser = x(LanguageChooser::class.java) { byClass("LanguageChooser") }.apply(func)


open class DebugToolWindowUi(data: ComponentData) : ToolWindowUiComponent(data) {

  protected var defaultTabs: MutableMap<String, UiComponent> = mapOf("Threads & Variables" to threadsAndVariablesTab, "Console" to consoleTab).toMutableMap()

  protected fun defineDebuggerTabByAccessibleName(name: String): UiComponent = x { and(byClass("SimpleColoredComponent"), byAccessibleName(name)) }
  val consoleTab: UiComponent get() = defineDebuggerTabByAccessibleName("Console")
  val threadsAndVariablesTab: UiComponent get() = defineDebuggerTabByAccessibleName("Threads & Variables")
  val debuggerConsoleTab: UiComponent get() = defineDebuggerTabByAccessibleName("Debugger Console")
  val memoryView: UiComponent get() = defineDebuggerTabByAccessibleName("Memory View")
  val consoleView: UiComponent get() = x { byType("com.intellij.execution.impl.ConsoleViewImpl") }
  val resumeButton: JButtonUiComponent get() = button { byAccessibleName("Resume Program") }
  val stopButton: UiComponent get() = x("//div[@myicon='stop.svg']")
  val stepOverButton: JButtonUiComponent get() = button { byAccessibleName("Step Over") }
  val stepOutButton: JButtonUiComponent get() = button { byAccessibleName("Step Out") }
  val stepIntoButton: JButtonUiComponent get() = button { byAccessibleName("Step Into") }
  val jbRunnerTabs: UiComponent get() = x { byType("com.intellij.execution.ui.layout.impl.JBRunnerTabs") }
  val debugTabWindow: UiComponent get() = x { byType("com.intellij.openapi.wm.impl.ToolWindowImpl") }

  fun selectOptionFromEvaluationToolTip(optionToSelect: String, fullMatch: Boolean = false) {
    driver.ui.accessibleList { byClass("LookupList") }
      .shouldBe("Evaluation tooltip with available items should be present") { present() }
      .clickItem(optionToSelect, fullMatch)
    keyboard {
      enter()
      enter()
    }
  }
}

class LanguageChooser(data: ComponentData) : DebugToolWindowUi(data) {

  fun getCurrentValue(): String {
    return getAllTexts().map { it.text }.first()
  }

  fun verifyCurrentValue(expectedValue: String): LanguageChooser {
    shouldBe("Verify current value equals to $expectedValue", { getCurrentValue() == expectedValue })
    return this
  }

  fun openLanguagePicker(): LanguageChooser {
    this.click()
    return this
  }

  fun selectLanguage(languageToSelect: String): LanguageChooser {
    driver.ui.accessibleList { and(byAccessibleName("Choose Language"), byClass("MyList")) }
      .clickItem(languageToSelect)
    return this
  }

}
