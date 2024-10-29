// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.attach.dialog

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.observable.properties.AtomicLazyProperty
import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachToProcessItemsListBase
import com.intellij.xdebugger.impl.ui.attach.dialog.statistics.AttachDialogStatisticsCollector

class AttachDialogState(val dialogDisposable: Disposable, val dataContext: DataContext) {

  companion object {
    private const val IS_ATTACH_VIEW_TREE_ENABLED = "ATTACH_VIEW_TREE_ENABLED"

    private fun getDefaultView(): AttachViewType =
      if (PropertiesComponent.getInstance().getBoolean(IS_ATTACH_VIEW_TREE_ENABLED, false))
        AttachViewType.TREE
      else
        AttachViewType.LIST

    val COLUMN_MINIMUM_WIDTH: Int
      get() = JBUI.scale(20)
    val DEFAULT_ROW_HEIGHT: Int
      get() = JBUI.scale(24)
  }

  val searchFieldValue = AtomicLazyProperty { "" }
  val selectedDebuggerItem = AtomicLazyProperty<AttachDialogProcessItem?> { null }
  val currentList = AtomicLazyProperty<AttachToProcessItemsListBase?> { null }
  val itemWasDoubleClicked = AtomicLazyProperty { false }
  val selectedDebuggersFilter = AtomicLazyProperty<AttachDialogDebuggersFilter> { AttachDialogAllDebuggersFilter }

  val selectedViewType = AtomicLazyProperty { getDefaultView() }

  init {
    selectedViewType.afterChange {
      if (getDefaultView() != it) {
        AttachDialogStatisticsCollector.viewSwitched(it)
      }
      PropertiesComponent.getInstance().setValue(IS_ATTACH_VIEW_TREE_ENABLED, it == AttachViewType.TREE)
    }
  }
}