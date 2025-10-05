// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status

import com.intellij.DynamicBundle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Dimension
import java.util.*
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants
import javax.swing.table.DefaultTableModel

@Suppress("HardCodedStringLiteral")
@Internal
internal class ShowBundleMessagesDialogAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent): Unit = BundleMessagesDialog(e.project).show()

  private class BundleMessagesDialog(project: Project?) : DialogWrapper(project) {
    private lateinit var  table: JBTable
    init {
      init()
    }

    override fun getPreferredFocusedComponent(): JComponent {
      return table
    }

    override fun createCenterPanel(): JComponent {
      val bundleMap = DynamicBundle.getResourceBundles()
      table = JBTable(object : DefaultTableModel(Vector(listOf("Bundle name", "Values")), bundleMap.size) {
        override fun isCellEditable(row: Int, column: Int): Boolean {
          return false
        }

        override fun getValueAt(row: Int, column: Int): Any {
          val bundleName = bundleMap.keys.elementAtOrNull(row) ?: return ""
          if (column == 0) return bundleName
          else {
            val resourceBundle = bundleMap[bundleName] ?: return ""
            val bundleKeys = resourceBundle.keySet().toList()
            return "<html>" + bundleKeys.subList(0, if(bundleKeys.size >= 5) 5 else bundleKeys.size).joinToString("<br>") { resourceBundle.getString(it) }
          }
        }
      })
      TableSpeedSearch.installOn(table)
      val scrollPane = ScrollPaneFactory.createScrollPane(table, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS).apply {
        preferredSize = Dimension(700, 700)
      }
      return panel {
        row {
          label("<html>This table intended to verify bundle localization. Only 5 messages from each bundle are displayed here." +
                "<br>Speed search functionality is available - just start to write text.")
        }
        row {
          cell(scrollPane)
        }
      }
    }

  }
}