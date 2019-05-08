// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ObjectUtils.assertNotNull
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogFileHistoryProvider
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.impl.*
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector
import com.intellij.vcs.log.ui.AbstractVcsLogUi
import com.intellij.vcs.log.ui.VcsLogUiImpl
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import com.intellij.vcs.log.visible.filters.matches
import com.intellij.vcsUtil.VcsUtil

private const val TAB_NAME = "History"

class VcsLogFileHistoryProviderImpl : VcsLogFileHistoryProvider {

  override fun canShowFileHistory(project: Project, path: FilePath, revisionNumber: String?): Boolean {
    if (!Registry.`is`("vcs.new.history")) return false

    val root = VcsLogUtil.getActualRoot(project, path) ?: return false
    val dataManager = VcsProjectLog.getInstance(project).dataManager ?: return false

    return dataManager.index.isIndexingEnabled(root)
  }

  override fun showFileHistory(project: Project, path: FilePath, revisionNumber: String?) {
    val root = VcsLogUtil.getActualRoot(project, path)!!
    val correctedPath = getCorrectedPath(project, path, root, revisionNumber)
    val hash = if (revisionNumber != null) HashImpl.build(revisionNumber) else null

    triggerFileHistoryUsage(correctedPath, hash)

    val historyUiConsumer = { ui: AbstractVcsLogUi, firstTime: Boolean ->
      if (hash != null) {
        ui.jumpToNearestCommit(hash, root)
      }
      else if (firstTime) {
        ui.jumpToRow(0)
      }
    }

    val logManager = VcsProjectLog.getInstance(project).logManager!!
    if (correctedPath.isDirectory && VcsLogUtil.isFolderHistoryShownInLog()) {
      findOrOpenFolderHistory(project, logManager, root, correctedPath, hash, historyUiConsumer)
    }
    else {
      findOrOpenHistory(project, logManager, root, correctedPath, hash, historyUiConsumer)
    }
  }

  private fun triggerFileHistoryUsage(path: FilePath, hash: Hash?) {
    val name = if (path.isDirectory) "Folder" else "File"
    val suffix = if (hash != null) "ForRevision" else ""
    VcsLogUsageTriggerCollector.triggerUsage("Show" + name + "History" + suffix)
  }

  private fun findOrOpenHistory(project: Project, logManager: VcsLogManager,
                                root: VirtualFile, path: FilePath, hash: Hash?,
                                consumer: (AbstractVcsLogUi, Boolean) -> Unit) {
    var fileHistoryUi = VcsLogContentUtil.findAndSelect(project, FileHistoryUi::class.java) { ui -> ui.matches(path, hash) }
    val firstTime = fileHistoryUi == null
    if (firstTime) {
      val suffix = if (hash != null) " (" + hash.toShortString() + ")" else ""
      fileHistoryUi = VcsLogContentUtil.openLogTab(project, logManager, TAB_NAME, path.name + suffix,
                                                   FileHistoryUiFactory(path, root, hash), true)
    }
    consumer(fileHistoryUi!!, firstTime)
  }

  private fun findOrOpenFolderHistory(project: Project, logManager: VcsLogManager,
                                      root: VirtualFile, path: FilePath, hash: Hash?,
                                      consumer: (AbstractVcsLogUi, Boolean) -> Unit) {
    var ui = VcsLogContentUtil.findAndSelect(project, VcsLogUiImpl::class.java) { logUi -> matches(logUi.filterUi.filters, path, hash) }
    val firstTime = ui == null
    if (firstTime) {
      val filters = createFilters(path, hash, root)
      ui = VcsProjectLog.getInstance(project).tabsManager.openAnotherLogTab(logManager, filters)
      ui.properties.set(MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES, true)
    }
    consumer(ui!!, firstTime)
  }

  private fun createFilters(filePath: FilePath, hash: Hash?, root: VirtualFile): VcsLogFilterCollection {
    val pathFilter = if (filePath.virtualFile == root) {
      VcsLogFilterObject.fromRoot(root)
    }
    else {
      VcsLogFilterObject.fromPaths(setOf(filePath))
    }

    if (hash == null) {
      return VcsLogFilterObject.collection(pathFilter, VcsLogFilterObject.fromBranch(VcsLogUtil.HEAD))
    }

    return VcsLogFilterObject.collection(pathFilter, VcsLogFilterObject.fromCommit(CommitId(hash, root)))
  }

  private fun matches(filters: VcsLogFilterCollection, filePath: FilePath, hash: Hash?): Boolean {
    val hashKey = if (hash == null)
      VcsLogFilterCollection.BRANCH_FILTER
    else
      VcsLogFilterCollection.REVISION_FILTER
    if (!filters.matches(hashKey, VcsLogFilterCollection.STRUCTURE_FILTER) &&
        !filters.matches(hashKey, VcsLogFilterCollection.ROOT_FILTER)) {
      return false
    }
    if (getSingleFilePath(filters) != filePath) return false
    return if (hash != null) getSingleHash(filters) == hash else isFilteredByHead(filters)
  }

  private fun isFilteredByHead(filters: VcsLogFilterCollection): Boolean {
    val branchFilter = filters.get(VcsLogFilterCollection.BRANCH_FILTER) ?: return false
    return branchFilter.textPresentation == listOf(VcsLogUtil.HEAD)
  }

  private fun getSingleHash(filters: VcsLogFilterCollection): Hash? {
    val revisionFilter = filters.get(VcsLogFilterCollection.REVISION_FILTER) ?: return null
    val heads = revisionFilter.heads
    return if (heads.size != 1) null else assertNotNull(ContainerUtil.getFirstItem(heads)).hash
  }

  private fun getSingleFilePath(filters: VcsLogFilterCollection): FilePath? {
    val structureFilter = filters.get(VcsLogFilterCollection.STRUCTURE_FILTER)
    if (structureFilter == null) {
      val rootFilter = filters.get(VcsLogFilterCollection.ROOT_FILTER) ?: return null
      val roots = rootFilter.roots
      return if (roots.size != 1) null else VcsUtil.getFilePath(assertNotNull(ContainerUtil.getFirstItem(roots)))
    }
    val filePaths = structureFilter.files
    return if (filePaths.size != 1) null else ContainerUtil.getFirstItem(filePaths)
  }

  private fun getCorrectedPath(project: Project, path: FilePath, root: VirtualFile,
                               revisionNumber: String?): FilePath {
    var correctedPath = path
    if (root != VcsUtil.getVcsRootFor(project, correctedPath) && correctedPath.isDirectory) {
      correctedPath = VcsUtil.getFilePath(correctedPath.path, false)
    }

    if (revisionNumber == null) {
      return VcsUtil.getLastCommitPath(project, correctedPath)
    }

    return correctedPath
  }
}
