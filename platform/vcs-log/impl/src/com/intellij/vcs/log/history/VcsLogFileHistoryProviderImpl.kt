// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.history

import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.VcsLogStorage
import com.intellij.vcs.log.impl.*
import com.intellij.vcs.log.impl.VcsLogNavigationUtil.jumpToGraphRow
import com.intellij.vcs.log.impl.VcsLogNavigationUtil.jumpToHash
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector
import com.intellij.vcs.log.ui.MainVcsLogUi
import com.intellij.vcs.log.ui.VcsLogUiEx
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.visible.VisiblePack
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import com.intellij.vcs.log.visible.filters.matches
import com.intellij.vcsUtil.VcsUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

internal class VcsLogFileHistoryProviderImpl(private val project: Project, private val cs: CoroutineScope) : VcsLogFileHistoryProvider {

  override fun canShowFileHistory(paths: Collection<FilePath>, revisionNumber: String?): Boolean {
    return selectProvider(project, paths, revisionNumber) != null
  }

  override fun showFileHistory(paths: Collection<FilePath>, revisionNumber: String?) {
    selectProvider(project, paths, revisionNumber)?.showFileHistoryUi(project, paths, revisionNumber)
  }

  override fun showFileHistory(paths: Collection<FilePath>, revisionNumber: String?, revisionToSelect: String) {
    val ui = selectProvider(project, paths, revisionNumber)?.showFileHistoryUi(project, paths, revisionNumber, false) ?: return
    val future = ui.jumpToHash(revisionToSelect, false, true)

    cs.launch {
      val title = VcsLogBundle.message("file.history.show.commit.in.history.process", VcsLogUtil.getShortHash(revisionToSelect))
      withBackgroundProgress(project, title, true) {
        future.await()
      }
    }
  }
}

private fun selectProvider(project: Project, paths: Collection<FilePath>, revisionNumber: String?): FileHistoryLogUiProvider? {
  return listOf(VcsLogSingleFileHistoryProvider, VcsLogDirectoryHistoryProvider).firstOrNull {
    it.canShowFileHistory(project, paths, revisionNumber)
  }
}

private interface FileHistoryLogUiProvider {
  fun canShowFileHistory(project: Project, paths: Collection<FilePath>, revisionNumber: String?): Boolean
  fun showFileHistoryUi(project: Project, paths: Collection<FilePath>, revisionNumber: String?, selectRow: Boolean = true): VcsLogUiEx?
}

private object VcsLogDirectoryHistoryProvider : FileHistoryLogUiProvider {
  override fun canShowFileHistory(project: Project, paths: Collection<FilePath>, revisionNumber: String?): Boolean {
    if (paths.isEmpty()) return false
    val projectLog = VcsProjectLog.getInstance(project)
    if (projectLog.logManager !is IdeVcsLogManager) return false
    val dataManager = projectLog.dataManager ?: return false
    return FileHistoryUtil.createLogPathsFilter(project, dataManager.logProviders, paths) != null
  }

  override fun showFileHistoryUi(project: Project, paths: Collection<FilePath>, revisionNumber: String?, selectRow: Boolean): VcsLogUiEx? {
    val hash = revisionNumber?.let { HashImpl.build(it) }
    val root = VcsLogUtil.getActualRoot(project, paths.first())!!

    triggerFileHistoryUsage(project, paths, hash)

    val logManager = VcsProjectLog.getInstance(project).logManager as? IdeVcsLogManager ?: return null

    val pathsFilter = FileHistoryUtil.createLogPathsFilter(project, logManager.dataManager.logProviders, paths)!!
    val hashFilter = createHashFilter(hash, root)
    var ui = logManager.findLogUi(MainVcsLogUi::class.java, true) { logUi ->
      matches(logUi.filterUi.filters, pathsFilter, hashFilter)
    }
    val firstTime = ui == null
    if (firstTime) {
      val filters = VcsLogFilterObject.collection(pathsFilter, hashFilter)
      ui = logManager.openNewLogTab(filters)
      ui.properties[MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES] = true
    }
    if (selectRow) selectRowWhenOpen(logManager, hash, root, ui, firstTime)

    return ui
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

private object VcsLogSingleFileHistoryProvider : FileHistoryLogUiProvider {
  override fun canShowFileHistory(project: Project, paths: Collection<FilePath>, revisionNumber: String?): Boolean {
    if (!isNewHistoryEnabled() || paths.size != 1) return false

    val root = VcsLogUtil.getActualRoot(project, paths.single()) ?: return false
    val correctedPath = getCorrectedPath(project, paths.single(), revisionNumber != null)
    if (correctedPath.isDirectory) return false

    val logProvider = VcsProjectLog.getInstance(project).dataManager?.logProviders?.get(root)
    return isNewFileHistoryAvailable(project, logProvider)
  }

  override fun showFileHistoryUi(project: Project, paths: Collection<FilePath>, revisionNumber: String?, selectRow: Boolean): VcsLogUiEx? {
    if (paths.size != 1) return null

    val root = VcsLogUtil.getActualRoot(project, paths.first())!!
    val path = getCorrectedPath(project, paths.single(), revisionNumber != null)
    if (path.isDirectory) return null

    val hash = revisionNumber?.let { HashImpl.build(it) }
    triggerFileHistoryUsage(project, paths, hash)

    val logManager = VcsProjectLog.getInstance(project).logManager!!

    val tabsManager = project.service<FileHistoryTabsManager>()

    var fileHistoryUi = tabsManager.findUi(true) { ui ->
      ui.matches(path, hash)
    }
    val firstTime = fileHistoryUi == null
    if (firstTime) {
      fileHistoryUi = tabsManager.openFileHistoryTab(logManager, path, root, hash)
    }
    if (selectRow) selectRowWhenOpen(logManager, hash, root, fileHistoryUi, firstTime)

    return fileHistoryUi
  }
}

@ApiStatus.Internal
fun isNewHistoryEnabled() = Registry.`is`("vcs.new.history")
@ApiStatus.Internal
fun isNewFileHistoryAvailable(project: Project, logProvider: VcsLogProvider?): Boolean {
  return logProvider?.diffHandler != null && logProvider.getFileHistoryHandler(project) != null
}

private fun selectRowWhenOpen(logManager: VcsLogManager, hash: Hash?, root: VirtualFile, ui: VcsLogUiEx, firstTime: Boolean) {
  if (hash != null) {
    ui.jumpToNearestCommit(logManager.dataManager.storage, hash, root, true)
  }
  else if (firstTime) {
    ui.jumpToGraphRow(0, true, true)
  }
}

private fun VcsLogUiEx.jumpToNearestCommit(storage: VcsLogStorage, hash: Hash, root: VirtualFile, silently: Boolean) {
  jumpTo(hash, { visiblePack: VisiblePack, h: Hash? ->
    if (!storage.containsCommit(CommitId(h!!, root))) return@jumpTo VcsLogUiEx.COMMIT_NOT_FOUND

    val commitIndex = storage.getCommitIndex(h, root)
    var rowIndex = visiblePack.visibleGraph.getVisibleRowIndex(commitIndex)
    if (rowIndex == null) {
      rowIndex = findVisibleAncestorRow(commitIndex, visiblePack)
    }
    rowIndex ?: VcsLogUiEx.COMMIT_DOES_NOT_MATCH
  }, SettableFuture.create(), silently, true)
}

private fun getCorrectedPath(project: Project, path: FilePath, isRevisionHistory: Boolean): FilePath {
  if (isRevisionHistory) return path
  return VcsUtil.getLastCommitPath(project, path)
}

private fun triggerFileHistoryUsage(project: Project, paths: Collection<FilePath>, hash: Hash?) {
  val kind = if (paths.size > 1) "multiple" else if (paths.first().isDirectory) "folder" else "file"
  VcsLogUsageTriggerCollector.triggerFileHistoryUsage(project, kind, hash != null)
}