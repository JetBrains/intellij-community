// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.exception

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.openapi.project.Project
import com.intellij.util.asSafely
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.authentication.GitLabLoginSource
import org.jetbrains.plugins.gitlab.authentication.GitLabLoginUtil
import org.jetbrains.plugins.gitlab.authentication.LoginResult
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JComponent

internal sealed class GitLabHttpStatusErrorAction(@Nls name: String) : AbstractAction(name) {
  class LogInAgain(
    private val project: Project,
    private val parentScope: CoroutineScope,
    private val account: GitLabAccount,
    private val accountManager: GitLabAccountManager,
    private val loginSource: GitLabLoginSource,
    private val resetAction: () -> Unit = {}
  ) : GitLabHttpStatusErrorAction(CollaborationToolsBundle.message("login.again.action.text")) {
    override fun actionPerformed(event: ActionEvent) {
      val parentComponent = event.source as? JComponent ?: return
      val loginResult = GitLabLoginUtil.updateToken(project, parentComponent, account, loginSource) { _, _ -> true }
                          .asSafely<LoginResult.Success>()
                        ?: return
      parentScope.launch {
        accountManager.updateAccount(account, loginResult.token)
        resetAction()
      }
    }
  }
}