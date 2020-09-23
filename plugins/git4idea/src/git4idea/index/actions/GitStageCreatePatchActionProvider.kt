// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionExtensionProvider
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.actions.CreatePatchFromChangesAction
import com.intellij.util.containers.addIfNotNull
import git4idea.index.ContentVersion
import git4idea.index.createChange
import git4idea.index.ui.GIT_FILE_STATUS_NODES_STREAM
import git4idea.index.ui.GIT_STAGE_TRACKER
import git4idea.index.ui.NodeKind
import kotlin.streams.toList

open class GitStageCreatePatchActionProvider private constructor(private val silentClipboard: Boolean) : AnActionExtensionProvider {
  class Dialog : GitStageCreatePatchActionProvider(false)
  class Clipboard : GitStageCreatePatchActionProvider(true)

  override fun isActive(e: AnActionEvent): Boolean = e.getData(GIT_STAGE_TRACKER) != null

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project != null &&
                               e.getData(GIT_FILE_STATUS_NODES_STREAM)?.anyMatch { it.kind == NodeKind.STAGED ||
                                                                                   it.kind == NodeKind.UNSTAGED } ?: false
    e.presentation.isVisible = e.presentation.isEnabled || e.isFromActionToolbar
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val nodes = e.getRequiredData(GIT_FILE_STATUS_NODES_STREAM).toList()

    val stagedNodesMap = nodes.filter { it.kind == NodeKind.STAGED }.mapTo(mutableSetOf()) { Pair(it.root, it.status) }
    val unstagedNodesMap = nodes.filter { it.kind == NodeKind.UNSTAGED }.mapTo(mutableSetOf()) { Pair(it.root, it.status) }

    val changes = mutableListOf<Change>()
    for (pair in (stagedNodesMap + unstagedNodesMap)) {
      val beforeVersion = if (stagedNodesMap.contains(pair)) ContentVersion.HEAD else ContentVersion.STAGED
      val afterVersion = if (unstagedNodesMap.contains(pair)) ContentVersion.LOCAL else ContentVersion.STAGED
      changes.addIfNotNull(createChange(project, pair.first, pair.second, beforeVersion, afterVersion))
    }

    CreatePatchFromChangesAction.createPatch(e.project, null, changes, silentClipboard)
  }
}