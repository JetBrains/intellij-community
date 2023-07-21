// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.CodeReviewProgressTreeModel
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestChangeListViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestChangesViewModel
import kotlin.properties.Delegates.observable

@OptIn(FlowPreview::class)
internal class GitLabMergeRequestProgressTreeModel(
  cs: CoroutineScope,
  vm: GitLabMergeRequestChangesViewModel
) : CodeReviewProgressTreeModel<Change>() {

  private var unresolvedThreadsCount by observable<Map<Change, Int>>(emptyMap()) { _, _, _ ->
    fireModelChanged()
  }

  init {
    cs.launchNow {
      vm.mappedDiscussionsCounts.debounce(100).collect {
        unresolvedThreadsCount = it
      }
    }
  }

  override fun asLeaf(node: ChangesBrowserNode<*>): Change? = node.userObject as? Change

  override fun isRead(leafValue: Change): Boolean = true

  override fun getUnresolvedDiscussionsCount(leafValue: Change): Int = unresolvedThreadsCount[leafValue] ?: 0
}