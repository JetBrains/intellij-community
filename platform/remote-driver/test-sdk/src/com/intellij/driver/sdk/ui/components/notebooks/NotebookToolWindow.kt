package com.intellij.driver.sdk.ui.components.notebooks

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UIComponentsList
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.toolwindows.StripeButtonUi
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration.Companion.seconds

val Finder.kotlinNotebookToolWindowButton: StripeButtonUi? get() = xx(StripeButtonUi::class.java) {
  byAccessibleName("Kotlin Notebook")
}.list().firstOrNull()

val Finder.kotlinNotebookToolWindow: KotlinNotebookToolWindowUiComponent
  get() = x("""
    //div[@class='InternalDecoratorImpl']
    //div[@class='XNextIslandHolder' and
        .//div[
          @class='JPanel' and .//div[@class='BaseLabel' and contains(@text, "Kotlin Notebook")]
        ]
    ]
  """.trimIndent(), KotlinNotebookToolWindowUiComponent::class.java)

inline fun Finder.withKotlinNotebookToolWindow(action: KotlinNotebookToolWindowUiComponent.() -> Unit) {
  with(kotlinNotebookToolWindow) {
    action()
  }
}

val Finder.jupyterVariablesPanel: UiComponent
  get() = x("//div[@class='PythonJupyterVarsToolWindow']")

class KotlinNotebookToolWindowUiComponent(data: ComponentData) : UiComponent(data) {
  val notebookTabs: UIComponentsList<UiComponent>
    get() = xx("""
      //div[@class='JPanel']
      //div[@class='TabPanel']
      /div[@class='ContentTabLabel']
    """.trimIndent())

  val perNotebookTabs: List<UiComponent>
    get() = xx("""
      //div[@class='MyNonOpaquePanel']
      //div[@class='JBRunnerTabs']
      /div[@class='SingleHeightLabel']
    """.trimIndent()).list()

  fun openVariablesTab() {
    variablesButton?.click() ?: error("Variables tab is not available")
  }

  fun openKernelLogsTab() {
    kernelLogsButton?.click() ?: error("Kernel logs tab is not available")
  }

  internal val kernelLogsButton: UiComponent?
    get() = perNotebookTabs.firstOrNull { it.hasSubtext("Kernel Logs") }

  internal val variablesButton: UiComponent?
    get() = perNotebookTabs.firstOrNull { it.hasSubtext("Variables") }

  val variablesToolWindowUiComponent: UiComponent
    get() = x(
      """
        //div[@class='KotlinNotebookVarsToolWindow']
        //div[@class='XDebuggerTree']
      """.trimIndent()
    )

  val variablesViewText: String
    get() = if (variablesButton == null) "" else {
      variablesToolWindowUiComponent.getAllTexts().joinToString("") { it.text }
    }

  fun waitForVariablesViewText(text: String) {
    waitFor("expect $text in $variablesViewText", timeout = 25.seconds) { variablesViewText.contains(text) }
  }
}