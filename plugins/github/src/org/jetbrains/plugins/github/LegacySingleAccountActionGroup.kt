// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import git4idea.DialogManager
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.util.GithubAccountsMigrationHelper
import org.jetbrains.plugins.github.util.GithubGitHelper
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JTextArea

abstract class LegacySingleAccountActionGroup(text: String?, description: String?, icon: Icon?) : DumbAwareAction(text, description, icon) {
  override fun update(e: AnActionEvent?) {
    if (e == null) return
    val project = e.getData(CommonDataKeys.PROJECT)
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
    if (project == null || project.isDefault) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val gitRepository = GithubGitHelper.findGitRepository(project, file)
    if (gitRepository == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    if (getAccountsForRemotes(project, gitRepository).isEmpty()
        && service<GithubAccountsMigrationHelper>().getOldServer()?.let { getRemote(it, gitRepository) } == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isEnabledAndVisible = true
  }

  override fun actionPerformed(e: AnActionEvent?) {
    if (e == null) return
    val project = e.getData(CommonDataKeys.PROJECT)
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
    if (project == null || project.isDefault) return

    val gitRepository = GithubGitHelper.findGitRepository(project, file)
    if (gitRepository == null) return
    gitRepository.update()

    if (!service<GithubAccountsMigrationHelper>().migrate(project)) return
    val accounts = getAccountsForRemotes(project, gitRepository)
    // can happen if migration was cancelled
    if (accounts.isEmpty()) return
    val account = if (accounts.size == 1) accounts.first()
    else {
      val dialog = ChooseAccountDialog(project, accounts)
      DialogManager.show(dialog)
      if (!dialog.isOK) return
      dialog.getAccount()
    }

    actionPerformed(project, file, gitRepository, account)
  }

  abstract fun actionPerformed(project: Project, file: VirtualFile?, gitRepository: GitRepository, account: GithubAccount)

  private fun getAccountsForRemotes(project: Project, repository: GitRepository): List<GithubAccount> {
    val authenticationManager = service<GithubAuthenticationManager>()
    val defaultAccount = authenticationManager.getDefaultAccount(project)
    return if (defaultAccount != null && getRemote(defaultAccount.server, repository) != null)
      listOf(defaultAccount)
    else {
      authenticationManager.getAccounts().filter { getRemote(it.server, repository) != null }
    }
  }

  protected abstract fun getRemote(server: GithubServerPath, repository: GitRepository): Pair<GitRemote, String>?
}

private class ChooseAccountDialog(project: Project, accounts: List<GithubAccount>) : DialogWrapper(project) {
  private val text = JTextArea().apply {
    font = UIUtil.getLabelFont()
    text = "Default account is not configured for this project. Choose Github account:"
    lineWrap = true
    wrapStyleWord = true
    isEditable = false
    isFocusable = false
    isOpaque = false
    border = null
    margin = JBUI.emptyInsets()
  }
  private val list = JBList<GithubAccount>(accounts).apply {
    cellRenderer = object : ColoredListCellRenderer<GithubAccount>() {
      override fun customizeCellRenderer(list: JList<out GithubAccount>,
                                         value: GithubAccount,
                                         index: Int,
                                         selected: Boolean,
                                         hasFocus: Boolean) {
        append(value.name)
        append(" ")
        append(value.server.toString(), SimpleTextAttributes.GRAYED_ATTRIBUTES)
        border = JBUI.Borders.empty(0, UIUtil.DEFAULT_HGAP)
      }
    }
  }

  init {
    title = "Choose Github Account"
    setOKButtonText("Choose")
    init()
  }

  override fun doValidate(): ValidationInfo? {
    return if (list.selectedValue == null) ValidationInfo("Account is not selected", list) else null
  }

  fun getAccount(): GithubAccount = list.selectedValue

  override fun createCenterPanel(): JComponent? {
    return JBUI.Panels.simplePanel(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP)
      .addToCenter(JBScrollPane(list).apply { preferredSize = JBDimension(150, 80) })
      .addToTop(text)
  }
}