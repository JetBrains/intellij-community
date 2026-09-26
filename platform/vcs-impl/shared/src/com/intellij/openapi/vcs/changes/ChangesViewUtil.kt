// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ui.ChangeNodeDecorator
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.platform.vcs.impl.shared.changes.ChangeListsViewModel
import com.intellij.platform.vcs.impl.shared.changes.TreeModelBuilderEx
import com.intellij.platform.vcs.impl.shared.commit.PartialCommitChangeNodeDecorator
import org.jetbrains.annotations.ApiStatus
import javax.swing.tree.DefaultTreeModel

private typealias DecoratorProvider = (ChangeNodeDecorator?) -> ChangeNodeDecorator

@ApiStatus.Internal
object ChangesViewUtil {
  private fun getChangeDecoratorProvider(project: Project, isAllowExcludeFromCommit: () -> Boolean): DecoratorProvider =
    { baseDecorator -> PartialCommitChangeNodeDecorator(project, baseDecorator, isAllowExcludeFromCommit) }

  fun createTreeModel(
    project: Project,
    grouping: ChangesGroupingPolicyFactory,
    changeLists: List<LocalChangeList>,
    unversionedFiles: List<FilePath>,
    ignoredFiles: List<FilePath>,
    isAllowExcludeFromCommit: () -> Boolean
  ): DefaultTreeModel {
    val skipSingleDefaultChangeList = !ChangeListsViewModel.getInstance(project).areChangeListsEnabled.value
    val treeModelBuilder = TreeModelBuilder(project, grouping)
      .setChangeLists(changeLists, skipSingleDefaultChangeList, getChangeDecoratorProvider(project, isAllowExcludeFromCommit))
      .also { TreeModelBuilderEx.getInstanceOrNull(project)?.modifyTreeModelBuilder(it) }
      .setUnversioned(unversionedFiles)

    treeModelBuilder.setIgnored(ignoredFiles)

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