// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.openapi.vcs.update

import com.intellij.configurationStore.StoreReloadManager
import com.intellij.configurationStore.forPoorJavaClientOnlySaveProjectIndEdtDoNotUseThisMethod
import com.intellij.history.Label
import com.intellij.history.LocalHistory
import com.intellij.history.LocalHistoryAction
import com.intellij.ide.errorTreeView.HotfixData
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.actions.DescindingFilesFilter
import com.intellij.openapi.vcs.changes.RemoteRevisionsCache
import com.intellij.openapi.vcs.changes.VcsAnnotationRefresher
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.changes.actions.VcsStatisticsCollector
import com.intellij.openapi.vcs.changes.committed.CommittedChangesCache
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.containers.MultiMap
import com.intellij.util.ui.OptionsDialog
import com.intellij.vcs.VcsActivity
import com.intellij.vcs.ViewUpdateInfoNotification
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

private val LOG = Logger.getInstance(AbstractCommonUpdateAction::class.java)

abstract class AbstractCommonUpdateAction protected constructor(
  private val actionInfo: ActionInfo,
  private val scopeInfo: ScopeInfo,
  private val alwaysVisible: Boolean
) : DumbAwareAction() {
  companion object {
    @JvmStatic
    fun showsCustomNotification(vcss: Collection<AbstractVcs>): Boolean {
      return vcss.all { vcs ->
        val environment = vcs.updateEnvironment
        environment != null && environment.hasCustomNotification()
      }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  @Suppress("IncorrectCancellationExceptionHandling")
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    val showUpdateOptions = isShowOptions(project)

    LOG.debug { "project: $project, show update options: $showUpdateOptions" }

    if (project == null) {
      return
    }

    try {
      val roots = getRoots(project, e.dataContext)
      if (roots.isEmpty()) {
        LOG.debug("No roots found.")
        return
      }

      val vcsToVirtualFiles = createVcsToFilesMap(roots, project)
      for (vcs in vcsToVirtualFiles.keys) {
        val updateEnvironment = actionInfo.getEnvironment(vcs)
        if (updateEnvironment != null && !updateEnvironment.validateOptions(vcsToVirtualFiles.get(vcs))) {
          // messages already shown
          LOG.debug { "Options not valid for files: $vcsToVirtualFiles" }
          return
        }
      }

      if (showUpdateOptions || OptionsDialog.shiftIsPressed(e.modifiers)) {
        val scopeName = scopeInfo.getScopeName(e.dataContext, actionInfo)
        showOptionsDialog(vcsToVirtualFiles, project, scopeName)
      }

      if (ApplicationManager.getApplication().isDispatchThread()) {
        // Not only documents, but also project settings should be saved,
        // to ensure that if as a result of Update some project settings will be changed,
        // all local changes are saved in prior and do not overwrite remote changes.
        // Also, there is a chance that save during update can break it -
        // we do disable auto saving during update, but still, there is a chance that save will occur.
        FileDocumentManager.getInstance().saveAllDocuments()
        forPoorJavaClientOnlySaveProjectIndEdtDoNotUseThisMethod(project, false)
      }

      val task = object : Updater(project, roots, vcsToVirtualFiles, actionInfo, getTemplatePresentation().text) {
        override fun onSuccess() {
          super.onSuccess()
          this@AbstractCommonUpdateAction.onSuccess()
        }
      }

      if (ApplicationManager.getApplication().isUnitTestMode()) {
        task.run(EmptyProgressIndicator())
      }
      else {
        ProgressManager.getInstance().run(task)
      }
    }
    catch (_: ProcessCanceledException) {
    }
  }

  protected open fun isShowOptions(project: Project?): Boolean = actionInfo.showOptions(project)

  protected open fun onSuccess() {
  }

  private fun showOptionsDialog(
    updateEnvToVirtualFiles: Map<AbstractVcs, Collection<FilePath>>, project: Project?,
    scopeName: String?
  ) {
    val envToConfMap = createConfigurableToEnvMap(updateEnvToVirtualFiles)
    LOG.debug { "configurables map: $envToConfMap" }
    if (!envToConfMap.isEmpty()) {
      val dialogOrStatus = actionInfo.createOptionsDialog(project, envToConfMap, scopeName)
      if (!dialogOrStatus.showAndGet()) {
        throw ProcessCanceledException()
      }
    }
  }

  private fun getRoots(project: Project, context: DataContext): Array<FilePath> {
    val filePaths = scopeInfo.getRoots(context, actionInfo)
    return DescindingFilesFilter.filterDescindingFiles(filterRoots(project, filePaths), project)
  }

  private fun createConfigurableToEnvMap(updateEnvToVirtualFiles: Map<AbstractVcs, Collection<FilePath>>): LinkedHashMap<Configurable, AbstractVcs> {
    val envToConfMap = LinkedHashMap<Configurable, AbstractVcs>()
    for (vcs in updateEnvToVirtualFiles.keys) {
      val configurable = actionInfo.getEnvironment(vcs).createConfigurable(updateEnvToVirtualFiles.get(vcs))
      if (configurable != null) {
        envToConfMap.put(configurable, vcs)
      }
    }
    return envToConfMap
  }

  fun getConfigurableToEnvMap(project: Project): LinkedHashMap<Configurable, AbstractVcs> {
    val roots = getRoots(project, SimpleDataContext.getProjectContext(project))
    val vcsToFilesMap = createVcsToFilesMap(roots, project)
    return createConfigurableToEnvMap(vcsToFilesMap)
  }

  private fun createVcsToFilesMap(roots: Array<FilePath>, project: Project): Map<AbstractVcs, Collection<FilePath>> {
    val resultPrep = MultiMap.createSet<AbstractVcs, FilePath>()
    for (file in roots) {
      val vcs = VcsUtil.getVcsFor(project, file) ?: continue
      val updateEnvironment = actionInfo.getEnvironment(vcs)
      if (updateEnvironment != null) {
        resultPrep.putValue(vcs, file)
      }
    }

    val result = HashMap<AbstractVcs, MutableCollection<FilePath>>()
    for (entry in resultPrep.entrySet()) {
      val vcs = entry.key
      @Suppress("DEPRECATION")
      result.put(vcs, vcs.filterUniqueRoots(ArrayList(entry.value)) { it.getVirtualFile() })
    }
    return result
  }

  private fun filterRoots(project: Project, roots: MutableList<FilePath>): Array<FilePath> {
    val result = ArrayList<FilePath>()
    for (file in roots) {
      val vcs = VcsUtil.getVcsFor(project, file) ?: continue
      if (!scopeInfo.filterExistsInVcs() || AbstractVcs.fileInVcsByFileStatus(project, file)) {
        val updateEnvironment = actionInfo.getEnvironment(vcs)
        if (updateEnvironment != null) {
          result.add(file)
        }
      }
      else {
        val virtualFile = file.getVirtualFile()
        if (virtualFile != null && virtualFile.isDirectory()) {
          val vcsRoots = ProjectLevelVcsManager.getInstance(project).getAllVersionedRoots()
          for (vcsRoot in vcsRoots) {
            if (VfsUtilCore.isAncestor(virtualFile, vcsRoot, false)) {
              result.add(file)
            }
          }
        }
      }
    }
    return result.toTypedArray()
  }

  protected abstract fun filterRootsBeforeAction(): Boolean

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    val project = e.project

    if (project == null) {
      presentation.setEnabledAndVisible(false)
      return
    }

    val vcsManager = ProjectLevelVcsManager.getInstance(project)
    val underVcs = vcsManager.hasActiveVcss()
    if (!underVcs) {
      presentation.setEnabledAndVisible(false)
      return
    }

    val scopeName = scopeInfo.getScopeName(e.dataContext, actionInfo)
    var actionName = actionInfo.getActionName(scopeName)
    if (actionInfo.showOptions(project) || OptionsDialog.shiftIsPressed(e.modifiers)) {
      actionName += "..."
    }
    presentation.setText(actionName)

    if (supportingVcsesAreEmpty(vcsManager, actionInfo)) {
      presentation.setEnabledAndVisible(false)
      return
    }

    if (filterRootsBeforeAction()) {
      val filePaths = scopeInfo.getRoots(e.dataContext, actionInfo)
      val roots = filterRoots(project, filePaths)
      if (roots.isEmpty()) {
        presentation.setVisible(alwaysVisible)
        presentation.setEnabled(false)
        return
      }
    }

    val singleVcs = vcsManager.getSingleVCS()
    presentation.setVisible(true)
    presentation.setEnabled(!vcsManager.isBackgroundVcsOperationRunning() && (singleVcs == null || !singleVcs.isUpdateActionDisabled))
  }

  @ApiStatus.Internal
  open class Updater(
    project: Project,
    private val roots: Array<FilePath>,
    private val vcsToVirtualFiles: Map<AbstractVcs, Collection<FilePath>>,
    private val actionInfo: ActionInfo,
    private val actionName: @Nls @NlsContexts.ProgressTitle String
  ) : Task.Backgroundable(project, actionName, true) {
    private val projectLevelVcsManager = ProjectLevelVcsManagerEx.getInstanceEx(project)
    protected var updatedFiles: UpdatedFiles = UpdatedFiles.create()
    private val groupedExceptions = HashMap<HotfixData?, MutableList<VcsException>>()
    private val updateSessions = ArrayList<UpdateSession>()
    private var updateNumber = 1

    // vcs name, context object
    // create from outside without any context; context is created by vcses
    private val contextInfo = HashMap<AbstractVcs, SequentialUpdatesContext?>()
    private val dirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject!!)

    private var before: Label? = null
    private var after: Label? = null
    private var localHistoryAction: LocalHistoryAction? = null

    private fun reset() {
      updatedFiles = UpdatedFiles.create()
      groupedExceptions.clear()
      updateSessions.clear()
      ++updateNumber
    }

    override fun run(indicator: ProgressIndicator) {
      runImpl()
    }

    private fun runImpl() {
      val project = myProject
      if (project != null) {
        StoreReloadManager.getInstance(project).blockReloadingProjectOnExternalChanges()
      }
      projectLevelVcsManager.startBackgroundVcsOperation()

      val progressIndicator = ProgressManager.getInstance().getProgressIndicator()

      before = LocalHistory.getInstance().putSystemLabel(project!!, VcsBundle.message("update.label.before.update"))
      localHistoryAction = LocalHistory.getInstance().startAction(VcsBundle.message("activity.name.update"), VcsActivity.Update)
      progressIndicator?.setIndeterminate(false)
      val activity = VcsStatisticsCollector.UPDATE_ACTIVITY.started(project)
      try {
        val toBeProcessed = vcsToVirtualFiles.size
        var processed = 0
        for (vcs in vcsToVirtualFiles.keys) {
          val updateEnvironment = actionInfo.getEnvironment(vcs)
          updateEnvironment.fillGroups(updatedFiles)
          val files = vcsToVirtualFiles.get(vcs)!!

          val context = contextInfo.get(vcs)
          val refContext = Ref<SequentialUpdatesContext>(context)

          // actual update
          val updateSession = performUpdate(progressIndicator, updateEnvironment, files, refContext)

          contextInfo.put(vcs, refContext.get())
          processed++
          if (progressIndicator != null) {
            progressIndicator.setFraction(processed.toDouble() / toBeProcessed.toDouble())
            progressIndicator.setText2("")
          }
          val exceptionList = updateSession.getExceptions()
          gatherExceptions(vcs, exceptionList)
          updateSessions.add(updateSession)
        }
      }
      finally {
        try {
          ProgressManager.progress(VcsBundle.message("progress.text.synchronizing.files"))
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

    protected open fun performUpdate(
      progressIndicator: ProgressIndicator?,
      updateEnvironment: UpdateEnvironment,
      files: Collection<FilePath>,
      refContext: Ref<SequentialUpdatesContext>
    ): UpdateSession {
      return updateEnvironment.updateDirectories(files.toTypedArray(), updatedFiles, progressIndicator, refContext)
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
      val refresher = myProject!!.getMessageBus().syncPublisher<VcsAnnotationRefresher>(VcsAnnotationRefresher.LOCAL_CHANGES_CHANGED)
      UpdateFilesHelper.iterateFileGroupFilesDeletedOnServerFirst(updatedFiles) { filePath, _ -> refresher.dirty(filePath) }
    }

    private fun prepareNotification(
      tree: UpdateInfoTree,
      someSessionWasCancelled: Boolean,
      updateSessions: List<UpdateSession>
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

    override fun onSuccess() {
      onSuccessImpl(false)
    }

    private fun onSuccessImpl(wasCanceled: Boolean) {
      val project = myProject!!
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
            @Suppress("IO_FILE_USAGE") val path: @NonNls String = VfsUtilCore.pathToUrl(filePath.replace(java.io.File.separatorChar, '/'))
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
        AbstractVcsHelper.getInstance(project).showErrors(groupedExceptions, VcsBundle.message("message.title.vcs.update.errors",
                                                                                               actionName))
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
        if (updateSessions.size == 1 && showsCustomNotification(vcsToVirtualFiles.keys)) {
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
          ProgressManager.getInstance().run(this)
        }
        else {
          showContextInterruptedError()
        }
      }
    }

    private fun showContextInterruptedError() {
      gatherContextInterruptedMessages()
      AbstractVcsHelper.getInstance(myProject).showErrors(groupedExceptions,
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
      val restoreUpdateTree = RestoreUpdateTree.getInstance(myProject!!)
      restoreUpdateTree.registerUpdateInformation(updatedFiles, actionInfo)
      val text = actionName + (if (willBeContinued || (updateNumber > 1)) ("#$updateNumber") else "")
      val updateInfoTree = projectLevelVcsManager.showUpdateProjectInfo(updatedFiles, text, actionInfo, wasCanceled)!!
      updateInfoTree.setBefore(before)
      updateInfoTree.setAfter(after)
      updateInfoTree.setCanGroupByChangeList(canGroupByChangelist(vcsToVirtualFiles.keys))
      return updateInfoTree
    }

    private fun canGroupByChangelist(abstractVcses: Set<AbstractVcs>): Boolean {
      if (actionInfo.canGroupByChangelist()) {
        for (vcs in abstractVcses) {
          if (vcs.getCachingCommittedChangesProvider() != null) {
            return true
          }
        }
      }
      return false
    }

    override fun onCancel() {
      onSuccessImpl(true)
    }
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

private fun someSessionWasCanceled(updateSessions: List<UpdateSession>): Boolean = updateSessions.any { it.isCanceled()  }

private fun getAllFilesAreUpToDateMessage(roots: Array<FilePath>): @NlsContexts.NotificationContent String {
  if (roots.size == 1 && !roots[0].isDirectory()) {
    return VcsBundle.message("message.text.file.is.up.to.date")
  }
  else {
    return VcsBundle.message("message.text.all.files.are.up.to.date")
  }
}

private fun supportingVcsesAreEmpty(vcsManager: ProjectLevelVcsManager, actionInfo: ActionInfo): Boolean {
  return vcsManager.getAllActiveVcss().all { actionInfo.getEnvironment(it) == null }
}
