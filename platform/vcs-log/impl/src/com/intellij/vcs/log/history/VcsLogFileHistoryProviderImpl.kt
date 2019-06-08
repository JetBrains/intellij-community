// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.VcsLogData
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

  override fun canShowFileHistory(project: Project, paths: Collection<FilePath>, revisionNumber: String?): Boolean {
    if (!Registry.`is`("vcs.new.history")) return false
    val dataManager = VcsProjectLog.getInstance(project).dataManager ?: return false

    if (paths.size == 1) {
      return canShowSingleFileHistory(project, dataManager, paths.single(), revisionNumber != null)
    }

    return revisionNumber == null && VcsLogUtil.isFolderHistoryShownInLog() && createPathsFilter(project, dataManager, paths) != null
  }

  private fun canShowSingleFileHistory(project: Project, dataManager: VcsLogData, path: FilePath, isRevisionHistory: Boolean): Boolean {
    val root = VcsLogUtil.getActualRoot(project, path) ?: return false
    return dataManager.index.isIndexingEnabled(root) ||
           canShowHistoryInLog(dataManager, getCorrectedPath(project, path, root, isRevisionHistory), root)
  }

  override fun showFileHistory(project: Project, paths: Collection<FilePath>, revisionNumber: String?) {
    val hash = revisionNumber?.let { HashImpl.build(it) }
    val root = VcsLogUtil.getActualRoot(project, paths.first())!!

    triggerFileHistoryUsage(paths, hash)

    val historyUiConsumer = { ui: AbstractVcsLogUi, firstTime: Boolean ->
      if (hash != null) {
        ui.jumpToNearestCommit(hash, root)
      }
      else if (firstTime) {
        ui.jumpToRow(0)
      }
    }

    val logManager = VcsProjectLog.getInstance(project).logManager!!

    if (paths.size == 1) {
      val correctedPath = getCorrectedPath(project, paths.single(), root, revisionNumber != null)
      if (!canShowHistoryInLog(logManager.dataManager, correctedPath, root)) {
        findOrOpenHistory(project, logManager, root, correctedPath, hash, historyUiConsumer)
        return
      }
    }

    findOrOpenFolderHistory(project, logManager, createHashFilter(hash, root), createPathsFilter(project, logManager.dataManager, paths)!!,
                            historyUiConsumer)
  }

  private fun canShowHistoryInLog(dataManager: VcsLogData,
                                  correctedPath: FilePath,
                                  root: VirtualFile): Boolean {
    if (!VcsLogUtil.isFolderHistoryShownInLog() || !correctedPath.isDirectory) {
      return false
    }
    val logProvider = dataManager.logProviders[root] ?: return false
    return (VcsLogProperties.get(logProvider, VcsLogProperties.SUPPORTS_LOG_DIRECTORY_HISTORY))
  }

  private fun triggerFileHistoryUsage(paths: Collection<FilePath>, hash: Hash?) {
    val name = if (paths.size > 1) "MultiplePaths" else if (paths.first().isDirectory) "Folder" else "File"
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
                                      hashFilter: VcsLogFilter, pathsFilter: VcsLogFilter,
                                      consumer: (AbstractVcsLogUi, Boolean) -> Unit) {
    var ui = VcsLogContentUtil.findAndSelect(project, VcsLogUiImpl::class.java) { logUi ->
      matches(logUi.filterUi.filters, pathsFilter, hashFilter)
    }
    val firstTime = ui == null
    if (firstTime) {
      val filters = VcsLogFilterObject.collection(pathsFilter, hashFilter)
      ui = VcsProjectLog.getInstance(project).tabsManager.openAnotherLogTab(logManager, filters)
      ui.properties.set(MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES, true)
    }
    consumer(ui!!, firstTime)
  }

  private fun createPathsFilter(project: Project, dataManager: VcsLogData, paths: Collection<FilePath>): VcsLogFilter? {
    val forRootFilter = mutableSetOf<VirtualFile>()
    val forPathsFilter = mutableListOf<FilePath>()
    for (path in paths) {
      val root = VcsLogUtil.getActualRoot(project, path)
      if (root == null) return null
      if (!VcsLogProperties.get(dataManager.getLogProvider(root), VcsLogProperties.SUPPORTS_LOG_DIRECTORY_HISTORY)) return null

      val correctedPath = getCorrectedPath(project, path, root, false)
      if (!correctedPath.isDirectory) return null;

      if (path.virtualFile == root) {
        forRootFilter.add(root)
      }
      else {
        forPathsFilter.add(correctedPath)
      }

      if (forPathsFilter.isNotEmpty() && forRootFilter.isNotEmpty()) return null
    }

    if (forPathsFilter.isNotEmpty()) return VcsLogFilterObject.fromPaths(forPathsFilter)
    return VcsLogFilterObject.fromRoots(forRootFilter)
  }

  private fun createHashFilter(hash: Hash?, root: VirtualFile): VcsLogFilter {
    if (hash == null) {
      return VcsLogFilterObject.fromBranch(VcsLogUtil.HEAD)
    }

    return VcsLogFilterObject.fromCommit(CommitId(hash, root))
  }

  private fun matches(filters: VcsLogFilterCollection, pathsFilter: VcsLogFilter, hashFilter: VcsLogFilter): Boolean {
    if (!filters.matches(hashFilter.key, pathsFilter.key)) {
      return false
    }
    return filters.get(pathsFilter.key) == pathsFilter && filters.get(hashFilter.key) == hashFilter
  }

  private fun getCorrectedPath(project: Project, path: FilePath, root: VirtualFile,
                               isRevisionHistory: Boolean): FilePath {
    var correctedPath = path
    if (root != VcsUtil.getVcsRootFor(project, correctedPath) && correctedPath.isDirectory) {
      correctedPath = VcsUtil.getFilePath(correctedPath.path, false)
    }

    if (!isRevisionHistory) {
      return VcsUtil.getLastCommitPath(project, correctedPath)
    }

    return correctedPath
  }
}
