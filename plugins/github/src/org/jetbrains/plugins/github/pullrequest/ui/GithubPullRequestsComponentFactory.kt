// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import javax.swing.JComponent
import javax.swing.JPanel

class GithubPullRequestsComponentFactory {
  fun createComponent(remoteUrl: String, account: GithubAccount): JComponent? = JPanel()
}