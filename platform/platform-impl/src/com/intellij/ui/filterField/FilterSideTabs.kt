package com.intellij.ui.filterField

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.project.Project
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.impl.SingleHeightTabs
import com.intellij.ui.tabs.impl.TabLabel
import com.intellij.util.ui.JBUI

class FilterSideTabs(project: Project, disposable: Disposable): SingleHeightTabs(project, disposable) {
  override fun createTabLabel(info: TabInfo): TabLabel {
    return object : SingleHeightLabel(this, info) {
      override fun getPreferredHeight(): Int {
        return ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.height +
               getActionButtonIndent() +
               getActionToolbarIndent() +
               getThinBorderThickness() +
               getFiltersEmptyBorder()
      }
    }
  }

  private fun getFiltersEmptyBorder(): Int = 2 * JBUI.scale(2)

  private fun getActionButtonIndent(): Int = 2 * JBUI.scale(1)

  private fun getActionToolbarIndent(): Int = 2 * JBUI.scale(2)

  private fun getThinBorderThickness(): Int = 1
}