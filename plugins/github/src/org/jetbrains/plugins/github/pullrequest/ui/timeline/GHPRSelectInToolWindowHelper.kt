// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.github.pullrequest.GHPRToolWindowController
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier

class GHPRSelectInToolWindowHelper(private val project: Project, private val pullRequest: GHPRIdentifier) {

  fun selectCommit(oid: String) {
    project.service<GHPRToolWindowController>().activate { twctr ->
      twctr.componentController?.viewPullRequest(pullRequest) {
        it?.selectCommit(oid)
      }
    }
  }

  fun selectChange(oid: String?, filePath: String) {
    project.service<GHPRToolWindowController>().activate { twctr ->
      twctr.componentController?.viewPullRequest(pullRequest) {
        it?.selectChange(oid, filePath)
        twctr.componentController?.openPullRequestDiff(pullRequest, false)
      }
    }
  }

}