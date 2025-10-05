// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.frame

import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.ui.AsyncChangesTree
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.asSafely
import com.intellij.util.containers.JBIterable
import com.intellij.util.ui.StatusText
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.data.LoadingDetails
import com.intellij.vcs.log.data.index.IndexedDetails
import com.intellij.vcs.log.ui.frame.VcsLogAsyncChangesTreeModel.ChangesState
import com.intellij.vcs.log.ui.frame.VcsLogAsyncChangesTreeModel.Companion.HAS_AFFECTED_FILES
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
object VcsLogChangesTreeComponents {
  fun getTag(model: VcsLogAsyncChangesTreeModel, change: Change): ChangesBrowserNode.Tag? {
    val changesToParents = model.changesState.asSafely<ChangesState.Changes>()?.changesToParents.orEmpty()
    val parentId = changesToParents.entries.firstOrNull { it.value.contains(change) }?.key ?: return null
    return ParentTag(parentId.hash, getText(model, parentId))
  }

  fun uiDataSnapshot(sink: DataSink, model: VcsLogAsyncChangesTreeModel, tree: AsyncChangesTree) {
    sink[HAS_AFFECTED_FILES] = model.affectedPaths != null
    val roots = model.changesState.asSafely<ChangesState.Changes>()?.roots?.toSet().orEmpty()
    val selectedData = VcsTreeModelData.selected(tree)
    sink.lazy(VcsDataKeys.VCS) {
      getSelectedVcs(tree.project, roots, selectedData)?.keyInstanceMethod
    }
  }

  internal fun getText(model: VcsLogAsyncChangesTreeModel, commitId: CommitId): @Nls String {
    var text = VcsLogBundle.message("vcs.log.changes.changes.to.parent.node", commitId.hash.toShortString())
    val detail = model.getCommitDetails(commitId)
    if (detail !is LoadingDetails || detail is IndexedDetails) {
      text += " " + StringUtil.shortenTextWithEllipsis(detail.subject, 50, 0)
    }
    return text
  }

  private fun getSelectedVcs(
    project: Project,
    roots: Set<VirtualFile>,
    selectedData: VcsTreeModelData,
  ): AbstractVcs? {
    val rootsVcs = JBIterable.from(roots)
      .filterMap { root -> ProjectLevelVcsManager.getInstance(project).getVcsFor(root) }
      .unique()
      .single()
    if (rootsVcs != null) return rootsVcs

    val selectionVcs = selectedData.iterateUserObjects(Change::class.java)
      .map { change -> ChangesUtil.getFilePath(change) }
      .filterMap { root -> ProjectLevelVcsManager.getInstance(project).getVcsFor(root) }
      .unique()
      .single()
    return selectionVcs
  }

  fun updateStatusText(
    model: VcsLogAsyncChangesTreeModel,
    emptyText: StatusText,
  ) {
    when (val changesState = model.changesState) {
      is ChangesState.Empty -> {
        if (changesState.resetText) emptyText.text = ""
      }
      is ChangesState.Error -> {
        emptyText.text = VcsLogBundle.message("vcs.log.error.loading.changes.status")
      }
      is ChangesState.ManyChanges -> {
        emptyText.text = VcsLogBundle.message("vcs.log.changes.too.many.status", changesState.size,
                                              changesState.maxSize)
        emptyText.appendSecondaryText(VcsLogBundle.message("vcs.log.changes.too.many.show.anyway.status.action"),
                                      SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES
        ) { changesState.showAnyway() }
      }
      is ChangesState.Changes -> {
        if (changesState.roots.isEmpty()) {
          emptyText.text = VcsLogBundle.message("vcs.log.changes.select.commits.to.view.changes.status")
        }
        else if (!changesState.changesToParents.isEmpty()) {
          emptyText.setText(VcsLogBundle.message("vcs.log.changes.no.merge.conflicts.status")).appendSecondaryText(
            VcsLogBundle.message("vcs.log.changes.show.changes.to.parents.status.action"),
            SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES
          ) { model.isShowChangesFromParents = true }
        }
        else if (model.isShowOnlyAffectedChanges && model.affectedPaths != null) {
          emptyText.setText(VcsLogBundle.message("vcs.log.changes.no.changes.that.affect.selected.paths.status"))
            .appendSecondaryText(VcsLogBundle.message("vcs.log.changes.show.all.paths.status.action"),
                                 SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES
            ) { model.isShowOnlyAffectedChanges = false }
        }
        else {
          emptyText.text = ""
        }
      }
    }
  }
}
