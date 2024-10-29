// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector

import com.intellij.codeInsight.hint.HintManager
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.impl.ToolbarComboWidget
import com.intellij.openapi.wm.impl.status.TextPanel
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.breadcrumbs.Breadcrumbs
import com.intellij.util.ui.ComponentWithEmptyText
import java.awt.Component
import java.awt.Container
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseEvent
import javax.accessibility.AccessibleText
import javax.swing.*
import javax.swing.border.TitledBorder
import javax.swing.plaf.basic.BasicComboPopup
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.JTableHeader
import javax.swing.table.TableCellRenderer
import javax.swing.text.JTextComponent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.TreeCellRenderer

internal class CopyUiLabelAction : UiMouseAction("CopyUiLabel") {

  override fun handleClick(component: Component, event: MouseEvent?) {
    val showFullDescription = event?.isShiftDown ?: false
    val text = when {
      showFullDescription -> {
        val text = mutableListOf<String?>()

        text += getComponentText(component)

        var parent: Component? = component
        while (parent != null) {
          text += getComponentContextText(parent)
          parent = parent.parent
        }

        text.join("\n")
      }
      else -> getComponentText(component)
    }
    if (text.isNullOrBlank()) return

    CopyPasteManager.getInstance().setContents(StringSelection(text))

    val anchor = findJComponentFor(component)
    if (anchor != null) {
      val message = "Copied: '${StringUtil.shortenTextWithEllipsis(text, 30, 0)}'"
      HintManager.getInstance().showHint(JLabel(message),
                                         RelativePoint.getSouthWestOf(anchor),
                                         HintManager.HIDE_BY_ANY_KEY or HintManager.HIDE_BY_OTHER_HINT, 0)
    }
  }

  private fun getComponentContextText(c: Component): List<String?> {
    val text = mutableListOf<String?>()

    if (c is ComponentWithEmptyText) {
      text += c.emptyText.toString()
    }

    if (c is EditorComponentImpl) {
      text += c.editor.placeholder.toString()
    }

    if (c is JComponent) {
      text += c.toolTipText

      val helpTooltip = HelpTooltip.getTooltipFor(c)
      if (helpTooltip != null) {
        text += getComponentText(helpTooltip.createTipPanel())
      }

      val border = c.border
      if (border is TitledBorder) {
        text += border.title
      }
    }

    return text
  }

  private fun List<String?>.join(separator: String) = this.filterNotNull().filter { it.isNotBlank() }.joinToString(separator = separator)

  @Suppress("UNCHECKED_CAST")
  private fun getComponentText(c: Component): String? {
    return when (c) {
      is JLabel -> c.text
      is AbstractButton -> c.text
      is SimpleColoredComponent -> c.toString()
      is JTextComponent -> c.text
      is TextPanel -> c.text
      is JComboBox<*> -> {
        when (val item = c.selectedItem) {
          null, is String -> item?.toString()
          else -> {
            val renderer: ListCellRenderer<Any> = (c.renderer ?: DefaultListCellRenderer()) as ListCellRenderer<Any>
            val jList = BasicComboPopup(c as JComboBox<Any?>?).list
            val rendererComponent = renderer.getListCellRendererComponent(jList, item, -1, false, false)
            getComponentText(rendererComponent)
          }
        }
      }
      is JList<*> -> {
        val text = mutableListOf<String?>()
        val model = c.model
        for (i in 0 until model.size) {
          val item = model.getElementAt(i)
          val renderer: ListCellRenderer<Any> = (c.cellRenderer ?: DefaultListCellRenderer()) as ListCellRenderer<Any>
          val rendererComponent = renderer.getListCellRendererComponent(c, item, i, false, false)
          text += getComponentText(rendererComponent)
        }
        text.join("\n")
      }
      is JTable -> {
        val text = mutableListOf<String?>()
        for (i in c.selectedRows) {
          val rowText = mutableListOf<String?>()
          for (j in 0 until c.columnCount) {
            val value = c.getValueAt(i, j)
            val renderer: TableCellRenderer = c.getCellRenderer(i, j) ?: DefaultTableCellRenderer()
            val rendererComponent = renderer.getTableCellRendererComponent(c, value, false, false, i, j)
            rowText += getComponentText(rendererComponent)
          }
          text += rowText.join("\t")
        }
        text.join("\n")
      }
      is JTableHeader -> {
        val text = mutableListOf<String?>()
        val columnModel = c.table.columnModel
        for (i in 0 until columnModel.columnCount) {
          val column = columnModel.getColumn(i)
          val renderer = column.headerRenderer ?: c.defaultRenderer
          val rendererComponent = renderer.getTableCellRendererComponent(c.table, column.headerValue, false, false, -1, i)
          text += getComponentText(rendererComponent)
        }
        text.join(", ")
      }
      is JTree -> {
        val text = mutableListOf<String?>()
        for (i in c.selectionRows ?: intArrayOf()) {
          val treePath = c.getPathForRow(i)
          val node = treePath.lastPathComponent as? DefaultMutableTreeNode ?: continue
          val renderer: TreeCellRenderer = c.cellRenderer ?: DefaultTreeCellRenderer()
          val rendererComponent = renderer.getTreeCellRendererComponent(c, node, false, false, true, i, false)
          text += "\t".repeat(treePath.pathCount) + getComponentText(rendererComponent)
        }
        text.join("\n")
      }
      is Breadcrumbs -> {
        c.crumbs.toList().map { it.text }.join(", ")
      }
      is ToolbarComboWidget -> {
        c.text
      }
      is Container -> {
        val text = mutableListOf<String?>()
        for (i in 0 until c.componentCount) {
          val child = c.getComponent(i) ?: continue
          if (!child.isVisible) continue
          text += getComponentText(child)
        }
        text.join("\n")
      }
      else -> {
        val accessibleText = c.accessibleContext?.accessibleText
        accessibleText?.getAtIndex(AccessibleText.SENTENCE, 0) ?: c.toString()
      }
    }
  }

  private fun findJComponentFor(component: Component?): JComponent? {
    var parent = component
    while (parent != null) {
      if (parent is JComponent) return parent
      parent = parent.parent
    }
    return null
  }
}