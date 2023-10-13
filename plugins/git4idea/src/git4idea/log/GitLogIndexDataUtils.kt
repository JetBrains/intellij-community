// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.io.Compressor
import com.intellij.util.io.ZipUtil
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.impl.VcsProjectLog.Companion.runOnDisposedLog
import com.intellij.vcs.log.util.PersistentUtil
import git4idea.i18n.GitBundle
import git4idea.index.GitIndexUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Path

internal object GitLogIndexDataUtils {
  private val LOG = logger<GitIndexUtil>()

  internal fun extractLogDataFromArchive(project: Project, zipFile: VirtualFile) {
    val logId = PersistentUtil.calcLogId(project, VcsProjectLog.getLogProviders(project))
    val logCache = PersistentUtil.LOG_CACHE

    val logIndexDirName = PersistentUtil.getProjectLogDataDirectoryName(project.name, logId)

    VcsProjectLog.getInstance(project).childScope().launch {
      val tempLogDataPath = withBackgroundProgress(project, GitBundle.message("vcs.log.status.bar.extracting.log.index.data")) {
        withContext(Dispatchers.IO) {
          try {
            val tempLogDataPath = logCache.resolve(logIndexDirName + "_temp")
            FileUtil.delete(tempLogDataPath)
            ZipUtil.extract(zipFile.toNioPath(), tempLogDataPath, null, true)
            tempLogDataPath
            // TODO: add versions validation
          }
          catch (e: IOException) {
            LOG.error("Unable to extract log index data from " + zipFile.name, e)
            null
          }
        }
      }
      if (tempLogDataPath == null) return@launch

      val vcsProjectLog = VcsProjectLog.getInstance(project)
      val canApplySharedIndexData = withContext(Dispatchers.EDT) {
        val data = vcsProjectLog.dataManager
        val isDataPackFull = data?.dataPack?.isFull ?: false
        !isDataPackFull || !indexingFinished(data)
      }
      if (!canApplySharedIndexData) {
        LOG.info("Shared log index data wasn't applied because local indexing completed faster")
        return@launch
      }

      withBackgroundProgress(project, GitBundle.message("vcs.log.status.bar.replacing.log.index.data")) {
        vcsProjectLog.runOnDisposedLog {
          withContext(Dispatchers.IO) {
            val currentLogDataPath = logCache.resolve(logIndexDirName)
            val logDataBackupPath = logCache.resolve(logIndexDirName + "_backup")
            FileUtil.rename(currentLogDataPath.toFile(), logDataBackupPath.fileName.toString())
            FileUtil.rename(tempLogDataPath.toFile(), currentLogDataPath.fileName.toString())
            FileUtil.delete(logDataBackupPath)
            LOG.info("Applied log index data from " + zipFile.name)
          }
        }
      }
    }
  }

  internal fun createArchiveWithLogData(project: Project, outputArchiveDir: Path) {
    VcsProjectLog.getInstance(project).childScope().launch {
      VcsProjectLog.getInstance(project).runOnDisposedLog {
        withBackgroundProgress(project = project,
                               title = GitBundle.message("vcs.log.archiving.log.index.data"),
                               cancellation = TaskCancellation.nonCancellable()) {
          val logId = PersistentUtil.calcLogId(project, VcsProjectLog.getLogProviders(project))
          val logIndexDirName = PersistentUtil.getProjectLogDataDirectoryName(projectName = project.name, logId = logId)
          withContext(Dispatchers.IO) {
            val logCache = PersistentUtil.LOG_CACHE
            Compressor.Zip(outputArchiveDir.resolve("$logIndexDirName.zip")).use { zip ->
              zip.addDirectory(logCache.resolve(logIndexDirName))
            }
          }
        }
      }
    }
  }

  internal fun indexingFinished(logData: VcsLogData?): Boolean {
    logData ?: return false
    val index = logData.index
    val rootsForIndexing = index.indexingRoots

    return rootsForIndexing.any { root -> index.isIndexed(root) }
  }
}