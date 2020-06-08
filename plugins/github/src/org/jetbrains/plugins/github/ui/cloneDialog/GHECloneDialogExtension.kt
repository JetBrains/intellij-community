// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.cloneDialog

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionComponent
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountInformationProvider
import org.jetbrains.plugins.github.util.CachingGithubUserAvatarLoader
import org.jetbrains.plugins.github.util.GithubImageResizer
import org.jetbrains.plugins.github.util.GithubUtil

private fun getGHEAccounts(): Collection<GithubAccount> =
  GithubAuthenticationManager.getInstance().getAccounts().filterNot { it.server.isGithubDotCom }

class GHECloneDialogExtension : BaseCloneDialogExtension() {
  override fun getName(): String = GithubUtil.ENTERPRISE_SERVICE_DISPLAY_NAME

  override fun getAccounts(): Collection<GithubAccount> = getGHEAccounts()

  override fun createMainComponent(project: Project, modalityState: ModalityState): VcsCloneDialogExtensionComponent =
    object : BaseCloneDialogExtensionComponent(
      project,
      GithubAuthenticationManager.getInstance(),
      GithubApiRequestExecutorManager.getInstance(),
      GithubAccountInformationProvider.getInstance(),
      CachingGithubUserAvatarLoader.getInstance(),
      GithubImageResizer.getInstance()
    ) {

      init {
        setup()
      }

      override fun getAccounts(): Collection<GithubAccount> = getGHEAccounts()
    }
}