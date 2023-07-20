package git4idea.performanceTesting

import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.LocalChangeListImpl
import com.intellij.vcs.commit.ChangeListCommitState
import com.intellij.vcs.commit.LocalChangesCommitter
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import git4idea.GitContentRevision
import git4idea.GitRevisionNumber

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
    val localChangeList = LocalChangeListImpl.createEmptyChangeList(context.project, "")
    val localFilePath = ((context.project.guessProjectDir() ?: throw RuntimeException("Project dir is null")).findFileByRelativePath(filePath)
                   ?: throw RuntimeException("")).toNioPath()

    val beforeRevision = GitContentRevision.createRevision(
      LocalFilePath(localFilePath, false),
      GitRevisionNumber.HEAD,
      context.project,
      null
    )
    val change = Change(beforeRevision, beforeRevision, FileStatus.MODIFIED)
    val listCommitState = ChangeListCommitState(localChangeList, listOf(change), commitMessage)
    LocalChangesCommitter(context.project, listCommitState, CommitContext()).runCommit("", true)
  }

  override fun getName(): String = NAME

}