// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history

import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.VcsLogStorage
import com.intellij.vcs.log.impl.*
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector
import com.intellij.vcs.log.ui.MainVcsLogUi
import com.intellij.vcs.log.ui.VcsLogUiEx
import com.intellij.vcs.log.ui.table.GraphTableModel
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import com.intellij.vcs.log.visible.filters.matches
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.annotations.NonNls
import java.util.function.Function

@NonNls
private const val TAB_GROUP_ID = "History"

fun isNewHistoryEnabled() = Registry.`is`("vcs.new.history")

class VcsLogFileHistoryProviderImpl : VcsLogFileHistoryProvider {

  override fun canShowFileHistory(project: Project, paths: Collection<FilePath>, revisionNumber: String?): Boolean {
    if (!isNewHistoryEnabled()) return false
    val dataManager = VcsProjectLog.getInstance(project).dataManager ?: return false

    if (paths.size == 1) {
      return canShowSingleFileHistory(project, dataManager, paths.single(), revisionNumber != null)
    }

    return revisionNumber == null && createPathsFilter(project, dataManager, paths) != null
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

    val logManager = VcsProjectLog.getInstance(project).logManager!!

    val historyUiConsumer = { ui: VcsLogUiEx, firstTime: Boolean ->
      if (hash != null) {
        ui.jumpToNearestCommit(logManager.dataManager.storage, hash, root, true)
      }
      else if (firstTime) {
        ui.jumpToRow(0, true)
      }
    }

    if (paths.size == 1) {
      val correctedPath = getCorrectedPath(project, paths.single(), root, revisionNumber != null)
      if (!canShowHistoryInLog(logManager.dataManager, correctedPath, root)) {
        findOrOpenHistory(project, logManager, root, correctedPath, hash, historyUiConsumer)
        return
      }
    }

    findOrOpenFolderHistory(project, createHashFilter(hash, root), createPathsFilter(project, logManager.dataManager, paths)!!,
                            historyUiConsumer)
  }

  private fun canShowHistoryInLog(dataManager: VcsLogData,
                                  correctedPath: FilePath,
                                  root: VirtualFile): Boolean {
    if (!correctedPath.isDirectory) {
      return false
    }
    val logProvider = dataManager.logProviders[root] ?: return false
    return VcsLogProperties.SUPPORTS_LOG_DIRECTORY_HISTORY.getOrDefault(logProvider)
  }

  private fun triggerFileHistoryUsage(paths: Collection<FilePath>, hash: Hash?) {
    VcsLogUsageTriggerCollector.triggerUsage(VcsLogUsageTriggerCollector.VcsLogEvent.HISTORY_SHOWN) { data ->
      val kind = if (paths.size > 1) "multiple" else if (paths.first().isDirectory) "folder" else "file"
      data.addData("kind", kind).addData("has_revision", hash != null)
    }
  }

  private fun findOrOpenHistory(project: Project, logManager: VcsLogManager,
                                root: VirtualFile, path: FilePath, hash: Hash?,
                                consumer: (VcsLogUiEx, Boolean) -> Unit) {
    var fileHistoryUi = VcsLogContentUtil.findAndSelect(project, FileHistoryUi::class.java) { ui -> ui.matches(path, hash) }
    val firstTime = fileHistoryUi == null
    if (firstTime) {
      val suffix = if (hash != null) " (" + hash.toShortString() + ")" else ""
      fileHistoryUi = VcsLogContentUtil.openLogTab(project, logManager, TAB_GROUP_ID,
                                                   VcsBundle.messagePointer("file.history.tab.name"), Function { path.name + suffix },
                                                   FileHistoryUiFactory(path, root, hash), true)
    }
    consumer(fileHistoryUi!!, firstTime)
  }

  private fun findOrOpenFolderHistory(project: Project, hashFilter: VcsLogFilter, pathsFilter: VcsLogFilter,
                                      consumer: (VcsLogUiEx, Boolean) -> Unit) {
    var ui = VcsLogContentUtil.findAndSelect(project, MainVcsLogUi::class.java) { logUi ->
      matches(logUi.filterUi.filters, pathsFilter, hashFilter)
    }
    val firstTime = ui == null
    if (firstTime) {
      val filters = VcsLogFilterObject.collection(pathsFilter, hashFilter)
      ui = VcsProjectLog.getInstance(project).openLogTab(filters) ?: return
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
      if (!dataManager.roots.contains(root) ||
          !VcsLogProperties.SUPPORTS_LOG_DIRECTORY_HISTORY.getOrDefault(dataManager.getLogProvider(root))) return null

      val correctedPath = getCorrectedPath(project, path, root, false)
      if (!correctedPath.isDirectory) return null

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

private fun VcsLogUiEx.jumpToNearestCommit(storage: VcsLogStorage, hash: Hash, root: VirtualFile, silently: Boolean) {
  jumpTo(hash, { model: GraphTableModel, h: Hash? ->
    if (!storage.containsCommit(CommitId(h!!, root))) return@jumpTo GraphTableModel.COMMIT_NOT_FOUND

    val commitIndex: Int = storage.getCommitIndex(h, root)
    val visiblePack = model.visiblePack
    var rowIndex = visiblePack.visibleGraph.getVisibleRowIndex(commitIndex)
    if (rowIndex == null) {
      rowIndex = findVisibleAncestorRow(commitIndex, visiblePack)
    }
    rowIndex ?: GraphTableModel.COMMIT_DOES_NOT_MATCH
  }, SettableFuture.create(), silently)
}
