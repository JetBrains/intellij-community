// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.collaboration.async.CompletableFutureUtil.composeOnEdt
import com.intellij.collaboration.async.CompletableFutureUtil.successOnEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.github.pullrequest.GHPRToolWindowController
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier

class GHPRSelectInToolWindowHelper(private val project: Project, private val pullRequest: GHPRIdentifier) {

  fun selectCommit(oid: String) {
    project.service<GHPRToolWindowController>().activate().composeOnEdt {
      it.repositoryContentController
    }.successOnEdt {
      it.viewPullRequest(pullRequest)?.selectCommit(oid)
    }
  }

  fun selectChange(oid: String?, filePath: String) {
    project.service<GHPRToolWindowController>().activate().composeOnEdt {
      it.repositoryContentController
    }.successOnEdt {
      it.viewPullRequest(pullRequest)?.selectChange(oid, filePath)
      it.openPullRequestDiff(pullRequest, false)
    }
  }
}