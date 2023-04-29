// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.Compressor
import com.intellij.util.io.ZipUtil
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.index.VcsLogPersistentIndex
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.util.PersistentUtil
import git4idea.i18n.GitBundle
import git4idea.index.GitIndexUtil
import java.io.IOException
import java.nio.file.Path

internal object GitLogIndexDataUtils {
  private val LOG = logger<GitIndexUtil>()

  internal fun extractLogDataFromArchive(project: Project, virtualFile: VirtualFile) {
    val logId = PersistentUtil.calcLogId(project, VcsProjectLog.getLogProviders(project))
    val logCache = PersistentUtil.LOG_CACHE

    val logIndexDirName = PersistentUtil.getProjectLogDataDirectoryName(project.name, logId)
    val currentLogDataPath = logCache.resolve(logIndexDirName)
    val tempLogDataPath = logCache.resolve(logIndexDirName + "_temp")
    val logDataBackupPath = logCache.resolve(logIndexDirName + "_backup")

    object : Task.Backgroundable(project, GitBundle.message("vcs.log.status.bar.extracting.log.index.data")) {
      override fun run(indicator: ProgressIndicator) {
        try {
          FileUtil.delete(tempLogDataPath)
          ZipUtil.extract(virtualFile.toNioPath(), tempLogDataPath, null, true)
          // TODO: add versions validation
        }
        catch (e: IOException) {
          LOG.error("Unable to extract log index data from " + virtualFile.name, e)
        }
      }

      override fun onSuccess() {
        val vcsProjectLog = VcsProjectLog.getInstance(project)
        val data = vcsProjectLog.dataManager
        val isDataPackFull = data?.dataPack?.isFull ?: false
        if (isDataPackFull && indexingFinished(data)) {
          LOG.info("Shared log index data wasn't applied because local indexing completed faster")
          return
        }

        vcsProjectLog.runOnDisposedLog {
          FileUtil.rename(currentLogDataPath.toFile(), logDataBackupPath.fileName.toString())
          FileUtil.rename(tempLogDataPath.toFile(), currentLogDataPath.fileName.toString())
          FileUtil.delete(logDataBackupPath)
        }
      }
    }.queue()
  }

  internal fun createArchiveWithLogData(project: Project, outputArchiveDir: Path) {
    VcsProjectLog.getInstance(project).runOnDisposedLog {
      val runnable = Runnable {
        val logId = PersistentUtil.calcLogId(project, VcsProjectLog.getLogProviders(project))
        val logCache = PersistentUtil.LOG_CACHE
        val logIndexDirName = PersistentUtil.getProjectLogDataDirectoryName(project.name, logId)
        val archive = outputArchiveDir.resolve("$logIndexDirName.zip")

        Compressor.Zip(archive).use { zip ->
          zip.addDirectory(logCache.resolve(logIndexDirName))
        }
      }

      ProgressManager.getInstance().runProcessWithProgressSynchronously(runnable, GitBundle.message("vcs.log.archiving.log.index.data"),
                                                                        false, project)
    }
  }

  internal fun indexingFinished(logData: VcsLogData?): Boolean {
    logData ?: return false
    val rootsForIndexing = VcsLogPersistentIndex.getRootsForIndexing(logData.logProviders)
    val index = logData.index

    return rootsForIndexing.any { root -> index.isIndexed(root) }
  }
}