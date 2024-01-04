// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.performanceTesting

import com.intellij.ide.DataManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.vcs.actions.VcsContextUtil
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.VcsLogFileHistoryProvider
import com.intellij.vcs.log.ui.VcsLogPanel
import com.intellij.vcs.log.ui.VcsLogUiEx
import com.intellij.vcs.log.visible.VisiblePack
import com.jetbrains.performancePlugin.PerformanceTestSpan
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise


class ShowFileHistoryCommand(text: String, line: Int) : AbstractCommand(text, line, true) {
  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val actionCallback: ActionCallback = ActionCallbackProfilerStopper()
    val focusedComponent = IdeFocusManager.findInstance().focusOwner
    val dataContext = DataManager.getInstance().getDataContext(focusedComponent)
    val selectedFiles = VcsContextUtil.selectedFilePaths(dataContext)
    val historyProvider: VcsLogFileHistoryProvider = context.project.getService(VcsLogFileHistoryProvider::class.java)
    if (!historyProvider.canShowFileHistory(selectedFiles, null)) {
      actionCallback.reject("Can't show file history for $selectedFiles")
    }

    val mainSpan = PerformanceTestSpan.TRACER.spanBuilder(MAIN_SPAN_NAME).startSpan()
    val scope = mainSpan.makeCurrent()
    val firstPackSpan = PerformanceTestSpan.TRACER.spanBuilder(FIRST_PACK_SPAN_NAME).startSpan()
    historyProvider.showFileHistory(selectedFiles, null)

    val contentManager = ToolWindowManager.getInstance(context.project).getToolWindow(
      ChangesViewContentManager.TOOLWINDOW_ID)?.contentManager
    val ui = VcsLogUiHolder.getLogUis(contentManager?.selectedContent?.component!!).single()
    ui.addLogListener { dataPack, _ ->
      run {
        if (!(dataPack as VisiblePack).canRequestMore()) {
          mainSpan.end()
          scope.close()
          actionCallback.setDone()
        }
        else if (firstPackSpan.isRecording) {
            firstPackSpan.end()
          }
      }
    }

    return actionCallback.toPromise()
  }

  companion object {
    const val MAIN_SPAN_NAME = "showFileHistory"
    const val FIRST_PACK_SPAN_NAME = "showFirstPack"
    const val PREFIX = "${CMD_PREFIX}showFileHistory"
  }

}