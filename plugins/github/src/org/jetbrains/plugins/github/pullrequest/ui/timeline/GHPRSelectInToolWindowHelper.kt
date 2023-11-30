// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRToolWindowViewModel

class GHPRSelectInToolWindowHelper(private val project: Project, private val pullRequest: GHPRIdentifier) {

  fun selectCommit(oid: String) {
    val vm = project.serviceIfCreated<GHPRToolWindowViewModel>() ?: return
    vm.activate()
    vm.projectVm.value?.viewPullRequest(pullRequest, oid)
  }

  fun selectChange(oid: String?, filePath: String) {
    val projectVm = project.serviceIfCreated<GHPRToolWindowViewModel>()?.projectVm?.value ?: return
    projectVm.viewPullRequest(pullRequest, oid, filePath)
    projectVm.openPullRequestDiff(pullRequest, true)
  }
}