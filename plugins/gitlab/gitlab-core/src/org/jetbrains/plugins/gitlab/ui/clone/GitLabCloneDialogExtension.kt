// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.clone

import com.intellij.collaboration.async.cancelledWith
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtension
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionComponent
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionStatusLine
import com.intellij.platform.util.coroutines.childScope
import com.intellij.vcs.gitlab.icons.GitlabIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.plus
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.ui.clone.model.GitLabCloneViewModelImpl
import javax.swing.Icon

class GitLabCloneDialogExtension : VcsCloneDialogExtension {
  override fun getName(): String = SERVICE_DISPLAY_NAME

  override fun getIcon(): Icon = GitlabIcons.GitLabLogo

  override fun getAdditionalStatusLines(): List<VcsCloneDialogExtensionStatusLine> {
    val accounts = service<GitLabAccountManager>().accountsState.value
    return if (accounts.isEmpty())
      listOf(VcsCloneDialogExtensionStatusLine.greyText(CollaborationToolsBundle.message("clone.dialog.label.no.accounts")))
    else
      accounts.map { account ->
        VcsCloneDialogExtensionStatusLine.greyText(account.nameWithServer)
      }
  }

  override fun createMainComponent(project: Project, modalityState: ModalityState): VcsCloneDialogExtensionComponent =
    project.service<GitLabCloneDialogExtensionComponentFactory>().create(modalityState)

  private val GitLabAccount.nameWithServer: String
    get() {
      val serverPrefix = if (server.isDefault) "" else "${server.uri}/"
      return serverPrefix + name
    }

  companion object {
    private const val SERVICE_DISPLAY_NAME: @NlsSafe String = "GitLab"
  }
}

@Service(Service.Level.PROJECT)
internal class GitLabCloneDialogExtensionComponentFactory(private val project: Project, private val cs: CoroutineScope) {
  fun create(modalityState: ModalityState): GitLabCloneComponent {
    // service scope -> vm scope -> ui scope
    val vmCs = cs.childScope(javaClass.name, modalityState.asContextElement())
    val vm = GitLabCloneViewModelImpl(project, vmCs + Dispatchers.Default, service<GitLabAccountManager>())

    val componentCs = vmCs.childScope("GitLab clone dialog component")
    val component = GitLabCloneComponent(project, componentCs, vm).also {
      vmCs.cancelledWith(it)
    }
    return component
  }
}