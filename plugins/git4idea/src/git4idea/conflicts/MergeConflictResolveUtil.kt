// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.conflicts

import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManagerEx
import com.intellij.diff.DiffRequestFactory
import com.intellij.diff.chains.DiffRequestProducerException
import com.intellij.diff.merge.MergeRequest
import com.intellij.diff.merge.MergeRequestProducer
import com.intellij.diff.merge.MergeResult
import com.intellij.diff.merge.MergeUtil
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.WindowWrapper
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import com.intellij.ui.LightColors
import com.intellij.util.Consumer
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.CalledInAwt
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame

object MergeConflictResolveUtil {
  private val LOG = Logger.getInstance(MergeConflictResolveUtil::class.java)
  private val ACTIVE_MERGE_WINDOW = Key.create<WindowWrapper>("ResolveConflictsWindow")

  @JvmStatic
  @CalledInAwt
  fun showMergeWindow(project: Project, file: VirtualFile?, resolverComputer: () -> GitMergeHandler.Resolver) {
    if (focusActiveMergeWindow(file)) return

    val title = if (file != null) MergeDialogCustomizer().getMergeWindowTitle(file) else "Merge"

    val windowHandler = Consumer<WindowWrapper> { wrapper -> putActiveWindowKey(project, wrapper, file) }
    val hints = DiffDialogHints(WindowWrapper.Mode.FRAME, null, windowHandler)

    val producer = MyProducer(project, title, resolverComputer)

    // TODO: support non-modal external tools (notification?)
    DiffManagerEx.getInstance().showMergeBuiltin(project, producer, hints)
  }

  private class MyProducer(val project: Project,
                           val title: String,
                           val resolverComputer: () -> GitMergeHandler.Resolver) : MergeRequestProducer {
    override fun getName(): String = title

    override fun process(context: UserDataHolder, indicator: ProgressIndicator): MergeRequest {
      try {
        val resolver = resolverComputer()
        val file = resolver.virtualFile

        val resolveCallback = { result: MergeResult ->
          val document = FileDocumentManager.getInstance().getCachedDocument(file)
          if (document != null) FileDocumentManager.getInstance().saveDocument(document)

          MergeUtil.reportProjectFileChangeIfNeeded(project, file)

          if (result != MergeResult.CANCEL) {
            runBackgroundableTask("Finishing Conflict Resolve", project, false) {
              resolver.onConflictResolved()
              VcsDirtyScopeManager.getInstance(project).filesDirty(listOf(file), emptyList())
            }
          }
        }

        val mergeData = resolver.mergeData
        val byteContents = listOf(mergeData.CURRENT, mergeData.ORIGINAL, mergeData.LAST)

        val request = runReadAction {
          DiffRequestFactory.getInstance().createMergeRequest(project, file, byteContents,
                                                              resolver.windowTitle, resolver.contentTitles,
                                                              resolveCallback)
        }
        MergeUtil.putRevisionInfos(request, mergeData)
        // TODO: notify MergeRequestProcessor about conflict invalidation

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

    window.addWindowListener(object : WindowAdapter() {
      override fun windowClosed(e: WindowEvent?) {
        window.removeWindowListener(this)
        file.putUserData(ACTIVE_MERGE_WINDOW, null)
        EditorNotifications.getInstance(project).updateNotifications(file)
      }
    })
  }

  private fun focusActiveMergeWindow(file: VirtualFile?): Boolean {
    val wrapper = file?.getUserData(ACTIVE_MERGE_WINDOW) ?: return false
    UIUtil.toFront(wrapper.window)
    return true
  }

  class NotificationProvider : EditorNotifications.Provider<EditorNotificationPanel>() {
    private val KEY = Key.create<EditorNotificationPanel>("MergeConflictResolveUtil.NotificationProvider")

    override fun getKey(): Key<EditorNotificationPanel> = KEY

    override fun createNotificationPanel(file: VirtualFile, fileEditor: FileEditor, project: Project): EditorNotificationPanel? {
      val wrapper = file.getUserData(ACTIVE_MERGE_WINDOW) ?: return null
      val panel = EditorNotificationPanel(LightColors.SLIGHTLY_GREEN)
      panel.setText("Merge conflicts resolve in progress.")
      panel.createActionLabel("Focus Window") { UIUtil.toFront(wrapper.window) }
      panel.createActionLabel("Cancel Resolve") { wrapper.close() }
      return panel
    }
  }
}