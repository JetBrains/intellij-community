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

class GHCloneDialogExtension : BaseCloneDialogExtension() {
  override fun getName() = GithubUtil.SERVICE_DISPLAY_NAME

  override fun getAccounts(): Collection<GithubAccount> =
    GithubAuthenticationManager.getInstance().getAccounts().filter { it.server.isGithubDotCom }

  override fun createMainComponent(project: Project, modalityState: ModalityState): VcsCloneDialogExtensionComponent =
    GHCloneDialogExtensionComponent(
      project,
      GithubAuthenticationManager.getInstance(),
      GithubApiRequestExecutorManager.getInstance(),
      GithubAccountInformationProvider.getInstance(),
      CachingGithubUserAvatarLoader.getInstance(),
      GithubImageResizer.getInstance()
    )
}