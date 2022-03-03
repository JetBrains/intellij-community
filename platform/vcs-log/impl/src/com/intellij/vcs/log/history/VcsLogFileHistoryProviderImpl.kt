// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history

import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.content.TabGroupId
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.VcsLogStorage
import com.intellij.vcs.log.impl.*
import com.intellij.vcs.log.impl.VcsLogTabLocation.Companion.findLogUi
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector
import com.intellij.vcs.log.ui.MainVcsLogUi
import com.intellij.vcs.log.ui.VcsLogUiEx
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.util.VcsLogUtil.jumpToRow
import com.intellij.vcs.log.visible.VisiblePack
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import com.intellij.vcs.log.visible.filters.matches
import com.intellij.vcsUtil.VcsUtil
import java.util.function.Function

class VcsLogFileHistoryProviderImpl(project: Project) : VcsLogFileHistoryProvider {
  private val providers = listOf(VcsLogSingleFileHistoryProvider(project), VcsLogDirectoryHistoryProvider(project))

  override fun canShowFileHistory(paths: Collection<FilePath>, revisionNumber: String?): Boolean {
    return providers.any { it.canShowFileHistory(paths, revisionNumber) }
  }

  override fun showFileHistory(paths: Collection<FilePath>, revisionNumber: String?) {
    providers.firstOrNull { it.canShowFileHistory(paths, revisionNumber) }?.showFileHistory(paths, revisionNumber)
  }
}

private class VcsLogDirectoryHistoryProvider(private val project: Project) : VcsLogFileHistoryProvider {
  override fun canShowFileHistory(paths: Collection<FilePath>, revisionNumber: String?): Boolean {
    if (!Registry.`is`("vcs.history.show.directory.history.in.log")) return false
    val dataManager = VcsProjectLog.getInstance(project).dataManager ?: return false
    return createPathsFilter(project, dataManager, paths) != null
  }

  override fun showFileHistory(paths: Collection<FilePath>, revisionNumber: String?) {
    val hash = revisionNumber?.let { HashImpl.build(it) }
    val root = VcsLogUtil.getActualRoot(project, paths.first())!!

    triggerFileHistoryUsage(project, paths, hash)

    val logManager = VcsProjectLog.getInstance(project).logManager!!

    val pathsFilter = createPathsFilter(project, logManager.dataManager, paths)!!
    val hashFilter = createHashFilter(hash, root)
    var ui = logManager.findLogUi(VcsLogTabLocation.TOOL_WINDOW, MainVcsLogUi::class.java, true) { logUi ->
      matches(logUi.filterUi.filters, pathsFilter, hashFilter)
    }
    val firstTime = ui == null
    if (firstTime) {
      val filters = VcsLogFilterObject.collection(pathsFilter, hashFilter)
      ui = VcsProjectLog.getInstance(project).openLogTab(filters) ?: return
      ui.properties.set(MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES, true)
    }
    selectRowWhenOpen(logManager, hash, root, ui!!, firstTime)
  }

  companion object {
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
  }
}

private class VcsLogSingleFileHistoryProvider(private val project: Project) : VcsLogFileHistoryProvider {
  private val tabGroupId: TabGroupId = TabGroupId("History", VcsBundle.messagePointer("file.history.tab.name"), false)

  override fun canShowFileHistory(paths: Collection<FilePath>, revisionNumber: String?): Boolean {
    if (!isNewHistoryEnabled() || paths.size != 1) return false

    val root = VcsLogUtil.getActualRoot(project, paths.single()) ?: return false
    val correctedPath = getCorrectedPath(project, paths.single(), root, revisionNumber != null)
    if (correctedPath.isDirectory) return false

    val dataManager = VcsProjectLog.getInstance(project).dataManager ?: return false
    if (dataManager.logProviders[root]?.diffHandler == null) return false
    return dataManager.index.isIndexingEnabled(root) || Registry.`is`("vcs.force.new.history")
  }

  override fun showFileHistory(paths: Collection<FilePath>, revisionNumber: String?) {
    if (paths.size != 1) return

    val root = VcsLogUtil.getActualRoot(project, paths.first())!!
    val path = getCorrectedPath(project, paths.single(), root, revisionNumber != null)
    if (path.isDirectory) return

    val hash = revisionNumber?.let { HashImpl.build(it) }
    triggerFileHistoryUsage(project, paths, hash)

    val logManager = VcsProjectLog.getInstance(project).logManager!!

    var fileHistoryUi = logManager.findLogUi(VcsLogTabLocation.TOOL_WINDOW, FileHistoryUi::class.java, true) { ui -> ui.matches(path, hash) }
    val firstTime = fileHistoryUi == null
    if (firstTime) {
      val suffix = if (hash != null) " (" + hash.toShortString() + ")" else ""
      fileHistoryUi = VcsLogContentUtil.openLogTab(project, logManager, tabGroupId, Function { path.name + suffix },
        FileHistoryUiFactory(path, root, hash), true)
    }
    selectRowWhenOpen(logManager, hash, root, fileHistoryUi!!, firstTime)
  }
}

fun isNewHistoryEnabled() = Registry.`is`("vcs.new.history")

private fun selectRowWhenOpen(logManager: VcsLogManager, hash: Hash?, root: VirtualFile, ui: VcsLogUiEx, firstTime: Boolean) {
  if (hash != null) {
    ui.jumpToNearestCommit(logManager.dataManager.storage, hash, root, true)
  }
  else if (firstTime) {
    jumpToRow(ui, 0, true)
  }
}

private fun VcsLogUiEx.jumpToNearestCommit(storage: VcsLogStorage, hash: Hash, root: VirtualFile, silently: Boolean) {
  jumpTo(hash, { visiblePack: VisiblePack, h: Hash? ->
    if (!storage.containsCommit(CommitId(h!!, root))) return@jumpTo VcsLogUiEx.COMMIT_NOT_FOUND

    val commitIndex: Int = storage.getCommitIndex(h, root)
    var rowIndex = visiblePack.visibleGraph.getVisibleRowIndex(commitIndex)
    if (rowIndex == null) {
      rowIndex = findVisibleAncestorRow(commitIndex, visiblePack)
    }
    rowIndex ?: VcsLogUiEx.COMMIT_DOES_NOT_MATCH
  }, SettableFuture.create(), silently, true)
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

private fun triggerFileHistoryUsage(project: Project, paths: Collection<FilePath>, hash: Hash?) {
  val kind = if (paths.size > 1) "multiple" else if (paths.first().isDirectory) "folder" else "file"
  VcsLogUsageTriggerCollector.triggerFileHistoryUsage(project, kind, hash != null)
}