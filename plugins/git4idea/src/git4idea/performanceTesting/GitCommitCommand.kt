package git4idea.performanceTesting

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.vcs.commit.ChangeListCommitState
import com.intellij.vcs.commit.LocalChangesCommitter
import com.intellij.vcs.log.data.DataPackChangeListener
import com.intellij.vcs.log.data.VcsLogGraphData
import com.intellij.vcs.log.impl.VcsProjectLog
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import git4idea.GitContentRevision
import git4idea.GitRevisionNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Command for committing file changes to git
 * Example - %gitCommit src/TheBestJavaClass.java, The Best Commit Ever
 */
class GitCommitCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {

  companion object {
    const val NAME = "gitCommit"
    const val PREFIX = CMD_PREFIX + NAME
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val (filePath, commitMessage) = extractCommandList(PREFIX, ",") ?: throw RuntimeException("Commit message wasn't passed")
    val changeList = ChangeListManager.getInstance(context.project).defaultChangeList

    val projectDir = context.project.guessProjectDir() ?: throw RuntimeException("Project dir is null")
    val virtualFile = (projectDir.findFileByRelativePath(filePath) ?: throw RuntimeException(
      "Specified file $filePath wasn't found")).toNioPath()

    val beforeRevision = GitContentRevision.createRevision(
      LocalFilePath(virtualFile, false),
      GitRevisionNumber.HEAD,
      context.project
    )

    val change = Change(beforeRevision, beforeRevision, FileStatus.MODIFIED)
    val listCommitState = ChangeListCommitState(changeList, listOf(change), commitMessage)

    runWithLogRefresh(context.project) {
      LocalChangesCommitter(context.project, listCommitState, CommitContext()).runCommit("", true)
    }
  }

  private suspend fun runWithLogRefresh(project: Project, runnable: () -> Unit) {
    withContext(Dispatchers.EDT) {
      val logManager = VcsProjectLog.getInstance(project).logManager ?: throw RuntimeException("VcsLogManager instance is null")
      suspendCancellableCoroutine { continuation ->
        val dataPackListener = object : DataPackChangeListener {
          override fun onDataPackChange(newDataPack: VcsLogGraphData) {
            if (newDataPack is VcsLogGraphData.OverlayData) return
            if (logManager.isLogUpToDate) {
              logManager.dataManager.removeDataPackChangeListener(this)
              continuation.resumeWith(Result.success(Unit))
            }
          }
        }
        logManager.dataManager.addDataPackChangeListener(dataPackListener)
        continuation.invokeOnCancellation { logManager.dataManager.removeDataPackChangeListener(dataPackListener) }

        runnable()

        logManager.scheduleUpdate()
      }
    }
  }

  override fun getName(): String = NAME

}