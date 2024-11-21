// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.cloneDialog

import com.intellij.collaboration.async.cancelledWith
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtension
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionStatusLine
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionStatusLine.Companion.greyText
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.isGHAccount
import javax.swing.Icon

private val GithubAccount.nameWithServer: String
  get() {
    val serverPrefix = if (isGHAccount) "" else "${server.host}/"
    return serverPrefix + name
  }

internal abstract class GHCloneDialogExtensionBase : VcsCloneDialogExtension {
  override fun getIcon(): Icon = AllIcons.Vcs.Vendors.Github

  protected abstract fun getAccounts(): Collection<GithubAccount>

  final override fun getAdditionalStatusLines(): List<VcsCloneDialogExtensionStatusLine> {
    val accounts = getAccounts()
    if (accounts.isEmpty()) return listOf(greyText(CollaborationToolsBundle.message("clone.dialog.label.no.accounts")))

    return accounts.map { greyText(it.nameWithServer) }
  }
}

@Service(Service.Level.PROJECT)
internal class GHCloneDialogExtensionComponentFactory(private val project: Project,
                                                      private val cs: CoroutineScope) {
  fun createInScope(modalityState: ModalityState, creator: CoroutineScope.(Project) -> GHCloneDialogExtensionComponentBase)
    : GHCloneDialogExtensionComponentBase {
    val scope = cs.childScope(javaClass.name, modalityState.asContextElement())
    return scope.creator(project).also {
      scope.cancelledWith(it)
    }
  }
}