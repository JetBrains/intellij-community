// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.performanceTesting

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.impl.VcsLogNavigationUtil.waitForRefresh
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.visible.CommitCountStage
import com.intellij.vcs.log.visible.VcsLogFiltererImpl
import com.intellij.vcs.log.visible.VisiblePack
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * This command will filter vcs log tab data by set of filters on the root directory
 * %filterVcsLogTab <user_name>
 * Example - %filterVcsLogTab intellij,Alexander Kass
 */
class FilterVcsLogTabCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {

  companion object {
    const val NAME = "filterVcsLogTab"
    const val PREFIX = CMD_PREFIX + NAME
  }

  override fun getName(): String = NAME

  override suspend fun doExecute(context: PlaybackContext) {
    val logManager = VcsProjectLog.getInstance(context.project).logManager ?: throw RuntimeException("VcsLogManager instance is null")
    withContext(Dispatchers.EDT) {
      if (!logManager.isLogUpToDate) logManager.waitForRefresh()
    }

    withContext(Dispatchers.IO) {
      val dataManager = logManager.dataManager
      val userName = extractCommandArgument(PREFIX)
      val usersFilter = VcsLogFilterObject.fromUserNames(listOf(userName), dataManager)
      val filterCollection = VcsLogFilterObject.collection(usersFilter)
      val (dataPack, _) = VcsLogFiltererImpl(dataManager)
        .filter(dataManager.dataPack, VisiblePack.EMPTY, PermanentGraph.SortType.Normal, filterCollection, CommitCountStage.ALL)
      logger<FilterVcsLogTabCommand>().info("VisibleCommitCount size ${dataPack.visibleGraph.visibleCommitCount}")
      //TODO Report filter result 'dataPack.first.visibleGraph.visibleCommitCount' to CSV in meter style
    }
  }
}