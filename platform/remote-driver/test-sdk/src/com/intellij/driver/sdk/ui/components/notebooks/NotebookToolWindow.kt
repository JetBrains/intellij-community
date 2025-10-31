package com.intellij.driver.sdk.ui.components.notebooks

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UIComponentsList
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration.Companion.seconds

internal val Finder.kotlinNotebookSessionToolWindow: KotlinNotebookToolWindowUiComponent
  get() = x("""
    //div[@class='InternalDecoratorImpl']
    //div[@class='XNextIslandHolder' and
        .//div[
          @class='JPanel' and .//div[@class='BaseLabel' and contains(@text, "Kotlin Notebook")]
        ]
    ]
  """.trimIndent(), KotlinNotebookToolWindowUiComponent::class.java)

fun Finder.withKotlinNotebookSessionToolWindow(action: KotlinNotebookToolWindowUiComponent.() -> Unit) {
  with(kotlinNotebookSessionToolWindow) {
    action()
  }
}

class KotlinNotebookToolWindowUiComponent(data: ComponentData) : UiComponent(data) {
  val notebookTabs: UIComponentsList<UiComponent>
    get() = xx("""
      /div[@class='JPanel']
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
    variablesButton?.click()
  }

  fun openKernelLogsTab() {
    kernelLogsButton?.click()
  }

  internal val kernelLogsButton: UiComponent?
    get() = perNotebookTabs.firstOrNull { it.hasSubtext("Kernel logs") }

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

  fun waitForVariablesViewText(text: String) = waitFor("expect $text in $variablesViewText", timeout = 25.seconds) { variablesViewText.contains(text) }
}