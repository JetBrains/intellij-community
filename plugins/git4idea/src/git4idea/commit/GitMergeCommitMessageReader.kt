// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit

import com.intellij.dvcs.DvcsUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
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
      DvcsUtil.joinMessagesOrNull(listOfNotNull(squashMsg, mergeMsg))
    }
    catch (e: IOException) {
      LOG.warn("Unable to load merge message", e)
      null
    }
  }

  companion object {
    fun getInstance(project: Project) = project.service<GitMergeCommitMessageReader>()

    private val LOG = thisLogger()
  }
}