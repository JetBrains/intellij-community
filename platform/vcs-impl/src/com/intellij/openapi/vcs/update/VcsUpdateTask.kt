// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.update

import com.intellij.configurationStore.StoreReloadManager
import com.intellij.configurationStore.saveSettings
import com.intellij.history.Label
import com.intellij.history.LocalHistory
import com.intellij.ide.errorTreeView.HotfixData
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.checkCanceled
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
import com.intellij.platform.util.progress.reportSequentialProgress
import com.intellij.vcs.VcsActivity
import com.intellij.vcs.ViewUpdateInfoNotification
import kotlinx.coroutines.Dispatchers
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
  suspend fun execute() {
    // to ensure that if as a result of Update some project settings will be changed,
    // all local changes are saved in prior and do not overwrite remote changes.
    // Also, there is a chance that save during update can break it -
    // we do disable auto saving during update, but still, there is a chance that save will occur.
    edtWriteAction {
      FileDocumentManager.getInstance().saveAllDocuments()
    }
    StoreReloadManager.getInstance(project).blockReloadingProjectOnExternalChanges()
    try {
      saveSettings(project)

      val context = mutableMapOf<AbstractVcs, SequentialUpdatesContext?>()
      var executionIndex = 0
      var continueChain = false
      withContext(Dispatchers.Default) {
        do {
          checkCanceled()
          continueChain = executeUpdate(context, executionIndex)
          executionIndex += 1
        }
        while (continueChain)
      }
    }
    finally {
      StoreReloadManager.getInstance(project).unblockReloadingProjectOnExternalChanges()
    }
  }

  private suspend fun executeUpdate(context: MutableMap<AbstractVcs, SequentialUpdatesContext?>, executionCount: Int): Boolean {
    val beforeLabel = LocalHistory.getInstance().putSystemLabel(project, VcsBundle.message("update.label.before.update"))
    val localHistoryAction = LocalHistory.getInstance().startAction(VcsBundle.message("activity.name.update"), VcsActivity.Update)

    val updatedFiles = UpdatedFiles.create()
    val exceptions = mutableMapOf<HotfixData?, MutableList<VcsException>>()
    val updateSessions = mutableListOf<UpdateSession>()

    // intentionally ignore cancellation of the bg job to do the post-processing
    val updateResult = runCatching {
      executeUpdate(context, updatedFiles, exceptions, updateSessions)
    }
    checkCanceled()

    LocalHistory.getInstance().putSystemLabel(project, VcsBundle.message("activity.name.update")) // TODO check why this label is needed

    withContext(Dispatchers.UiWithModelAccess) {
      // here text conflicts might be interactively resolved
      for (updateSession in updateSessions) {
        checkCanceled()
        updateSession.onRefreshFilesCompleted()
      }
    }

    // only after conflicts are resolved, put a label
    localHistoryAction.finish()
    val afterLabel = LocalHistory.getInstance().putSystemLabel(project, VcsBundle.message("update.label.after.update"))
    checkCanceled()

    if (actionInfo.canChangeFileStatus()) {
      refreshFilesStatus(project, updatedFiles)
    }

    val contextInfo = context.fold()
    val runResult = UpdateRunResult(updatedFiles, exceptions, updateSessions, updateResult.exceptionOrNull() is CancellationException)
    val continueChain = withContext(Dispatchers.UiWithModelAccess) {
      handleResult(contextInfo, runResult, executionCount, beforeLabel, afterLabel)
    }
    updateResult.getOrThrow() // rethrow actual exceptions
    return continueChain
  }

  suspend fun executeUpdate(
    contextInfo: MutableMap<AbstractVcs, SequentialUpdatesContext?>,
    updatedFiles: UpdatedFiles,
    groupedExceptions: MutableMap<HotfixData?, MutableList<VcsException>>,
    updateSessions: MutableList<UpdateSession>,
  ) {
    val vcsManager = ProjectLevelVcsManager.getInstance(project)
    vcsManager.startBackgroundVcsOperation()
    val activity = VcsStatisticsCollector.UPDATE_ACTIVITY.started(project)
    try {
      withBackgroundProgress(project, actionName) {
        reportSequentialProgress(spec.size) { progress ->
          try {
            for ((vcs, updateEnvironment, files) in spec) {
              progress.itemStep {
                updateEnvironment.fillGroups(updatedFiles)

                val refContext = Ref(contextInfo[vcs])
                val updateSession = coroutineToIndicator {
                  updateEnvironment.updateDirectories(files.toTypedArray(), updatedFiles, it, refContext)
                }
                contextInfo[vcs] = refContext.get()

                val exceptionList = collectExceptions(vcs, updateSession.getExceptions())
                groupedExceptions.putAllNonEmpty(exceptionList)
                updateSessions.add(updateSession)
              }
            }
          }
          finally {
            checkCanceled()
            progress.indeterminateStep(VcsBundle.message("progress.text.synchronizing.files")) {
              LOG.info("Calling refresh files after update for roots: ${roots.contentToString()}")
              RefreshVFsSynchronously.updateAllChanged(updatedFiles)
            }
            notifyAnnotations(project, updatedFiles)
          }
        }
      }
    }
    finally {
      vcsManager.stopBackgroundVcsOperation()
      notifyFiles(project, updatedFiles)
      activity.finished()
    }
  }

  private fun handleResult(
    contextInfo: ContextInfo,
    runData: UpdateRunResult,
    executionIndex: Int,
    beforeLabel: Label?,
    afterLabel: Label?,
  ): Boolean {
    val (updatedFiles, groupedExceptions, updateSessions, cancelled) = runData
    val someSessionWasCancelled = cancelled || updateSessions.any(UpdateSession::isCanceled)

    val updateSuccess = !someSessionWasCancelled && groupedExceptions.isEmpty()
    val couldContinue = updateSuccess && contextInfo.continueChain
    val noMerged = updatedFiles.getGroupById(FileGroup.MERGED_WITH_CONFLICT_ID)?.isEmpty() ?: true
    val willBeContinued = couldContinue && noMerged

    if (updatedFiles.isEmpty && groupedExceptions.isEmpty()) {
      VcsNotifier.standardNotification().run {
        if (someSessionWasCancelled) {
          createNotification(VcsBundle.message("progress.text.updating.canceled"), NotificationType.WARNING)
        }
        else {
          createNotification(getAllFilesAreUpToDateMessage(roots), NotificationType.INFORMATION)
        }
      }.setDisplayId(VcsNotificationIdsHolder.PROJECT_UPDATE_FINISHED).notify(project)
    }
    else if (!updatedFiles.isEmpty) {
      val singleUpdateSession = updateSessions.singleOrNull()
      if (singleUpdateSession != null && VcsUpdateProcess.checkUpdateHasCustomNotification(spec.map { it.vcs })) {
        // multi-vcs projects behave as before: only a compound notification & file tree is shown for them, for the sake of simplicity
        singleUpdateSession.showNotification()
      }
      else {
        val updateNumber = if (willBeContinued || executionIndex > 0) executionIndex + 1 else 0
        val tree = showUpdateTree(updatedFiles, someSessionWasCancelled, updateNumber, beforeLabel, afterLabel)

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

    val exceptions = groupedExceptions.toMutableMap()
    if (couldContinue && !noMerged) {
      exceptions.putAllNonEmpty(contextInfo.interruptedExceptions)
    }
    showExceptions(exceptions)

    return willBeContinued
  }

  private fun showExceptions(exceptions: Map<HotfixData?, List<VcsException>>) {
    if (exceptions.values.any { it.isNotEmpty() }) {
      AbstractVcsHelper.getInstance(project).showErrors(exceptions, VcsBundle.message("message.title.vcs.update.errors", actionName))
    }
  }

  private fun showUpdateTree(
    updatedFiles: UpdatedFiles,
    wasCancelled: Boolean, updateNumber: Int,
    beforeLabel: Label?,
    afterLabel: Label?,
  ): UpdateInfoTree? {
    val title = actionName + if (updateNumber > 1) "#$updateNumber" else ""
    val tree = ProjectLevelVcsManagerEx.getInstanceEx(project).showUpdateProjectInfo(updatedFiles, title, actionInfo, wasCancelled)
               ?: return null
    RestoreUpdateTree.getInstance(project).registerUpdateInformation(updatedFiles, actionInfo)

    with(tree) {
      setBefore(beforeLabel)
      setAfter(afterLabel)
      setCanGroupByChangeList(canGroupByChangelist(spec.map { it.vcs }))
    }

    CommittedChangesCache.getInstance(project).processUpdatedFiles(updatedFiles) {
      tree.setChangeLists(it)
    }
    return tree
  }

  private fun canGroupByChangelist(vcses: Collection<AbstractVcs>): Boolean {
    if (!actionInfo.canGroupByChangelist()) return false
    return vcses.any { it.getCachingCommittedChangesProvider() != null }
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

private data class UpdateRunResult(
  val updatedFiles: UpdatedFiles,
  val groupedExceptions: Map<HotfixData?, MutableList<VcsException>>,
  val updateSessions: List<UpdateSession>,
  val cancelled: Boolean,
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