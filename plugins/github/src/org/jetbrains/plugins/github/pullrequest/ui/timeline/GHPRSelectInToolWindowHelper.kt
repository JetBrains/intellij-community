// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.github.pullrequest.GHPRToolWindowTabsManager
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.util.GitRemoteUrlCoordinates

class GHPRSelectInToolWindowHelper(private val project: Project,
                                   private val remoteUrl: GitRemoteUrlCoordinates,
                                   private val pullRequest: GHPRIdentifier) {

  fun selectCommit(oid: String) {
    project.service<GHPRToolWindowTabsManager>().showTab(remoteUrl) { twctr ->
      twctr?.viewPullRequest(pullRequest) {
        it?.selectCommit(oid)
      }
    }
  }

  fun selectChange(oid: String?, filePath: String) {
    project.service<GHPRToolWindowTabsManager>().showTab(remoteUrl) { twctr ->
      twctr?.viewPullRequest(pullRequest) {
        it?.selectChange(oid, filePath)
        twctr.openPullRequestDiff(pullRequest, false)
      }
    }
  }

}
