// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch.dashboard

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.CustomVcsLogUiFactoryProvider
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.impl.VcsLogManager.BaseVcsLogUiFactory
import com.intellij.vcs.log.ui.MainVcsLogUi
import com.intellij.vcs.log.ui.VcsLogColorManager
import com.intellij.vcs.log.ui.VcsLogUiImpl
import com.intellij.vcs.log.ui.filter.VcsLogFilterUiEx
import com.intellij.vcs.log.visible.VisiblePackRefresher
import com.intellij.vcs.log.visible.VisiblePackRefresherImpl
import git4idea.GitVcs
import javax.swing.JComponent

class BranchesInGitLogUiFactoryProvider(private val project: Project) : CustomVcsLogUiFactoryProvider {

  override fun isActive(providers: Map<VirtualFile, VcsLogProvider>) = hasGitRoots(project, providers.keys)

  override fun createLogUiFactory(
    logId: String,
    vcsLogManager: VcsLogManager,
    filters: VcsLogFilterCollection?,
  ): VcsLogManager.VcsLogUiFactory<out MainVcsLogUi> =
    BranchesVcsLogUiFactory(vcsLogManager, logId, filters)

  private fun hasGitRoots(project: Project, roots: Collection<VirtualFile>) =
    ProjectLevelVcsManager.getInstance(project).allVcsRoots.asSequence()
      .filter { it.vcs?.keyInstanceMethod == GitVcs.getKey() }
      .map(VcsRoot::getPath)
      .toSet()
      .containsAll(roots)
}

private class BranchesVcsLogUiFactory(
  logManager: VcsLogManager, logId: String, filters: VcsLogFilterCollection? = null,
) : BaseVcsLogUiFactory<BranchesVcsLogUi>(logId, filters, logManager.uiProperties, logManager.colorManager) {
  override fun createVcsLogUiImpl(
    logId: String,
    logData: VcsLogData,
    properties: MainVcsLogUiProperties,
    colorManager: VcsLogColorManager,
    refresher: VisiblePackRefresherImpl,
    filters: VcsLogFilterCollection?,
  ) = BranchesVcsLogUi(logId, logData, colorManager, properties, refresher, filters)
}

internal class BranchesVcsLogUi(
  id: String, logData: VcsLogData, colorManager: VcsLogColorManager,
  uiProperties: MainVcsLogUiProperties, refresher: VisiblePackRefresher,
  initialFilters: VcsLogFilterCollection?,
) : VcsLogUiImpl(id, logData, colorManager, uiProperties, refresher, initialFilters) {

  private val branchesUi =
    BranchesDashboardUi(logData.project, this)
      .also { branchesUi -> Disposer.register(this, branchesUi) }

  internal val mainLogComponent: JComponent
    get() = mainFrame

  internal val changesBrowser: ChangesBrowserBase
    get() = mainFrame.changesBrowser

  override fun createMainFrame(
    logData: VcsLogData, uiProperties: MainVcsLogUiProperties,
    filterUi: VcsLogFilterUiEx, isEditorDiffPreview: Boolean,
  ) = super.createMainFrame(logData, uiProperties, filterUi, isEditorDiffPreview).apply {
    isFocusCycleRoot = false
    focusTraversalPolicy = null //new focus traversal policy will be configured include branches tree
  }

  override fun getMainComponent() = branchesUi.getMainComponent()
}