// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import javax.swing.JPanel
import javax.swing.table.DefaultTableModel
import java.awt.BorderLayout

class LockReqsToolWindowFactorySwing : ToolWindowFactory {

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val service = project.service<LockReqsService>()

    val tableModel = DefaultTableModel(arrayOf("Execution Path"), 0)
    val panel = JPanel(BorderLayout())
    val table = JBTable(tableModel)
    val scrollPane = JBScrollPane(table)
    panel.add(scrollPane)

    service.onResultsUpdated = { loadData(service.currentResult!!.paths.map{it.pathString}, tableModel) }
    val factory = toolWindow.contentManager.factory
    val content = factory.createContent(panel, null, false)
    toolWindow.contentManager.addContent(content)
  }

  private fun loadData(results: List<String>, tableModel: DefaultTableModel) {
    tableModel.rowCount = 0
    results.forEach { tableModel.addRow(arrayOf(it)) }
  }
}

