// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.performanceTesting

import com.intellij.ide.DataManager
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.vcs.actions.VcsContextUtil
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.vcs.log.VcsLogDataPack
import com.intellij.vcs.log.VcsLogFileHistoryProvider
import com.intellij.vcs.log.VcsLogListener
import com.intellij.vcs.log.impl.VcsLogNavigationUtil.waitForRefresh
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.VcsLogUiHolder
import com.intellij.vcs.log.visible.VisiblePack
import com.jetbrains.performancePlugin.PerformanceTestSpan
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume


class ShowFileHistoryCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  override suspend fun doExecute(context: PlaybackContext) {
    val logManager = VcsProjectLog.getInstance(context.project).logManager ?: throw RuntimeException("VcsLogManager instance is null")
    withContext(Dispatchers.EDT) {
      if (!logManager.isLogUpToDate) logManager.waitForRefresh()

      val focusedComponent = IdeFocusManager.findInstance().focusOwner
      val dataContext = DataManager.getInstance().getDataContext(focusedComponent)
      val selectedFiles = writeIntentReadAction { VcsContextUtil.selectedFilePaths (dataContext) }
      LOG.info("is active window ${ProjectUtil.getActiveProject()}")
      LOG.info("Selected file paths ${selectedFiles.size}")

      val historyProvider: VcsLogFileHistoryProvider = context.project.getService(VcsLogFileHistoryProvider::class.java)
      if (!historyProvider.canShowFileHistory(selectedFiles, null)) {
        throw RuntimeException("Can't show file history for $selectedFiles")
      }

      val mainSpan = PerformanceTestSpan.TRACER.spanBuilder(MAIN_SPAN_NAME).startSpan()
      LOG.info("$MAIN_SPAN_NAME launched")
      val scope = mainSpan.makeCurrent()
      val firstPackSpan = PerformanceTestSpan.TRACER.spanBuilder(FIRST_PACK_SPAN_NAME).startSpan()
      historyProvider.showFileHistory(selectedFiles, null)

      val contentManager = ToolWindowManager.getInstance(context.project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID)?.contentManager
      val ui = VcsLogUiHolder.getLogUis(contentManager?.selectedContent?.component!!).single()
      LOG.info("suspendCancellableCoroutine scheduled")
      suspendCancellableCoroutine { continuation ->
        LOG.info("suspendCancellableCoroutine launched")
        val listener = object : VcsLogListener {
          override fun onChange(dataPack: VcsLogDataPack, refreshHappened: Boolean) {
            if (!(dataPack as VisiblePack).canRequestMore()) {
              mainSpan.end()
              scope.close()
              ui.removeLogListener(this)
              continuation.resume(Result.success(Unit))
            }
            else if (firstPackSpan.isRecording) {
              firstPackSpan.end()
            }
          }
        }
        ui.addLogListener(listener)
        continuation.invokeOnCancellation { ui.removeLogListener(listener) }
      }
    }
  }

  override fun getName(): String = COMMAND_NAME

  companion object {
    const val MAIN_SPAN_NAME = "showFileHistory"
    const val FIRST_PACK_SPAN_NAME = "showFirstPack"
    const val COMMAND_NAME = "showFileHistory"
    const val PREFIX = "${CMD_PREFIX}$COMMAND_NAME"
    private val LOG = logger<ShowFileHistoryCommand>()
  }
}