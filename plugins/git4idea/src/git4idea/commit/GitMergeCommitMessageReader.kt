// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit

import com.intellij.dvcs.DvcsUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import git4idea.GitUtil
import git4idea.config.GitConfigUtil
import git4idea.repo.GitRepository
import java.io.IOException

@Service(Service.Level.PROJECT)
class GitMergeCommitMessageReader(private val project: Project) {
  @RequiresBackgroundThread
  fun read(repository: GitRepository): String? {
    val mergeMsgFile = repository.repositoryFiles.mergeMessageFile
    if (!mergeMsgFile.exists()) return null

    if (LOG.isDebugEnabled) {
      LOG.debug("Merge message file $mergeMsgFile exists")
    }

    return try {
      val encoding = GitConfigUtil.getCommitEncodingCharset(project, repository.root)
      val mergeMsg = FileUtil.loadFile(mergeMsgFile, encoding)
      val squashMsgFile = repository.repositoryFiles.squashMessageFile
      val squashMsg = if (squashMsgFile.exists()) {
          LOG.debug("Squash message file $squashMsgFile exists")
         FileUtil.loadFile(squashMsgFile, encoding)
      } else null

      return fixCommentCharsIfNeeded(DvcsUtil.joinMessagesOrNull(listOfNotNull(squashMsg, mergeMsg)), repository)
    }
    catch (e: IOException) {
      LOG.warn("Unable to load merge message", e)
      null
    }
  }

  /**
   * commentChar is redefined in interactive rebase
   *
   * @see [git4idea.commands.GitImpl.REBASE_CONFIG_PARAMS]
   */
  private fun fixCommentCharsIfNeeded(message: String?, repository: GitRepository): String? {
    if (message == null) return null

    if (message.lines().none { it.startsWith(GitUtil.COMMENT_CHAR) }) return message
    val replaceWith = GitConfigUtil.getValue(project, repository.root, GitConfigUtil.CORE_COMMENT_CHAR) ?: "#"
    return message.replace(COMMENT_CHAR_REGEX, replaceWith)
  }

  companion object {
    private val LOG = thisLogger()
    private val COMMENT_CHAR_REGEX = Regex("^${GitUtil.COMMENT_CHAR}", RegexOption.MULTILINE)

    fun getInstance(project: Project): GitMergeCommitMessageReader = project.service<GitMergeCommitMessageReader>()
  }
}