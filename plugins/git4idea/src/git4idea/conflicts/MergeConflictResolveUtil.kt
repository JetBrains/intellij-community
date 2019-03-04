// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.conflicts

import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManagerEx
import com.intellij.diff.DiffRequestFactory
import com.intellij.diff.InvalidDiffRequestException
import com.intellij.diff.merge.MergeResult
import com.intellij.diff.merge.MergeUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.WindowWrapper
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileTooBigException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Consumer
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.CalledInAwt
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame

object MergeConflictResolveUtil {
  private val LOG = Logger.getInstance(MergeConflictResolveUtil::class.java)
  private val ACTIVE_MERGE_WINDOW = Key.create<JFrame>("ResolveConflictsWindow")

  @JvmStatic
  @CalledInAwt
  fun showMergeWindow(project: Project, resolver: GitMergeHandler.Resolver) {
    val file = resolver.virtualFile
    if (focusActiveMergeWindow(file)) return

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

    try {
      val mergeData = resolver.mergeData
      val byteContents = listOf(mergeData.CURRENT, mergeData.ORIGINAL, mergeData.LAST)

      val request = DiffRequestFactory.getInstance().createMergeRequest(project, file, byteContents,
                                                                        resolver.windowTitle, resolver.contentTitles,
                                                                        resolveCallback)
      MergeUtil.putRevisionInfos(request, mergeData)
      // TODO: notify MergeRequestProcessor about conflict invalidation

      val windowHandler = Consumer<WindowWrapper> { wrapper -> putActiveWindowKey(wrapper.window, file) }
      val hints = DiffDialogHints(WindowWrapper.Mode.FRAME, null, windowHandler)

      // TODO: support non-modal external tools (notification?)
      DiffManagerEx.getInstance().showMergeBuiltin(project, request, hints)
    }
    catch (e: InvalidDiffRequestException) {
      if (e.cause is FileTooBigException) {
        VcsNotifier.getInstance(project).notifyError("Can't Show Merge Dialog", "File is too big to be loaded.")
      }
      else {
        LOG.error(e)
        VcsNotifier.getInstance(project).notifyError("Can't Show Merge Dialog", e.message!!)
      }
    }
  }

  private fun putActiveWindowKey(frame: Window, file: VirtualFile) {
    if (frame !is JFrame) return

    file.putUserData(ACTIVE_MERGE_WINDOW, frame)

    frame.addWindowListener(object : WindowAdapter() {
      override fun windowClosed(e: WindowEvent?) {
        frame.removeWindowListener(this)
        file.putUserData(ACTIVE_MERGE_WINDOW, null)
      }
    })
  }

  fun focusActiveMergeWindow(file: VirtualFile?): Boolean {
    val window = file?.getUserData(ACTIVE_MERGE_WINDOW) ?: return false
    UIUtil.toFront(window)
    return true
  }
}