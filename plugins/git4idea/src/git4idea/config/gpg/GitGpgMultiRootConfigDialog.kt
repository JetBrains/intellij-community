// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config.gpg

import com.intellij.dvcs.DvcsUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.table.JBTable
import com.intellij.util.FontUtil
import com.intellij.util.ui.JBUI
import git4idea.i18n.GitBundle.message
import git4idea.repo.GitRepository
import kotlinx.coroutines.*
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.table.AbstractTableModel

class GitGpgMultiRootConfigDialog(val project: Project,
                                  val secretKeys: SecretKeysValue,
                                  repoConfigs: Map<GitRepository, RepoConfigValue>) : DialogWrapper(project) {
  companion object {
    private const val ROOT_COLUMN = 0
    private const val GPG_KEY_COLUMN = 1
  }

  private val uiDispatcher get() = Dispatchers.EDT + ModalityState.any().asContextElement()
  private val scope = CoroutineScope(SupervisorJob()).also { Disposer.register(disposable) { it.cancel() } }

  private val repoConfigs = repoConfigs.map { Node(it.key, it.value) }
    .sortedBy {
      DvcsUtil.getShortRepositoryName(it.repo)
    }

  private val tableModel = GpgConfigTableModel()
  private val table: JBTable

  init {
    title = message("settings.configure.sign.gpg.for.repos.dialog.title")

    table = JBTable(tableModel).apply {
      setDefaultRenderer(Any::class.java, MyCellRenderer())
      setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
      columnModel.getColumn(ROOT_COLUMN).preferredWidth = JBUI.scale(200)
      columnModel.getColumn(GPG_KEY_COLUMN).preferredWidth = JBUI.scale(500)
      preferredScrollableViewportSize = JBUI.size(700, -1)

      object : DoubleClickListener() {
        override fun onDoubleClick(e: MouseEvent): Boolean {
          if (canEditConfig()) {
            editConfig()
            return true
          }
          return false
        }
      }.installOn(this)
    }

    init()
    initTable()
  }

  override fun createSouthAdditionalPanel(): JPanel {
    val label = JBLabel(message("settings.configure.sign.gpg.synced.with.gitconfig.text"))
    label.foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
    return JBUI.Panels.simplePanel(label)
  }

  override fun createCenterPanel(): JComponent {
    return ToolbarDecorator.createDecorator(table)
      .setEditAction { editConfig() }
      .setEditActionUpdater { canEditConfig() }
      .disableUpDownActions()
      .createPanel()
  }

  override fun createActions(): Array<Action> {
    return arrayOf(okAction)
  }

  private fun initTable() {
    scope.launch(uiDispatcher +
                 CoroutineName("GitGpgMultiRootConfigDialog - readConfig")) {
      table.setPaintBusy(true)
      try {
        for (repoConfig in repoConfigs) {
          repoConfig.config.tryLoad()
        }
        tableModel.fireTableDataChanged()

        secretKeys.tryLoad()
        table.repaint()
      }
      finally {
        table.setPaintBusy(false)
      }
    }
  }

  private fun canEditConfig(): Boolean {
    return table.selectedRowCount == 1
  }

  private fun editConfig() {
    val row = table.selectedRow
    val node = repoConfigs[row]
    GitGpgConfigDialog(node.repo, secretKeys, node.config).show()
    tableModel.fireTableRowsUpdated(row, row)
  }

  private inner class GpgConfigTableModel : AbstractTableModel() {
    override fun getRowCount() = repoConfigs.size
    override fun getColumnCount() = 2

    override fun getColumnName(column: Int): String {
      if (column == ROOT_COLUMN) return message("settings.configure.sign.gpg.root.table.column.name")
      if (column == GPG_KEY_COLUMN) return message("settings.configure.sign.gpg.gpg.kep.table.column.name")
      error("Column number: $column")
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
      val node = repoConfigs[rowIndex]
      return when (columnIndex) {
        ROOT_COLUMN -> node.repo
        GPG_KEY_COLUMN -> node.config
        else -> null
      }
    }
  }

  private inner class MyCellRenderer : ColoredTableCellRenderer() {
    override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
      if (value is GitRepository) {
        append(DvcsUtil.getShortRepositoryName(value))
      }
      else if (value is RepoConfigValue) {
        val error = value.error
        val config = value.value

        when {
          error != null -> append(message("settings.configure.sign.gpg.error.table.text", error))
          config == null -> append(message("settings.configure.sign.gpg.loading.table.text"))
          config.key == null -> append(message("settings.configure.sign.gpg.do.not.sign.table.text"))
          else -> {
            append(config.key.id)

            val descriptions = secretKeys.value?.descriptions ?: return
            val description = descriptions[config.key]
            if (description != null) {
              append(FontUtil.spaceAndThinSpace())
              append(description, SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
          }
        }
      }
    }
  }

  private class Node(val repo: GitRepository, val config: RepoConfigValue)
}
