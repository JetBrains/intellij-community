// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.update

import com.intellij.configurationStore.StoreReloadManager
import com.intellij.history.Label
import com.intellij.history.LocalHistory
import com.intellij.history.LocalHistoryAction
import com.intellij.ide.errorTreeView.HotfixData
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
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
import org.jetbrains.annotations.NonNls
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

private val LOG = logger<VcsUpdateTask>()

internal open class VcsUpdateTask(
  private val project: Project,
  private val roots: Array<FilePath>,
  private val spec: List<VcsUpdateSpecification>,
  private val actionInfo: ActionInfo,
  private val actionName: @Nls @NlsContexts.ProgressTitle String,
) {
  private val projectLevelVcsManager = ProjectLevelVcsManagerEx.getInstanceEx(project)
  protected var updatedFiles: UpdatedFiles = UpdatedFiles.create()
  private val groupedExceptions = HashMap<HotfixData?, MutableList<VcsException>>()
  private val updateSessions = ArrayList<UpdateSession>()
  private var updateNumber = 1

  // vcs name, context object
  // create from outside without any context; context is created by vcses
  private val contextInfo = HashMap<AbstractVcs, SequentialUpdatesContext?>()
  private val dirtyScopeManager = VcsDirtyScopeManager.getInstance(project)

  private var before: Label? = null
  private var after: Label? = null
  private var localHistoryAction: LocalHistoryAction? = null

  private fun reset() {
    updatedFiles = UpdatedFiles.create()
    groupedExceptions.clear()
    updateSessions.clear()
    ++updateNumber
  }

  fun launch() {
    project.service<ProjectVcsUpdateTaskExecutor>().cs.launch(Dispatchers.Default) {
      try {
        withBackgroundProgress(project, actionName) {
          coroutineToIndicator {
            run(it)
          }
        }
        withContext(Dispatchers.UiWithModelAccess) {
          onSuccess()
        }
      }
      catch (ce: CancellationException) {
        withContext(Dispatchers.UiWithModelAccess) {
          onSuccessImpl(true)
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
    projectLevelVcsManager.startBackgroundVcsOperation()

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
        val exceptionList = updateSession.getExceptions()
        gatherExceptions(vcs, exceptionList)
        updateSessions.add(updateSession)
      }
    }
    finally {
      try {
        progressIndicator.checkCanceled()
        progressIndicator.setText(VcsBundle.message("progress.text.synchronizing.files"))
        progressIndicator.setText2("")
        doVfsRefresh()
      }
      finally {
        projectLevelVcsManager.stopBackgroundVcsOperation()
        project.getMessageBus().syncPublisher<UpdatedFilesListener>(UpdatedFilesListener.UPDATED_FILES)
          .consume(UpdatedFilesReverseSide.getPathsFromUpdatedFiles(updatedFiles))
        activity.finished()
      }
    }
  }

  private fun gatherExceptions(vcs: AbstractVcs, exceptionList: MutableList<VcsException>) {
    val fixer = vcs.vcsExceptionsHotFixer
    if (fixer == null) {
      putExceptions(null, exceptionList)
    }
    else {
      putExceptions(fixer.groupExceptions(ActionType.update, exceptionList))
    }
  }

  private fun putExceptions(map: Map<HotfixData, List<VcsException>>) {
    for (entry in map.entries) {
      putExceptions(entry.key, entry.value)
    }
  }

  private fun putExceptions(key: HotfixData?, list: List<VcsException>) {
    if (list.isEmpty()) {
      return
    }
    groupedExceptions.computeIfAbsent(key) { ArrayList() }.addAll(list)
  }

  private fun doVfsRefresh() {
    LOG.info("Calling refresh files after update for roots: ${roots.contentToString()}")
    RefreshVFsSynchronously.updateAllChanged(updatedFiles)
    notifyAnnotations()
  }

  private fun notifyAnnotations() {
    val refresher = project.getMessageBus().syncPublisher<VcsAnnotationRefresher>(VcsAnnotationRefresher.LOCAL_CHANGES_CHANGED)
    UpdateFilesHelper.iterateFileGroupFilesDeletedOnServerFirst(updatedFiles) { filePath, _ -> refresher.dirty(filePath) }
  }

  private fun prepareNotification(
    tree: UpdateInfoTree,
    someSessionWasCancelled: Boolean,
    updateSessions: List<UpdateSession>,
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
    content.append(if (someSessionWasCancelled) {
      HtmlChunk.text(VcsBundle.message("update.notification.content.files.updated", allFilesCount))
    }
                   else {
      prepareScopeUpdatedText(tree)
    })

    val additionalContent = updateSessions.asSequence()
      .map { it.additionalNotificationContent }
      .filterNotNull()
      .map {
        @Suppress("HardCodedStringLiteral")
        HtmlChunk.raw(it)
      }
      .toList()
    if (!additionalContent.isEmpty()) {
      if (!content.isEmpty) {
        content.append(HtmlChunk.br())
      }
      content.appendWithSeparators(HtmlChunk.text(", "), additionalContent)
    }

    val type = if (someSessionWasCancelled) NotificationType.WARNING else NotificationType.INFORMATION
    return VcsNotifier.standardNotification()
      .createNotification(title, content.toString(), type).setDisplayId(VcsNotificationIdsHolder.PROJECT_PARTIALLY_UPDATED)
  }

  @RequiresEdt
  protected open fun onSuccess() {
    onSuccessImpl(false)
  }

  private fun onSuccessImpl(wasCanceled: Boolean) {
    if (!project.isOpen() || project.isDisposed()) {
      LocalHistory.getInstance().putSystemLabel(project, VcsBundle.message("activity.name.update")) // TODO check why this label is needed
      return
    }

    var continueChain = false
    for (context in contextInfo.values) {
      continueChain = continueChain or (context != null && (context.shouldFail()))
    }
    val continueChainFinal = continueChain

    val someSessionWasCancelled = wasCanceled || someSessionWasCanceled(updateSessions)
    // here text conflicts might be interactively resolved
    for (updateSession in updateSessions) {
      updateSession.onRefreshFilesCompleted()
    }
    // only after conflicts are resolved, put a label
    localHistoryAction?.finish()
    after = LocalHistory.getInstance().putSystemLabel(project, VcsBundle.message("update.label.after.update"))

    if (actionInfo.canChangeFileStatus()) {
      val files = ArrayList<VirtualFile>()
      val revisionsCache = RemoteRevisionsCache.getInstance(project)
      revisionsCache.invalidate(updatedFiles)
      UpdateFilesHelper.iterateFileGroupFiles(updatedFiles, object : UpdateFilesHelper.Callback {
        override fun onFile(filePath: String, groupId: String?) {
          val path: @NonNls String = VfsUtilCore.pathToUrl(filePath.replace(File.separatorChar, '/'))
          val file = VirtualFileManager.getInstance().findFileByUrl(path) ?: return
          files.add(file)
        }
      })
      dirtyScopeManager.filesDirty(files, null)
    }

    val updateSuccess = !someSessionWasCancelled && groupedExceptions.isEmpty()

    if (project.isDisposed()) {
      StoreReloadManager.getInstance(project).unblockReloadingProjectOnExternalChanges()
      return
    }

    if (!groupedExceptions.isEmpty()) {
      if (continueChainFinal) {
        gatherContextInterruptedMessages()
      }
      AbstractVcsHelper.getInstance(project).showErrors(groupedExceptions,
                                                        VcsBundle.message("message.title.vcs.update.errors", actionName))
    }
    else if (someSessionWasCancelled) {
      ProgressManager.progress(VcsBundle.message("progress.text.updating.canceled"))
    }
    else {
      ProgressManager.progress(VcsBundle.message("progress.text.updating.done"))
    }

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
      if (updateSessions.size == 1 && VcsUpdateProcess.checkUpdateHasCustomNotification(spec.map { it.vcs })) {
        // multi-vcs projects behave as before: only a compound notification & file tree is shown for them, for the sake of simplicity
        updateSessions.get(0).showNotification()
      }
      else {
        val tree = showUpdateTree(continueChainFinal && updateSuccess && noMerged, someSessionWasCancelled)
        val cache = CommittedChangesCache.getInstance(project)
        cache.processUpdatedFiles(updatedFiles) {
          tree.setChangeLists(it)
        }

        val notification = prepareNotification(tree, someSessionWasCancelled, updateSessions)
        notification.addAction(
          ViewUpdateInfoNotification(project, tree, VcsBundle.message("update.notification.content.view"), notification))
        VcsNotifier.getInstance(project).notify(notification)
      }
    }

    StoreReloadManager.getInstance(project).unblockReloadingProjectOnExternalChanges()

    if (continueChainFinal && updateSuccess) {
      if (noMerged) {
        // trigger the next update; for CVS when updating from several branches simultaneously
        reset()
        launch()
      }
      else {
        showContextInterruptedError()
      }
    }
  }

  private fun showContextInterruptedError() {
    gatherContextInterruptedMessages()
    AbstractVcsHelper.getInstance(project).showErrors(groupedExceptions,
                                                      VcsBundle.message("message.title.vcs.update.errors", actionName))
  }

  private fun gatherContextInterruptedMessages() {
    for (entry in contextInfo.entries) {
      val context = entry.value
      if (context == null || !context.shouldFail()) {
        continue
      }
      val exception = VcsException(context.getMessageWhenInterruptedBeforeStart())
      gatherExceptions(entry.key, mutableListOf(exception))
    }
  }

  private fun showUpdateTree(willBeContinued: Boolean, wasCanceled: Boolean): UpdateInfoTree {
    val restoreUpdateTree = RestoreUpdateTree.getInstance(project)
    restoreUpdateTree.registerUpdateInformation(updatedFiles, actionInfo)
    val text = actionName + (if (willBeContinued || (updateNumber > 1)) ("#$updateNumber") else "")
    val updateInfoTree = projectLevelVcsManager.showUpdateProjectInfo(updatedFiles, text, actionInfo, wasCanceled)!!
    updateInfoTree.setBefore(before)
    updateInfoTree.setAfter(after)
    updateInfoTree.setCanGroupByChangeList(canGroupByChangelist(spec.map { it.vcs }))
    return updateInfoTree
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

private fun getFilesCount(group: FileGroup): Int = group.getFiles().size + group.children.sumOf { getFilesCount(it) }

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

@Service(Service.Level.PROJECT)
private class ProjectVcsUpdateTaskExecutor(val cs: CoroutineScope)