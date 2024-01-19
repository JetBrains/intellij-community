// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.performanceTesting

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.VcsLogDetailsFilter
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.data.VcsLogData
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
import java.io.FileNotFoundException
import kotlin.io.path.absolute
import kotlin.io.path.exists

/**
 * This command will filter vcs log tab data by set of filters on the root directory
 * %filterVcsLogTab -name <user_name> -path<slash/divided/path>
 * Example - '%filterVcsLogTab -name Alexander Kass -path srs/SomeImpl.java'
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
      val vcsLogData = logManager.dataManager
      val (dataPack, commitStage) = VcsLogFiltererImpl(vcsLogData)
        .filter(vcsLogData.dataPack, VisiblePack.EMPTY, PermanentGraph.SortType.Normal,
                generateVcsFilter(context.project.guessProjectDir(), extractCommandArgument(PREFIX), vcsLogData),
                CommitCountStage.ALL)

      logger<FilterVcsLogTabCommand>().info("VisibleCommitCount size ${dataPack.visibleGraph.visibleCommitCount}")
      logger<FilterVcsLogTabCommand>().info("Commit stage $commitStage")
      //TODO Report filter result 'dataPack.first.visibleGraph.visibleCommitCount' to CSV in meter style
    }
  }

  private fun generateVcsFilter(projectFile: VirtualFile?, rawParams: String, vcsLogData: VcsLogData): VcsLogFilterCollection {
    val regex = "-(\\w+)\\s+([\\S]+)".toRegex()

    val matches = regex.findAll(rawParams)
    val result = mutableListOf<VcsLogDetailsFilter>()

    for (match in matches) {
      val (key, value) = match.destructured

      when (key) {
        "name" -> result.add(VcsLogFilterObject.fromUserNames(listOf(value), vcsLogData))
        "path" -> {
          if (projectFile != null) {
            val fileToFilter = projectFile.toNioPath().absolute().resolve(value)
            if (fileToFilter.exists()) {
              result.add(VcsLogFilterObject.fromPaths(listOf(LocalFilePath(fileToFilter, false))))
            }
            else {
              throw FileNotFoundException(value)
            }
          }
        }
      }
    }

    return VcsLogFilterObject.collection(*result.toTypedArray())
  }
}