// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.conflicts

import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManagerEx
import com.intellij.diff.DiffRequestFactory
import com.intellij.diff.chains.DiffRequestProducerException
import com.intellij.diff.merge.*
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.Disposable
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.WindowWrapper
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vcs.impl.BackgroundableActionLock
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer
import com.intellij.openapi.vcs.merge.MergeUtils
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import com.intellij.ui.LightColors
import com.intellij.util.Consumer
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import git4idea.i18n.GitBundle
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
                                                                          resolver.windowTitle, resolver.contentTitles)
        resolver.titleCustomizerList.run {
          request.putUserData(DiffUserDataKeysEx.EDITORS_TITLE_CUSTOMIZER,
                              listOf(leftTitleCustomizer, centerTitleCustomizer, rightTitleCustomizer))
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
    EditorNotifications.getInstance(project).updateNotifications(file)

    UIUtil.runWhenWindowClosed(window) {
      file.putUserData(ACTIVE_MERGE_WINDOW, null)
      EditorNotifications.getInstance(project).updateNotifications(file)
    }
  }

  private fun focusActiveMergeWindow(file: VirtualFile?): Boolean {
    val wrapper = file?.getUserData(ACTIVE_MERGE_WINDOW) ?: return false
    UIUtil.toFront(wrapper.window)
    return true
  }

  private class MyMergeCallback(private val resolver: GitMergeHandler.Resolver) : MergeCallback() {
    override fun applyResult(result: MergeResult) {
      val project = resolver.project
      val file = resolver.virtualFile

      val document = FileDocumentManager.getInstance().getCachedDocument(file)
      if (document != null) FileDocumentManager.getInstance().saveDocument(document)

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


  class NotificationProvider : EditorNotifications.Provider<EditorNotificationPanel>() {
    private val KEY = Key.create<EditorNotificationPanel>("MergeConflictResolveUtil.NotificationProvider")

    override fun getKey(): Key<EditorNotificationPanel> = KEY

    override fun createNotificationPanel(file: VirtualFile, fileEditor: FileEditor, project: Project): EditorNotificationPanel? {
      val wrapper = file.getUserData(ACTIVE_MERGE_WINDOW) ?: return null
      val panel = EditorNotificationPanel(LightColors.SLIGHTLY_GREEN)
      panel.setText(GitBundle.message("link.label.editor.notification.merge.conflicts.resolve.in.progress"))
      panel.createActionLabel(GitBundle.message("link.label.merge.conflicts.resolve.in.progress.focus.window")) { UIUtil.toFront(wrapper.window) }
      panel.createActionLabel(GitBundle.message("link.label.merge.conflicts.resolve.in.progress.cancel.resolve")) { wrapper.close() }
      return panel
    }
  }
}