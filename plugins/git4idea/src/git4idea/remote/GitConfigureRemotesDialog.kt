// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package git4idea.remote

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.DvcsUtil.sortRepositories
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapper.IdeModalityType.IDE
import com.intellij.openapi.ui.Messages.*
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.UIUtil.DEFAULT_HGAP
import com.intellij.xml.util.XmlStringUtil
import git4idea.commands.Git
import git4idea.commands.GitCommandResult
import git4idea.fetch.GitFetchSupport
import git4idea.i18n.GitBundle.message
import git4idea.repo.GitRemote
import git4idea.repo.GitRemote.Companion.ORIGIN
import git4idea.repo.GitRepository
import org.jetbrains.annotations.Nls
import java.awt.Font
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.table.AbstractTableModel
import kotlin.math.min

private val LOG = logger<GitConfigureRemotesDialog>()

class GitConfigureRemotesDialog(val project: Project, val repositories: Collection<GitRepository>) :
    DialogWrapper(project, true, IDE) {

  private val git = Git.getInstance()

  private val NAME_COLUMN = 0
  private val URL_COLUMN = 1
  private val REMOTE_PADDING = 30
  private val table = JBTable(RemotesTableModel())

  private var nodes = buildNodes(repositories)

  init {
    init()
    title = message("remotes.dialog.title")
    updateTableWidth()
  }

  override fun getDimensionServiceKey(): String = javaClass.name

  override fun createActions(): Array<Action> = arrayOf(okAction)

  override fun getPreferredFocusedComponent(): JBTable = table

  override fun createCenterPanel(): JComponent {
    table.selectionModel = DefaultListSelectionModel()
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    table.intercellSpacing = JBUI.emptySize()
    table.setDefaultRenderer(Any::class.java, MyCellRenderer())

    object : DoubleClickListener() {
      override fun onDoubleClick(e: MouseEvent): Boolean {
        if (isRemoteSelected()) {
          editRemote()
          return true
        }
        return false
      }
    }.installOn(table)

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
      runInModalTask(message("remotes.dialog.adding.remote"),
                     message("remote.dialog.add.remote"),
                     message("remotes.dialog.cannot.add.remote.error.message", dialog.remoteName, dialog.remoteUrl),
                     repository, { if (dialog.shouldFetch) doFetch(repository, dialog.remoteName, dialog.remoteUrl) },
                     onSuccess = rebuildTreeOnSuccess) {
        arrayListOf<GitCommandResult>().apply {
          add(git.addRemote(repository, dialog.remoteName, dialog.remoteUrl))
        }
      }
    }
  }

  private fun removeRemote() {
    val remoteNode = getSelectedRemote()!!
    val remote = remoteNode.remote
    val repository = remoteNode.repository

    removeRemotes(git, repository, setOf(remote), rebuildTreeOnSuccess)
  }

  private fun editRemote() {
    val remoteNode = getSelectedRemote()!!
    val remote = remoteNode.remote
    val repository = remoteNode.repository

    editRemote(git, repository, remote, rebuildTreeOnSuccess)
  }

  private fun updateTableWidth() {
    var maxNameWidth = 30
    var maxUrlWidth = 250
    for (node in nodes) {
      val fontMetrics = table.getFontMetrics(UIManager.getFont("Table.font").deriveFont(Font.BOLD))
      val nameWidth = fontMetrics.stringWidth(node.getPresentableString())
      val remote = (node as? RemoteNode)?.remote
      val urlWidth = if (remote == null) 0 else fontMetrics.stringWidth(remote.url)
      if (maxNameWidth < nameWidth) maxNameWidth = nameWidth
      if (maxUrlWidth < urlWidth) maxUrlWidth = urlWidth
    }
    maxNameWidth += REMOTE_PADDING + DEFAULT_HGAP

    table.columnModel.getColumn(NAME_COLUMN).preferredWidth = maxNameWidth
    table.columnModel.getColumn(URL_COLUMN).preferredWidth = maxUrlWidth

    table.preferredScrollableViewportSize = JBUI.size(maxNameWidth + maxUrlWidth + DEFAULT_HGAP, -1)
    table.visibleRowCount = min(nodes.size + 3, 8)
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

  private val rebuildTreeOnSuccess: () -> Unit = { rebuildTable() }

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

  private abstract class Node {
    @Nls
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

  private inner class RemotesTableModel : AbstractTableModel() {
    override fun getRowCount() = nodes.size
    override fun getColumnCount() = 2

    override fun getColumnName(column: Int): String {
      if (column == NAME_COLUMN) return message("remotes.remote.column.name")
      else return message("remotes.remote.column.url")
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
      val node = nodes[rowIndex]
      when {
        columnIndex == NAME_COLUMN -> return node
        node is RepoNode -> return ""
        node is RemoteNode -> return node.remote.url
        else -> {
          LOG.error("Unexpected position at row $rowIndex and column $columnIndex")
          return ""
        }
      }
    }
  }

  private inner class MyCellRenderer : ColoredTableCellRenderer() {
    override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
      when (value) {
        is RepoNode -> {
          append(value.getPresentableString(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        }
        is RemoteNode -> {
          if (repositories.size > 1) append("", SimpleTextAttributes.REGULAR_ATTRIBUTES, REMOTE_PADDING, SwingConstants.LEFT)
          append(value.getPresentableString())
        }
        is String -> {
          append(value)
        }
      }
      border = null
    }
  }
}

fun removeRemotes(git: Git, repository: GitRepository, remotes: Set<GitRemote>, onSuccess: () -> Unit = {}) {
  if (YES == showYesNoDialog(repository.project,
                             message("remotes.dialog.remove.remote.message", remotes.size, remotes.toStringRepresentation()),
                             message("remotes.dialog.remove.remote.title", remotes.size), getQuestionIcon())) {
    runInModalTask(message("remotes.dialog.removing.remote.progress", remotes.size),
                   message("remotes.dialog.removing.remote.error.title", remotes.size),
                   message("remotes.dialog.removing.remote.error.message", remotes.size, remotes.toStringRepresentation()),
                   repository, onSuccess = onSuccess) {
      arrayListOf<GitCommandResult>().apply {
        for (remote in remotes) {
          add(git.removeRemote(repository, remote))
        }
      }
    }
  }
}

fun editRemote(git: Git, repository: GitRepository, remote: GitRemote, onSuccess: () -> Unit = {}) {
  val oldName = remote.name
  val oldUrl = remote.url
  val dialog = GitDefineRemoteDialog(repository, git, oldName, oldUrl)
  if (dialog.showAndGet()) {
    val newRemoteName = dialog.remoteName
    val newRemoteUrl = dialog.remoteUrl
    if (newRemoteName == oldName && newRemoteUrl == oldUrl) return
    runInModalTask(message("remotes.changing.remote.progress"),
                   message("remotes.changing.remote.error.title"),
                   message("remotes.changing.remote.error.message", oldName, newRemoteName, newRemoteUrl),
                   repository, { if (dialog.shouldFetch) doFetch(repository, newRemoteName, newRemoteUrl) }, onSuccess) {
      arrayListOf<GitCommandResult>().apply {
        add(changeRemote(git, repository, oldName, oldUrl, newRemoteName, newRemoteUrl))
      }
    }
  }
}

private fun doFetch(repository: GitRepository, remoteName: String, remoteUrl: String) {
  val remoteToFetch = repository.remotes.find { remote -> remote.name == remoteName
                                                          && remote.urls.any { url -> url == remoteUrl } }
  if (remoteToFetch != null) {
    GitFetchSupport.fetchSupport(repository.project)
      .fetch(repository, remoteToFetch).showNotificationIfFailed()
  }
}

private val GitRemote.url: String get() = urls.firstOrNull() ?: ""

private fun Set<GitRemote>.toStringRepresentation() =
  if (size == 1) with(first()){"$name '$url'"} else "\n${joinToString(separator = "\n") {"${it.name} '${it.url}'" }}"

private fun changeRemote(git: Git, repo: GitRepository, oldName: String, oldUrl: String, newName: String, newUrl: String): GitCommandResult {
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

private fun runInModalTask(@Nls(capitalization = Nls.Capitalization.Title) title: String,
                           @Nls(capitalization = Nls.Capitalization.Title) errorTitle: String,
                           @Nls(capitalization = Nls.Capitalization.Sentence) errorMessage: String,
                           repository: GitRepository,
                           afterRepoRefresh: () -> Unit = {},
                           onSuccess: () -> Unit,
                           operation: () -> List<GitCommandResult>?) {
  ProgressManager.getInstance().run(object : Task.Modal(repository.project, title, true) {
    private var results: List<GitCommandResult>? = null

    override fun run(indicator: ProgressIndicator) {
      results = operation()
      repository.update()
      afterRepoRefresh()
    }

    override fun onSuccess() {
      onSuccess()
      if (results == null || results!!.any { !it.success() }) {
        val errorDetails =
          if (results == null) message("remotes.operation.not.executed.message")
          else results!!.joinToString(separator = UIUtil.BR) { it.errorOutputAsHtmlString }
        val message = message("remotes.operation.error.message", errorMessage, repository, errorDetails)
        LOG.warn(message)
        showErrorDialog(myProject, XmlStringUtil.wrapInHtml(message), errorTitle)
      }
    }
  })
}
