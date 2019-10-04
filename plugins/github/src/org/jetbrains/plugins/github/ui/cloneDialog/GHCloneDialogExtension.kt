// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.cloneDialog

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtension
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionComponent
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionStatusLine
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountInformationProvider
import org.jetbrains.plugins.github.util.CachingGithubUserAvatarLoader
import org.jetbrains.plugins.github.util.GithubImageResizer
import org.jetbrains.plugins.github.util.GithubUtil
import javax.swing.Icon

class GHCloneDialogExtension : VcsCloneDialogExtension {
  private val authenticationManager = GithubAuthenticationManager.getInstance()

  override fun getName() = GithubUtil.SERVICE_DISPLAY_NAME

  override fun getIcon(): Icon = AllIcons.Vcs.Vendors.Github

  override fun getAdditionalStatusLines(): List<VcsCloneDialogExtensionStatusLine> {
    if (!authenticationManager.hasAccounts()) {
      return listOf(VcsCloneDialogExtensionStatusLine.greyText("No accounts"))
    }

    val list = ArrayList<VcsCloneDialogExtensionStatusLine>()
    for (account in authenticationManager.getAccounts()) {
      val accName = if (account.server.isGithubDotCom) account.name else ("${account.server.host}/${account.name}")
      list.add(VcsCloneDialogExtensionStatusLine.greyText(accName))
    }
    return list
  }

  override fun createMainComponent(project: Project): VcsCloneDialogExtensionComponent {
    return GHCloneDialogExtensionComponent(project,
                                           GithubAuthenticationManager.getInstance(),
                                           GithubApiRequestExecutorManager.getInstance(),
                                           GithubApiRequestExecutor.Factory.getInstance(),
                                           GithubAccountInformationProvider.getInstance(),
                                           CachingGithubUserAvatarLoader.getInstance(),
                                           GithubImageResizer.getInstance()
    )
  }
}