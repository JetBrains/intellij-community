// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.conflicts

import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManagerEx
import com.intellij.diff.DiffRequestFactory
import com.intellij.diff.InvalidDiffRequestException
import com.intellij.diff.merge.MergeResult
import com.intellij.diff.merge.MergeUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diff.impl.mergeTool.MergeVersion
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.WindowWrapper
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileTooBigException
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Consumer
import com.intellij.util.ui.UIUtil
import com.intellij.vcsUtil.VcsUtil
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame

class MergeConflictResolveUtil {
  companion object {
    private val LOG = Logger.getInstance(MergeConflictResolveUtil::class.java)
    private val ACTIVE_MERGE_WINDOW = Key.create<JFrame>("ResolveConflictsWindow")

    fun showMergeWindow(project: Project, resolver: GitMergeHandler.Resolver, callback: (MergeResult) -> Unit) {
      val file = resolver.virtualFile
      if (focusActiveMergeWindow(file)) return

      val mergeData = resolver.mergeData
      val byteContents = listOf(mergeData.CURRENT, mergeData.ORIGINAL, mergeData.LAST)
      val contentTitles = listOf(resolver.leftPanelTitle, resolver.centerPanelTitle, resolver.rightPanelTitle)

      val resolveCallback = { result: MergeResult ->
        val document = FileDocumentManager.getInstance().getCachedDocument(file)
        if (document != null) FileDocumentManager.getInstance().saveDocument(document)
        MergeVersion.MergeDocumentVersion.reportProjectFileChangeIfNeeded(project, file)

        if (result != MergeResult.CANCEL) {
          VcsUtil.runVcsProcessWithProgress({ resolver.conflictResolved() }, "Finishing Conflict Resolve", false, project)
          VcsDirtyScopeManager.getInstance(project).filesDirty(listOf(file), emptyList())
        }

        callback(result)
      }

      try {
        val request = DiffRequestFactory.getInstance().createMergeRequest(project, file, byteContents,
                                                                          resolver.mergeWindowTitle, contentTitles, resolveCallback)
        MergeUtil.putRevisionInfos(request, mergeData)

        val windowHandler = Consumer<WindowWrapper> { wrapper -> putActiveWindowKey(wrapper.window as? JFrame, file) }
        val hints = DiffDialogHints(WindowWrapper.Mode.FRAME, null, windowHandler)

        DiffManagerEx.getInstance().showMergeBuiltin(project, request, hints)
      }
      catch (e: InvalidDiffRequestException) {
        if (e.cause is FileTooBigException) {
          Messages.showErrorDialog(project, "File is too big to be loaded.", "Can't Show Merge Dialog")
        }
        else {
          LOG.error(e)
          Messages.showErrorDialog(project, e.message, "Can't Show Merge Dialog")
        }
      }
    }

    private fun putActiveWindowKey(frame: JFrame?, file: VirtualFile) {
      if (frame != null && frame.isVisible) {
        file.putUserData(ACTIVE_MERGE_WINDOW, frame)
        frame.addWindowListener(object : WindowAdapter() {
          override fun windowClosed(e: WindowEvent?) {
            file.putUserData(ACTIVE_MERGE_WINDOW, null)
            frame.removeWindowListener(this)
          }
        })
      }
    }

    private fun focusActiveMergeWindow(file: VirtualFile?): Boolean {
      val window = file?.getUserData(ACTIVE_MERGE_WINDOW)
      if (window != null) {
        UIUtil.toFront(window)
        return true
      }
      return false
    }
  }
}