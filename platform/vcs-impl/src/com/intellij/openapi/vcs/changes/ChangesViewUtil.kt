// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.InitialVfsRefreshService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ui.ChangeNodeDecorator
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserUnversionedLoadingPendingNode
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.platform.vcs.impl.shared.changes.ChangesViewModelBuilderService
import com.intellij.vcs.commit.PartialCommitChangeNodeDecorator
import org.jetbrains.annotations.ApiStatus
import javax.swing.tree.DefaultTreeModel

@ApiStatus.Internal
object ChangesViewUtil {
  private fun getChangeDecoratorProvider(project: Project, isAllowExcludeFromCommit: () -> Boolean): (ChangeNodeDecorator?) -> PartialCommitChangeNodeDecorator {
    return  { baseDecorator: ChangeNodeDecorator? -> PartialCommitChangeNodeDecorator(project, baseDecorator!!, isAllowExcludeFromCommit) }
  }

  fun createTreeModel(
    project: Project,
    grouping: ChangesGroupingPolicyFactory,
    changeLists: List<LocalChangeList>,
    unversionedFiles: List<FilePath>,
    showIgnoredFiles: Boolean,
    isAllowExcludeFromCommit: () -> Boolean
  ): DefaultTreeModel {
    val changeListManager = ChangeListManagerImpl.getInstanceImpl(project)

    val shouldShowUntrackedLoading = unversionedFiles.isEmpty() &&
                                     !project.getService(InitialVfsRefreshService::class.java).isInitialVfsRefreshFinished() &&
                                     changeListManager.isUnversionedInUpdateMode
    val skipSingleDefaultChangeList = Registry.`is`("vcs.skip.single.default.changelist") ||
                                      !changeListManager.areChangeListsEnabled()

    val treeModelBuilder = TreeModelBuilder(project, grouping)
      .setChangeLists(changeLists, skipSingleDefaultChangeList, getChangeDecoratorProvider(project, isAllowExcludeFromCommit))
      .apply {
        with(ChangesViewModelBuilderService.getInstance(project)) { createNodes() }
      }
      .setUnversioned(unversionedFiles)

    if (showIgnoredFiles) {
      val ignoredFilePaths: List<FilePath> = changeListManager.getIgnoredFilePaths()
      treeModelBuilder.setIgnored(ignoredFilePaths)
    }
    if (shouldShowUntrackedLoading) {
      treeModelBuilder.insertSubtreeRoot(ChangesBrowserUnversionedLoadingPendingNode())
    }

    for (extension in ChangesViewModifier.KEY.getExtensions(project)) {
      try {
        extension.modifyTreeModelBuilder(treeModelBuilder)
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (t: Throwable) {
        Logger.getInstance(this::class.java).error(t)
      }
    }

    return treeModelBuilder.build(true)
  }
}