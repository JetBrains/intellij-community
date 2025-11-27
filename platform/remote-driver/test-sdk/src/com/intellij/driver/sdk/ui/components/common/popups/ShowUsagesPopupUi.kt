package com.intellij.driver.sdk.ui.components.common.popups

import com.intellij.driver.client.impl.RefWrapper
import com.intellij.driver.model.RdTarget
import com.intellij.driver.sdk.ui.AccessibleNameCellRendererReader
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.elements.JTableUiComponent
import com.intellij.driver.sdk.ui.components.elements.PopupUiComponent

fun IdeaFrameUI.showUsagesPopupUi(block: ShowUsagesPopupUi.() -> Unit = {}) =
  x(ShowUsagesPopupUi::class.java) {
    componentWithChild(byClass("HeavyWeightWindow"), byClass("ShowUsagesTable"))
  }.apply(block)

class ShowUsagesPopupUi(data: ComponentData) : PopupUiComponent(data) {
  val usagesFoundLabel = x { contains(byAccessibleName("usages")) }
  val showUsagesTable: ShowUsagesResultsTableUi = x(ShowUsagesResultsTableUi::class.java) { byClass("ShowUsagesTable") }

  class ShowUsagesResultsTableUi(data: ComponentData): JTableUiComponent(data) {
    val items: List<ShowUsagesItem> get() = content().map { (_, row) ->
      val rowValues = row.values.toList()
      check(rowValues.size == 4) { "failed to parse usages row values, ${rowValues}" }
      val file = rowValues[1].removePrefix("File ").substringBefore(",")
      val line = rowValues[2].substringBeforeLast(":").removePrefix("line ").removePrefix("(").toInt()
      val usageText = rowValues[3].removePrefix("Usage text ")
      ShowUsagesItem(file, line, usageText)
    }

    val selectedItem: ShowUsagesItem? get() = getSelectedRow().let { if (it != -1) items[it] else null }

    init {
      replaceCellRendererReader {
        driver.new(AccessibleNameCellRendererReader::class, rdTarget = (it as? RefWrapper)?.getRef()?.rdTarget ?: RdTarget.DEFAULT)
      }
    }

    fun clickItem(predicate: (ShowUsagesItem) -> Boolean) {
      val row = items.withIndex().singleOrNull { predicate(it.value) }?.index ?: error("The item is not found in the usages table")
      clickCell(row, 0)
    }

    data class ShowUsagesItem(val file: String, val line: Int, val usageText: String)
  }
}