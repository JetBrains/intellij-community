// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.clone

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtension
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionComponent
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionStatusLine
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.plus
import org.jetbrains.plugins.gitlab.GitlabIcons
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import javax.swing.Icon

class GitLabCloneDialogExtension : VcsCloneDialogExtension {
  private val accountManager: GitLabAccountManager = service<GitLabAccountManager>()

  override fun getName(): String = SERVICE_DISPLAY_NAME

  override fun getIcon(): Icon = GitlabIcons.GitLabLogo

  override fun getAdditionalStatusLines(): List<VcsCloneDialogExtensionStatusLine> {
    val accounts = accountManager.accountsState.value
    return if (accounts.isEmpty())
      listOf(VcsCloneDialogExtensionStatusLine.greyText(CollaborationToolsBundle.message("clone.dialog.label.no.accounts")))
    else
      accounts.map { account ->
        VcsCloneDialogExtensionStatusLine.greyText(account.nameWithServer)
      }
  }

  override fun createMainComponent(project: Project, modalityState: ModalityState): VcsCloneDialogExtensionComponent {
    val cs = MainScope() + modalityState.asContextElement()
    val cloneVm = GitLabCloneViewModelImpl(project, cs, accountManager)
    return GitLabCloneComponent(project, cs, cloneVm)
  }

  private val GitLabAccount.nameWithServer: String
    get() {
      val serverPrefix = if (server.isDefault) "" else "${server.uri}/"
      return serverPrefix + name
    }

  companion object {
    private const val SERVICE_DISPLAY_NAME: @NlsSafe String = "GitLab"
  }
}