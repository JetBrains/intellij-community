// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.update

import com.intellij.configurationStore.StoreReloadManager
import com.intellij.history.Label
import com.intellij.history.LocalHistory
import com.intellij.history.LocalHistoryAction
import com.intellij.ide.errorTreeView.HotfixData
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.changes.RemoteRevisionsCache
import com.intellij.openapi.vcs.changes.VcsAnnotationRefresher
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.changes.actions.VcsStatisticsCollector
import com.intellij.openapi.vcs.changes.committed.CommittedChangesCache
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.VcsActivity
import com.intellij.vcs.ViewUpdateInfoNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

private val LOG = logger<VcsUpdateTask>()

internal class VcsUpdateTask(
  private val project: Project,
  private val roots: Array<FilePath>,
  private val spec: List<VcsUpdateSpecification>,
  private val actionInfo: ActionInfo,
  private val actionName: @Nls @NlsContexts.ProgressTitle String,
) {
  private var updatedFiles: UpdatedFiles = UpdatedFiles.create()
  private val groupedExceptions = HashMap<HotfixData?, MutableList<VcsException>>()
  private val updateSessions = ArrayList<UpdateSession>()
  private var updateNumber = 1

  // vcs name, context object
  // create from outside without any context; context is created by vcses
  private val contextInfo = HashMap<AbstractVcs, SequentialUpdatesContext?>()

  private var before: Label? = null
  private var after: Label? = null
  private var localHistoryAction: LocalHistoryAction? = null

  private fun reset() {
    updatedFiles = UpdatedFiles.create()
    groupedExceptions.clear()
    updateSessions.clear()
    ++updateNumber
  }

  fun launch(@RequiresEdt onSuccess: () -> Unit = {}) {
    project.service<ProjectVcsUpdateTaskExecutor>().cs.launch(Dispatchers.Default) {
      try {
        withBackgroundProgress(project, actionName) {
          coroutineToIndicator {
            run(it)
          }
        }
        withContext(Dispatchers.UiWithModelAccess) {
          finishExecution(false)
          onSuccess()
        }
      }
      catch (ce: CancellationException) {
        withContext(Dispatchers.UiWithModelAccess) {
          finishExecution(true)
        }
        throw ce
      }
      catch (e: Throwable) {
        LOG.error(e)
      }
    }
  }

  @RequiresBackgroundThread
  fun run(progressIndicator: ProgressIndicator) {
    StoreReloadManager.getInstance(project).blockReloadingProjectOnExternalChanges()
    val vcsManager = ProjectLevelVcsManager.getInstance(project)
    vcsManager.startBackgroundVcsOperation()

    before = LocalHistory.getInstance().putSystemLabel(project, VcsBundle.message("update.label.before.update"))
    localHistoryAction = LocalHistory.getInstance().startAction(VcsBundle.message("activity.name.update"), VcsActivity.Update)
    progressIndicator.setIndeterminate(false)
    val activity = VcsStatisticsCollector.UPDATE_ACTIVITY.started(project)
    try {
      val toBeProcessed = spec.size
      var processed = 0
      for ((vcs, updateEnvironment, files) in spec) {
        updateEnvironment.fillGroups(updatedFiles)

        val context = contextInfo[vcs]
        val refContext = Ref<SequentialUpdatesContext>(context)

        val updateSession = updateEnvironment.updateDirectories(files.toTypedArray<FilePath>(), updatedFiles, progressIndicator, refContext)

        contextInfo[vcs] = refContext.get()
        processed++
        progressIndicator.setFraction(processed.toDouble() / toBeProcessed.toDouble())
        progressIndicator.setText2("")
        val exceptionList = collectExceptions(vcs, updateSession.getExceptions())
        groupedExceptions.putAllNonEmpty(exceptionList)
        updateSessions.add(updateSession)
      }
    }
    finally {
      try {
        progressIndicator.checkCanceled()
        progressIndicator.setText(VcsBundle.message("progress.text.synchronizing.files"))
        progressIndicator.setText2("")
        LOG.info("Calling refresh files after update for roots: ${roots.contentToString()}")
        RefreshVFsSynchronously.updateAllChanged(updatedFiles)
        notifyAnnotations(project, updatedFiles)
      }
      finally {
        vcsManager.stopBackgroundVcsOperation()
        notifyFiles(project, updatedFiles)
        activity.finished()
      }
    }
  }

  private fun finishExecution(wasCanceled: Boolean) {
    val continueChain = try {
      if (!project.isOpen() || project.isDisposed()) {
        LocalHistory.getInstance().putSystemLabel(project, VcsBundle.message("activity.name.update")) // TODO check why this label is needed
        return
      }

      handleResult(wasCanceled)
    }
    finally {
      StoreReloadManager.getInstance(project).unblockReloadingProjectOnExternalChanges()
    }

    if (continueChain) {
      // trigger the next update; for CVS when updating from several branches simultaneously
      reset()
      launch()
    }
  }

  private fun handleResult(wasCanceled: Boolean): Boolean {
    val contextInfo = contextInfo.fold()
    val someSessionWasCancelled = wasCanceled || someSessionWasCanceled(updateSessions)
    // here text conflicts might be interactively resolved
    for (updateSession in updateSessions) {
      updateSession.onRefreshFilesCompleted()
    }
    // only after conflicts are resolved, put a label
    localHistoryAction?.finish()
    after = LocalHistory.getInstance().putSystemLabel(project, VcsBundle.message("update.label.after.update"))

    if (actionInfo.canChangeFileStatus()) {
      refreshFilesStatus(project, updatedFiles)
    }

    val updateSuccess = !someSessionWasCancelled && groupedExceptions.isEmpty()
    val noMerged = updatedFiles.getGroupById(FileGroup.MERGED_WITH_CONFLICT_ID)!!.isEmpty()
    if (updatedFiles.isEmpty && groupedExceptions.isEmpty()) {
      val type: NotificationType?
      val content: String?
      if (someSessionWasCancelled) {
        content = VcsBundle.message("progress.text.updating.canceled")
        type = NotificationType.WARNING
      }
      else {
        content = getAllFilesAreUpToDateMessage(roots)
        type = NotificationType.INFORMATION
      }
      VcsNotifier.getInstance(project).notify(
        VcsNotifier.standardNotification().createNotification(content, type)
          .setDisplayId(VcsNotificationIdsHolder.PROJECT_UPDATE_FINISHED))
    }
    else if (!updatedFiles.isEmpty) {
      val singleUpdateSession = updateSessions.singleOrNull()
      if (singleUpdateSession != null && VcsUpdateProcess.checkUpdateHasCustomNotification(spec.map { it.vcs })) {
        // multi-vcs projects behave as before: only a compound notification & file tree is shown for them, for the sake of simplicity
        singleUpdateSession.showNotification()
      }
      else {
        val willBeContinued = contextInfo.continueChain && updateSuccess && noMerged
        val updateNumber = if (willBeContinued || updateNumber > 1) updateNumber else 0
        val tree = showUpdateTree(someSessionWasCancelled, updateNumber)

        if (tree != null) {
          val additionalContent = updateSessions.mapNotNull { it.additionalNotificationContent }
          prepareUpdatedFilesNotification(someSessionWasCancelled, updatedFiles, additionalContent, tree).apply {
            addAction(ViewUpdateInfoNotification(project, tree, VcsBundle.message("update.notification.content.view")))
          }.also {
            Disposer.register(tree, Disposable { it.expire() })
          }.notify(project)
        }
      }
    }

    if (groupedExceptions.isNotEmpty()) {
      val exceptions = groupedExceptions.toMutableMap().apply {
        putAllNonEmpty(contextInfo.interruptedExceptions)
      }
      showExceptions(exceptions)
    }
    else if (contextInfo.continueChain && !someSessionWasCancelled) {
      if (noMerged) {
        return true
      }
      else {
        showExceptions(contextInfo.interruptedExceptions)
      }
    }
    return false
  }

  private fun showExceptions(exceptions: Map<HotfixData?, List<VcsException>>) {
    if (exceptions.values.any { it.isNotEmpty() }) {
      AbstractVcsHelper.getInstance(project).showErrors(exceptions, VcsBundle.message("message.title.vcs.update.errors", actionName))
    }
  }

  private fun showUpdateTree(wasCancelled: Boolean, updateNumber: Int): UpdateInfoTree? {
    val title = actionName + if (updateNumber > 1) "#$updateNumber" else ""
    val tree = ProjectLevelVcsManagerEx.getInstanceEx(project).showUpdateProjectInfo(updatedFiles, title, actionInfo, wasCancelled)
               ?: return null
    RestoreUpdateTree.getInstance(project).registerUpdateInformation(updatedFiles, actionInfo)

    with(tree) {
      setBefore(before)
      setAfter(after)
      setCanGroupByChangeList(canGroupByChangelist(spec.map { it.vcs }))
    }

    CommittedChangesCache.getInstance(project).processUpdatedFiles(updatedFiles) {
      tree.setChangeLists(it)
    }
    return tree
  }

  private fun canGroupByChangelist(vcses: Collection<AbstractVcs>): Boolean {
    if (actionInfo.canGroupByChangelist()) {
      for (vcs in vcses) {
        if (vcs.getCachingCommittedChangesProvider() != null) {
          return true
        }
      }
    }
    return false
  }
}

private fun refreshFilesStatus(project: Project, updatedFiles: UpdatedFiles) {
  RemoteRevisionsCache.getInstance(project).invalidate(updatedFiles)

  val files = ArrayList<VirtualFile>()
  UpdateFilesHelper.iterateFileGroupFiles(updatedFiles) { filePath, _ ->
    val path = VfsUtilCore.pathToUrl(filePath.replace(File.separatorChar, '/'))
    val file = VirtualFileManager.getInstance().findFileByUrl(path)
    if (file != null) {
      files.add(file)
    }
  }
  VcsDirtyScopeManager.getInstance(project).filesDirty(files, null)
}

private fun notifyAnnotations(project: Project, updatedFiles: UpdatedFiles) {
  val refresher = project.getMessageBus().syncPublisher<VcsAnnotationRefresher>(VcsAnnotationRefresher.LOCAL_CHANGES_CHANGED)
  UpdateFilesHelper.iterateFileGroupFilesDeletedOnServerFirst(updatedFiles) { filePath, _ -> refresher.dirty(filePath) }
}

private fun notifyFiles(project: Project, updatedFiles: UpdatedFiles) {
  val updatedPaths = UpdatedFilesReverseSide.getPathsFromUpdatedFiles(updatedFiles)
  project.getMessageBus().syncPublisher<UpdatedFilesListener>(UpdatedFilesListener.UPDATED_FILES).consume(updatedPaths)
}

private fun getFilesCount(group: FileGroup): Int = group.getFiles().size + group.children.sumOf { getFilesCount(it) }

private fun prepareUpdatedFilesNotification(
  someSessionWasCancelled: Boolean,
  updatedFiles: UpdatedFiles,
  additionalContent: List<String>,
  tree: UpdateInfoTree,
): Notification {
  val allFilesCount = updatedFiles.topLevelGroups.sumOf { getFilesCount(it) }

  @Suppress("DialogTitleCapitalization")
  val title = if (someSessionWasCancelled) {
    VcsBundle.message("update.notification.title.project.partially.updated")
  }
  else {
    VcsBundle.message("update.notification.title.count.files.updated", allFilesCount)
  }

  val content = HtmlBuilder()
  val text = if (someSessionWasCancelled) {
    HtmlChunk.text(VcsBundle.message("update.notification.content.files.updated", allFilesCount))
  }
  else {
    prepareScopeUpdatedText(tree)
  }
  content.append(text)

  val additionalContent = additionalContent.map {
    @Suppress("HardCodedStringLiteral")
    HtmlChunk.raw(it)
  }
  if (!additionalContent.isEmpty()) {
    if (!content.isEmpty) {
      content.append(HtmlChunk.br())
    }
    content.appendWithSeparators(HtmlChunk.text(", "), additionalContent)
  }

  val type = if (someSessionWasCancelled) NotificationType.WARNING else NotificationType.INFORMATION
  return VcsNotifier.standardNotification().createNotification(title, content.toString(), type)
    .setDisplayId(VcsNotificationIdsHolder.PROJECT_PARTIALLY_UPDATED)
}

private fun prepareScopeUpdatedText(tree: UpdateInfoTree): @Nls HtmlChunk {
  val scopeFilter = tree.getFilterScope() ?: return HtmlChunk.empty()
  val filteredFiles = tree.getFilteredFilesCount()
  val filterName = scopeFilter.presentableName
  if (filteredFiles == 0) {
    return HtmlChunk.text(VcsBundle.message("update.file.name.wasn.t.modified", filterName))
  }
  else {
    return HtmlChunk.text(VcsBundle.message("update.filtered.files.count.in.filter.name", filteredFiles, filterName))
  }
}

private fun someSessionWasCanceled(updateSessions: List<UpdateSession>): Boolean = updateSessions.any { it.isCanceled() }

private fun getAllFilesAreUpToDateMessage(roots: Array<FilePath>): @NlsContexts.NotificationContent String {
  if (roots.size == 1 && !roots[0].isDirectory()) {
    return VcsBundle.message("message.text.file.is.up.to.date")
  }
  else {
    return VcsBundle.message("message.text.all.files.are.up.to.date")
  }
}

private fun collectExceptions(vcs: AbstractVcs, exceptionList: List<VcsException>): Map<HotfixData?, List<VcsException>> {
  val fixer = vcs.vcsExceptionsHotFixer
  if (exceptionList.isEmpty()) return emptyMap()
  if (fixer == null) {
    return mapOf(null to exceptionList)
  }
  else {
    return fixer.groupExceptions(ActionType.update, exceptionList)
  }
}

private data class ContextInfo(
  val continueChain: Boolean,
  val interruptedExceptions: Map<HotfixData?, List<VcsException>>,
)

private fun Map<AbstractVcs, SequentialUpdatesContext?>.fold(): ContextInfo {
  var continueChain = false
  val exceptions = mutableMapOf<HotfixData?, MutableList<VcsException>>()
  for ((vcs, context) in entries) {
    if (context == null) {
      continue
    }
    continueChain = continueChain || context.shouldFail() // WAT? Why shouldFail? Will only be true for CVS. Do we even want to support it?

    if (!context.shouldFail()) {
      continue
    }
    val exception = VcsException(context.getMessageWhenInterruptedBeforeStart())
    val newExceptions = collectExceptions(vcs, listOf(exception))
    exceptions.putAllNonEmpty(newExceptions)
  }
  return ContextInfo(continueChain, exceptions)
}

private fun <K, V> MutableMap<K, MutableList<V>>.putAllNonEmpty(map: Map<K, List<V>>) {
  for ((key, values) in map) {
    if (values.isEmpty()) continue
    computeIfAbsent(key) { mutableListOf() }.addAll(values)
  }
}

@Service(Service.Level.PROJECT)
private class ProjectVcsUpdateTaskExecutor(val cs: CoroutineScope)