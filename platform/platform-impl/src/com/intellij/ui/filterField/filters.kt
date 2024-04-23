package com.intellij.ui.filterField

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.CheckBoxList
import com.intellij.ui.ScreenUtil
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.util.function.Supplier

fun createFilterActions(filterField: Component, attribute: String, @Nls title: String,
                        items: List<FilterItem>, selectedItems: Collection<String>, applier: FilterApplier): Collection<AnAction> {
  val group = mutableListOf<AnAction>()
  group.add(FilterAllAction(attribute, applier))
  group.add(FilterMultiSelectAction(attribute, title, filterField, items, selectedItems, applier))
  group.add(Separator())
  fillFilterItems(items, attribute, selectedItems, group, applier)
  return group
}

fun fillFilterItems(items: List<FilterItem>,
                    attribute: String,
                    selectedItems: Collection<String>,
                    group: MutableList<AnAction>,
                    applier: FilterApplier) {
  for (item in items) {
    val action = FilterValueAction(attribute, item.title, item.value, applier)
    if (selectedItems.contains(item.value)) {
      action.templatePresentation.icon = AllIcons.Actions.Checked
    }
    group.add(action)
  }
}

fun interface FilterApplier {
  fun applyFilter(attribute: String, values: Collection<String>)
}

class FilterItem(@Nls val title: String, val value: String) {
  constructor(titleMsg: Supplier<@Nls String>, value: String) : this(titleMsg.get(), value)
}

class FilterAllAction(private val attribute: String,
                      private val applier: FilterApplier) : AnAction(IdeBundle.message("filters.all")) {
  override fun actionPerformed(e: AnActionEvent) {
    applier.applyFilter(attribute, emptyList())
  }
}

class FilterValueAction(private val attribute: String,
                        @Nls private val title: String,
                        private val value: String,
                        private val applier: FilterApplier) : AnAction(title) {
  override fun actionPerformed(e: AnActionEvent) {
    applier.applyFilter(attribute, listOf(value))
  }
}

class FilterMultiSelectAction(private val attribute: String,
                              @Nls(capitalization = Nls.Capitalization.Title) private val title: String,
                              private val owner: Component,
                              private val items: List<FilterItem>,
                              private val selectedValues: Collection<String>,
                              private val applier: FilterApplier)
  : AnAction(IdeBundle.message("filters.select")) {

  override fun actionPerformed(e: AnActionEvent) {
    val checkboxList = CheckBoxList<FilterItem>()
    checkboxList.setItems(items) { it.title }
    for (item in items) {
      if (selectedValues.contains(item.value)) {
        checkboxList.setItemSelected(item, true)
      }
    }

    if (items.isNotEmpty()) {
      checkboxList.addSelectionInterval(0, 0)
    }

    checkboxList.setCheckBoxListListener { _, _ ->
      val selected = mutableSetOf<String>()
      for (item in items) {
        if (checkboxList.isItemSelected(item)) {
          selected.add(item.value)
        }
      }
      applier.applyFilter(attribute, selected)
    }

    val scrollPane = JBScrollPane(checkboxList)
    scrollPane.border = JBUI.Borders.empty()
    scrollPane.preferredSize = computeNotBiggerDimension(checkboxList.preferredSize, owner.locationOnScreen)

    val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(scrollPane, checkboxList)
      .setTitle(title)
      .setRequestFocus(true)
      .createPopup()
    popup.setMinimumSize(JBDimension(250, 0))
    popup.showUnderneathOf(owner)
  }

  private fun computeNotBiggerDimension(ofContent: Dimension, locationOnScreen: Point?): Dimension {
    val maxSize = Dimension(Int.MAX_VALUE, 600)

    var resultHeight = if (ofContent.height > maxSize.height) maxSize.height else ofContent.height
    if (locationOnScreen != null) {
      val r = ScreenUtil.getScreenRectangle(locationOnScreen)
      resultHeight = resultHeight.coerceAtMost(r.height - r.height / 4)
    }
    var resultWidth = ofContent.width.coerceAtMost(maxSize.width)
    if (ofContent.height > maxSize.height) {
      resultWidth += ScrollPaneFactory.createScrollPane().verticalScrollBar.preferredSize.getWidth().toInt()
    }
    return Dimension(resultWidth, resultHeight)
  }
}