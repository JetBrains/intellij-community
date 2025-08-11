// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.conflicts

import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManagerEx
import com.intellij.diff.DiffRequestFactory
import com.intellij.diff.chains.DiffRequestProducerException
import com.intellij.diff.merge.*
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.WindowWrapper
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vcs.impl.BackgroundableActionLock
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer
import com.intellij.openapi.vcs.merge.MergeUtils
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import com.intellij.util.Consumer
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import com.intellij.vcsUtil.VcsUtil
import git4idea.i18n.GitBundle
import git4idea.index.ui.createMergeHandler
import git4idea.repo.GitConflict
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.status.GitStagingAreaHolder.StagingAreaListener
import java.util.function.Function
import javax.swing.JComponent
import javax.swing.JFrame

object MergeConflictResolveUtil {
  private val ACTIVE_MERGE_WINDOW = Key.create<WindowWrapper>("ResolveConflictsWindow")

  @RequiresEdt
  fun showMergeWindow(project: Project,
                      file: VirtualFile?,
                      lock: BackgroundableActionLock,
                      resolverComputer: () -> GitMergeHandler.Resolver) {
    if (focusActiveMergeWindow(file)) return

    if (lock.isLocked) return
    lock.lock()

    val title = if (file != null) MergeDialogCustomizer().getMergeWindowTitle(file) else DiffBundle.message("merge.files.dialog.title")

    val windowHandler = Consumer<WindowWrapper> { wrapper ->
      UIUtil.runWhenWindowClosed(wrapper.window) { lock.unlock() }
      putActiveWindowKey(project, wrapper, file)
    }
    val hints = DiffDialogHints(WindowWrapper.Mode.FRAME, null, windowHandler)

    val producer = MyProducer(project, title, resolverComputer)

    DiffManagerEx.getInstance().showMergeBuiltin(project, producer, hints)
  }

  private class MyProducer(val project: Project,
                           val title: String,
                           val resolverComputer: () -> GitMergeHandler.Resolver) : MergeRequestProducer {
    override fun getName(): String = title

    override fun process(context: UserDataHolder, indicator: ProgressIndicator): MergeRequest {
      try {
        val resolver = resolverComputer()

        val mergeData = resolver.mergeData
        val byteContents = listOf(mergeData.CURRENT, mergeData.ORIGINAL, mergeData.LAST)
        val request = DiffRequestFactory.getInstance().createMergeRequest(project, resolver.virtualFile, byteContents,
                                                                          mergeData.CONFLICT_TYPE,
                                                                          resolver.windowTitle, resolver.contentTitles)
        resolver.titleCustomizerList.run {
          DiffUtil.addTitleCustomizers(request, listOf(leftTitleCustomizer, centerTitleCustomizer, rightTitleCustomizer))
        }
        MergeUtils.putRevisionInfos(request, mergeData)
        MergeCallback.register(request, MyMergeCallback(resolver))
        return request
      }
      catch (e: Throwable) {
        throw DiffRequestProducerException(e)
      }
    }
  }

  private fun putActiveWindowKey(project: Project, wrapper: WindowWrapper, file: VirtualFile?) {
    if (file == null) return
    val window = wrapper.window
    if (window !is JFrame) return

    file.putUserData(ACTIVE_MERGE_WINDOW, wrapper)
    updateMergeConflictEditorNotifications(project)

    UIUtil.runWhenWindowClosed(window) {
      file.putUserData(ACTIVE_MERGE_WINDOW, null)
      updateMergeConflictEditorNotifications(project)
    }
  }

  private fun getActiveMergeWindow(file: VirtualFile): WindowWrapper? {
    return file.getUserData(ACTIVE_MERGE_WINDOW)?.takeIf { !it.isDisposed }
  }

  internal fun hasActiveMergeWindow(file: VirtualFile) = getActiveMergeWindow(file) != null

  private fun focusActiveMergeWindow(file: VirtualFile?): Boolean {
    if (file == null) return false
    val wrapper = getActiveMergeWindow(file) ?: return false
    UIUtil.toFront(wrapper.window)
    return true
  }

  private class MyMergeCallback(private val resolver: GitMergeHandler.Resolver) : MergeCallback() {
    override fun applyResult(result: MergeResult) {
      val project = resolver.project
      val file = resolver.virtualFile

      val document = FileDocumentManager.getInstance().getCachedDocument(file)
      if (document != null) {
        application.runWriteAction {
          FileDocumentManager.getInstance().saveDocument(document)
        }
      }

      MergeUtil.reportProjectFileChangeIfNeeded(project, file)

      if (result != MergeResult.CANCEL) {
        runBackgroundableTask(GitBundle.message("progress.finishing.conflict.resolve"), project, false) {
          resolver.onConflictResolved(result)
        }
      }
    }

    override fun checkIsValid(): Boolean {
      return resolver.checkIsValid()
    }

    override fun addListener(listener: Listener, disposable: Disposable) {
      resolver.addListener(listener, disposable)
    }
  }


  class NotificationProvider : EditorNotificationProvider, DumbAware {
    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
      if (hasActiveMergeWindow(file)) {
        return Function { fileEditor -> createPanelForOngoingResolve(fileEditor, file) }
      }

      val conflict = findConflictFor(project, file)
      if (conflict != null && GitConflictsUtil.canShowMergeWindow(project, createMergeHandler(project), conflict)) {
        return Function { fileEditor -> createPanelForFileWithConflict(project, fileEditor, file) }
      }

      return null
    }

    private fun createPanelForOngoingResolve(fileEditor: FileEditor, file: VirtualFile): EditorNotificationPanel? {
      if (!hasActiveMergeWindow(file)) return null

      val panel = EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info)
      panel.text = GitBundle.message("link.label.editor.notification.merge.conflicts.resolve.in.progress")
      panel.createActionLabel(GitBundle.message("link.label.merge.conflicts.resolve.in.progress.focus.window")) {
        UIUtil.toFront(getActiveMergeWindow(file)?.window)
      }
      panel.createActionLabel(GitBundle.message("link.label.merge.conflicts.resolve.in.progress.cancel.resolve")) {
        getActiveMergeWindow(file)?.close()
      }
      return panel
    }

    private fun createPanelForFileWithConflict(project: Project, fileEditor: FileEditor, file: VirtualFile): EditorNotificationPanel {
      val panel = EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning)
      panel.text = GitBundle.message("link.label.editor.notification.merge.conflicts.suggest.resolve")
      panel.createActionLabel(GitBundle.message("link.label.merge.conflicts.suggest.resolve.show.window")) {
        showMergeWindow(project, file)
      }
      return panel
    }

    private fun showMergeWindow(project: Project, file: VirtualFile) {
      val conflict = findConflictFor(project, file) ?: return
      GitConflictsUtil.showMergeWindow(project, createMergeHandler(project), listOf(conflict))
    }

    private fun findConflictFor(project: Project, file: VirtualFile): GitConflict? {
      val repo = GitRepositoryManager.getInstance(project).getRepositoryForFileQuick(file) ?: return null
      return repo.stagingAreaHolder.findConflict(VcsUtil.getFilePath(file))
    }
  }

  class MyStagingAreaListener : StagingAreaListener {
    override fun stagingAreaChanged(repository: GitRepository) {
      val project = repository.project
      runInEdt(ModalityState.nonModal()) {
        updateMergeConflictEditorNotifications(project)
      }
    }
  }
}

fun updateMergeConflictEditorNotifications(project: Project) {
  if (project.isDisposed) return
  //val provider: MergeConflictResolveUtil.NotificationProvider =
  //  EditorNotificationProvider.EP_NAME.findExtension(MergeConflictResolveUtil.NotificationProvider::class.java, project) ?: return
  EditorNotifications.getInstance(project).updateAllNotifications()
}
