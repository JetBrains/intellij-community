// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.util.GitRemoteUrlCoordinates
import javax.swing.JComponent

internal class GHPRToolWindowComponentFactory(private val project: Project) {

  @CalledInAwt
  fun createComponent(remoteUrl: GitRemoteUrlCoordinates, disposable: Disposable): JComponent {
    return GHPRAccountsComponent(GithubAuthenticationManager.getInstance(), project, remoteUrl, disposable)
  }
}
