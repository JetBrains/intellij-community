package com.intellij.driver.sdk.ui.components.kotlin

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.elements.JLabelUiComponent
import com.intellij.driver.sdk.ui.components.elements.JTableUiComponent
import com.intellij.driver.sdk.ui.pasteText
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitFor
import org.intellij.lang.annotations.Language
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun Finder.notebookEditor(@Language("xpath") xpath: String? = null, action: NotebookEditorUiComponent.() -> Unit) {
  return x(xpath ?: "//div[@class='EditorCompositePanel']",
           NotebookEditorUiComponent::class.java).action()
}


class NotebookEditorUiComponent(data: ComponentData) : JEditorUiComponent(data) {
  private val addCellBelow
    get() = x("//div[@myicon='add.svg']")
  private val runAllCells
    get() = x("//div[@myicon='runAll.svg']")

  fun addCodeCell() = addCellBelow.click()

  fun addCodeCell(text: String) {
    addCodeCell()
    driver.ui.pasteText(text)
  }

  fun runAllCells() = runAllCells.click()

  fun runAllCellsAndWaitExecuted(timeout: Duration = 30.seconds) = run {
    runAllCells()
    waitFor(timeout = timeout) {
      notebookCellExecutionInfos.all {
        it.getParent().x { contains(byAttribute("defaulticon", "greenCheckmark.svg")) }.present()
      }
    }
  }


  val notebookCellLines
    get() = xx("//div[@class='FullEditorWidthRenderer']//div[contains(@class, 'NotebookAboveCellDelimiterPanel')]").list()

  val notebookCellOutputs
    get() = xx("//div[@class='FullEditorWidthRenderer']//div[@class='EditorComponentImpl']").list()

  val notebookMdCellsAsHtml
    get() = xx("//div[@class='FullEditorWidthRenderer']//div[@class='JupyterMarkdownHtmlPane']").list()

  val notebookCellExecutionInfos
    get() = xx("//div[@class='FullEditorWidthRenderer']/div[@class='NotebookBelowCellDelimiterPanel']/div[@class='JLabel']", JLabelUiComponent::class.java).list()

  val notebookTables
    get() = xx("//div[@class='TableResultView']", JTableUiComponent::class.java).list()

}
