// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.attach.dialog.items.columns

import com.intellij.ide.util.PropertiesComponent
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachToProcessElementsFilters
import com.intellij.xdebugger.impl.ui.attach.dialog.items.cells.AttachTableCell
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachDialogProcessNode
import org.jetbrains.annotations.Nls

abstract class AttachDialogColumnsLayout {

  internal abstract fun getColumnInfos(): List<AttachDialogColumnInfo>

  internal abstract fun createCell(columnIndex: Int, node: AttachDialogProcessNode, filters: AttachToProcessElementsFilters, isInsideTree: Boolean): AttachTableCell

  fun getColumnsCount(): Int = getColumnInfos().size


  fun getColumnClass(columnKey: String): Class<*> = getColumnInfos().first { it.columnKey == columnKey }.columnClass

  fun getColumnClass(columnIndex: Int): Class<*> = getColumnInfos()[columnIndex].columnClass

  @Nls
  fun getColumnName(columnKey: String): String = getColumnInfos().first { it.columnKey == columnKey }.columnHeader

  fun getColumnName(columnIndex: Int): String = getColumnInfos()[columnIndex].columnHeader

  fun getColumnWidth(columnKey: String): Int {
    return PropertiesComponent.getInstance().getInt("${columnKey}_Width", getDefaultColumnWidth(columnKey))
  }

  fun setColumnWidth(columnKey: String, value: Int) {
    PropertiesComponent.getInstance().setValue("${columnKey}_Width", value, getDefaultColumnWidth(columnKey))
  }

  fun getMinimumViewWidth(): Int = getColumnInfos().map { it.defaultColumnWidth }.reduce { sum, value -> sum + value }

  @Suppress("unused")
  fun getColumnIndex(columnKey: String): Int = getColumnInfos().indexOfFirst { it.columnKey == columnKey }

  fun getColumnKey(columnIndex: Int): String = getColumnInfos()[columnIndex].columnKey

  private fun getDefaultColumnWidth(columnKey: String) = getColumnInfos().first { it.columnKey == columnKey }.defaultColumnWidth
}

internal data class AttachDialogColumnInfo(
  val columnKey: String,
  val columnClass: Class<*>,
  @param:Nls val columnHeader: String,
  val defaultColumnWidth: Int)