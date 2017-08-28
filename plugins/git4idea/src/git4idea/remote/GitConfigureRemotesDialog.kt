/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package git4idea.remote

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.DvcsUtil.sortRepositories
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapper.IdeModalityType.IDE
import com.intellij.openapi.ui.DialogWrapper.IdeModalityType.PROJECT
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil.DEFAULT_HGAP
import git4idea.commands.Git
import git4idea.commands.GitCommandResult
import git4idea.repo.GitRemote
import git4idea.repo.GitRemote.ORIGIN
import git4idea.repo.GitRepository
import java.awt.Dimension
import java.awt.Font
import java.util.*
import javax.swing.*
import javax.swing.table.AbstractTableModel

class GitConfigureRemotesDialog(val project: Project, val repositories: Collection<GitRepository>) :
    DialogWrapper(project, true, getModalityType()) {

  private val git = service<Git>()
  private val LOG = logger<GitConfigureRemotesDialog>()

  private val NAME_COLUMN = 0
  private val URL_COLUMN = 1
  private val REMOTE_PADDING = 30
  private val table = JBTable(RemotesTableModel())

  private var nodes = buildNodes(repositories)

  init {
    init()
    title = "Git Remotes"
    updateTableWidth()
  }

  override fun createActions() = arrayOf(helpAction,okAction)

  override fun getPreferredFocusedComponent() = table

  override fun createCenterPanel(): JComponent? {
    table.selectionModel = DefaultListSelectionModel()
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    table.intercellSpacing = JBUI.emptySize()
    table.setDefaultRenderer(Any::class.java, MyCellRenderer())

    return ToolbarDecorator.createDecorator(table).
        setAddAction { addRemote() }.
        setRemoveAction { removeRemote() }.
        setEditAction { editRemote() }.
        setEditActionUpdater { isRemoteSelected() }.
        setRemoveActionUpdater { isRemoteSelected() }.
        disableUpDownActions().createPanel()
  }

  private fun addRemote() {
    val repository = getSelectedRepo()
    val proposedName = if (repository.remotes.any { it.name == ORIGIN }) "" else ORIGIN
    val dialog = GitDefineRemoteDialog(repository, git, proposedName, "")
    if (dialog.showAndGet()) {
      runInModalTask("Adding Remote...", repository,
                     "Add Remote", "Couldn't add remote ${dialog.remoteName} '${dialog.remoteUrl}'") {
        git.addRemote(repository, dialog.remoteName, dialog.remoteUrl)
      }
    }
  }

  private fun removeRemote() {
    val remoteNode = getSelectedRemote()!!
    val remote = remoteNode.remote
    if (YES == showYesNoDialog(rootPane, "Remove remote ${remote.name} '${getUrl(remote)}'?", "Remove Remote", getQuestionIcon())) {
      runInModalTask("Removing Remote...", remoteNode.repository, "Remove Remote", "Couldn't remove remote $remote") {
        git.removeRemote(remoteNode.repository, remote)
      }
    }
  }

  private fun editRemote() {
    val remoteNode = getSelectedRemote()!!
    val remote = remoteNode.remote
    val repository = remoteNode.repository
    val oldName = remote.name
    val oldUrl = getUrl(remote)

    val dialog = GitDefineRemoteDialog(repository, git, oldName, oldUrl)
    if (dialog.showAndGet()) {
      val newRemoteName = dialog.remoteName
      val newRemoteUrl = dialog.remoteUrl
      if (newRemoteName == oldName && newRemoteUrl == oldUrl) return
      runInModalTask("Changing Remote...", repository,
                     "Change Remote", "Couldn't change remote $oldName to $newRemoteName '$newRemoteUrl'") {
        changeRemote(repository, oldName, oldUrl, newRemoteName, newRemoteUrl)
      }
    }
  }

  private fun changeRemote(repo: GitRepository, oldName: String, oldUrl: String, newName: String, newUrl: String): GitCommandResult {
    var result : GitCommandResult? = null
    if (newName != oldName) {
      result = git.renameRemote(repo, oldName, newName)
      if (!result.success()) return result
    }
    if (newUrl != oldUrl) {
      result = git.setRemoteUrl(repo, newName, newUrl) // NB: remote name has just been changed
    }
    return result!! // at least one of two has changed
  }

  private fun updateTableWidth() {
    var maxNameWidth = 30
    var maxUrlWidth = 250
    for (node in nodes) {
      val fontMetrics = table.getFontMetrics(UIManager.getFont("Table.font").deriveFont(Font.BOLD))
      val nameWidth = fontMetrics.stringWidth(node.getPresentableString())
      val remote = (node as? RemoteNode)?.remote
      val urlWidth = if (remote == null) 0 else fontMetrics.stringWidth(getUrl(remote))
      if (maxNameWidth < nameWidth) maxNameWidth = nameWidth
      if (maxUrlWidth < urlWidth) maxUrlWidth = urlWidth
    }
    maxNameWidth += REMOTE_PADDING + DEFAULT_HGAP

    table.columnModel.getColumn(NAME_COLUMN).preferredWidth = maxNameWidth
    table.columnModel.getColumn(URL_COLUMN).preferredWidth = maxUrlWidth

    val defaultPreferredHeight = table.rowHeight*(table.rowCount+3)
    table.preferredScrollableViewportSize = Dimension(maxNameWidth + maxUrlWidth + DEFAULT_HGAP, defaultPreferredHeight)
  }

  private fun buildNodes(repositories: Collection<GitRepository>): List<Node> {
    val nodes = mutableListOf<Node>()
    for (repository in sortRepositories(repositories)) {
      if (repositories.size > 1) nodes.add(RepoNode(repository))
      for (remote in sortedRemotes(repository)) {
        nodes.add(RemoteNode(remote, repository))
      }
    }
    return nodes
  }

  private fun sortedRemotes(repository: GitRepository): List<GitRemote> {
    return repository.remotes.sortedWith(Comparator<GitRemote> { r1, r2 ->
      if (r1.name == ORIGIN) {
        if (r2.name == ORIGIN) 0 else -1
      }
      else if (r2.name == ORIGIN) 1 else r1.name.compareTo(r2.name)
    })
  }

  private fun rebuildTable() {
    nodes = buildNodes(repositories)
    (table.model as RemotesTableModel).fireTableDataChanged()
  }

private fun runInModalTask(title: String,
                           repository: GitRepository,
                           errorTitle: String,
                           errorMessage: String,
                           operation: () -> GitCommandResult) {
    ProgressManager.getInstance().run(object : Task.Modal(project, title, true) {
      private var result: GitCommandResult? = null

      override fun run(indicator: ProgressIndicator) {
        result = operation()
        repository.update()
      }

      override fun onSuccess() {
        rebuildTable()
        if (result == null || !result!!.success()) {
          val errorDetails = if (result == null) "operation was not executed" else result!!.errorOutputAsJoinedString
          val message = "$errorMessage in $repository:\n$errorDetails"
          LOG.warn(message)
          Messages.showErrorDialog(myProject, message, errorTitle)
        }
      }
    })
  }

  private fun getSelectedRepo(): GitRepository {
    val selectedRow = table.selectedRow
    if (selectedRow < 0) return sortRepositories(repositories).first()
    val value = nodes[selectedRow]
    if (value is RepoNode) return value.repository
    if (value is RemoteNode) return value.repository
    throw IllegalStateException("Unexpected selected value: $value")
  }

  private fun getSelectedRemote() : RemoteNode? {
    val selectedRow = table.selectedRow
    if (selectedRow < 0) return null
    return nodes[selectedRow] as? RemoteNode
  }

  private fun isRemoteSelected() = getSelectedRemote() != null

  private fun getUrl(remote: GitRemote) = remote.urls.firstOrNull() ?: ""

  private abstract class Node {
    abstract fun getPresentableString() : String
  }
  private class RepoNode(val repository: GitRepository) : Node() {
    override fun toString() = repository.presentableUrl
    override fun getPresentableString() = DvcsUtil.getShortRepositoryName(repository)
  }
  private class RemoteNode(val remote: GitRemote, val repository: GitRepository) : Node() {
    override fun toString() = remote.name
    override fun getPresentableString() = remote.name
  }

  private inner class RemotesTableModel() : AbstractTableModel() {
    override fun getRowCount() = nodes.size
    override fun getColumnCount() = 2

    override fun getColumnName(column: Int): String {
      if (column == NAME_COLUMN) return "Name"
      else return "URL"
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
      val node = nodes[rowIndex]
      when {
        columnIndex == NAME_COLUMN -> return node
        node is RepoNode -> return ""
        node is RemoteNode -> return getUrl(node.remote)
        else -> {
          LOG.error("Unexpected position at row $rowIndex and column $columnIndex")
          return ""
        }
      }
    }
  }

  private inner class MyCellRenderer : ColoredTableCellRenderer() {
    override fun customizeCellRenderer(table: JTable?, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
      if (value is RepoNode) {
        append(value.getPresentableString(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
      }
      else if (value is RemoteNode) {
        if (repositories.size > 1) append("", SimpleTextAttributes.REGULAR_ATTRIBUTES, REMOTE_PADDING, SwingConstants.LEFT)
        append(value.getPresentableString())
      }
      else if (value is String) {
        append(value)
      }
      border = null
    }
  }

  override fun getHelpId() = "VCS.Git.Remotes";

}

private fun getModalityType() = if (Registry.`is`("ide.perProjectModality")) PROJECT else IDE
