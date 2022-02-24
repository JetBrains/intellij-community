// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.comment

import com.intellij.openapi.diff.impl.patch.ApplyPatchStatus
import com.intellij.openapi.diff.impl.patch.PatchHunk
import com.intellij.openapi.diff.impl.patch.PatchLine
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.diff.impl.patch.apply.GenericPatchApplier
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.LocalFilePath
import git4idea.GitContentRevision
import git4idea.GitRevisionNumber
import git4idea.checkin.GitCheckinEnvironment
import git4idea.checkin.GitCommitOptions
import git4idea.index.GitIndexUtil
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService
import java.nio.charset.Charset
import java.nio.file.Path

class GHSuggestedChangeApplier(
  private val project: Project,
  private val suggestedChange: String,
  private val suggestedChangeInfo: GHSuggestedChangeInfo,
  repositoryDataService: GHPRRepositoryDataService
) {
  private val repository = repositoryDataService.remoteCoordinates.repository
  private val virtualBaseDir = repository.root

  fun applySuggestedChange(): ApplyPatchStatus {
    val suggestedChangePatch = createSuggestedChangePatch(suggestedChange, suggestedChangeInfo)
    val patchApplier = PatchApplier(project, virtualBaseDir, listOf(suggestedChangePatch), null, null)

    return patchApplier.execute(true, false)
  }

  private fun createSuggestedChangePatchHunk(suggestedChangeContent: List<String>, suggestedChangeInfo: GHSuggestedChangeInfo): PatchHunk {
    val suggestedChangePatchHunk = PatchHunk(suggestedChangeInfo.startLine, suggestedChangeInfo.endLine,
                                             suggestedChangeInfo.startLine, suggestedChangeInfo.startLine + suggestedChangeContent.size - 1)

    val changedLines = suggestedChangeInfo.cutChangedContent()
    changedLines.forEach { suggestedChangePatchHunk.addLine(PatchLine(PatchLine.Type.REMOVE, it)) }
    suggestedChangeContent.forEach { suggestedChangePatchHunk.addLine(PatchLine(PatchLine.Type.ADD, it)) }

    return suggestedChangePatchHunk
  }

  fun commitSuggestedChanges(commitMessage: String): ApplyPatchStatus {
    // Apply patch
    val suggestedChangePatch = createSuggestedChangePatch(suggestedChange, suggestedChangeInfo)
    val patchApplier = PatchApplier(project, virtualBaseDir, listOf(suggestedChangePatch), null, null)
    val patchStatus = patchApplier.execute(true, false)
    if (patchStatus == ApplyPatchStatus.ALREADY_APPLIED) {
      return patchStatus
    }

    // Create suggested change revision
    val beforeLocalFilePath = createLocalFilePath(suggestedChangePatch.beforeName)
    val afterLocalFilePath = createLocalFilePath(suggestedChangePatch.afterName)

    val beforeRevision = GitContentRevision.createRevision(beforeLocalFilePath, GitRevisionNumber.HEAD, project)
    val appliedPatch = GenericPatchApplier.apply(beforeRevision.content, suggestedChangePatch.hunks)
    if (appliedPatch == null || appliedPatch.status != ApplyPatchStatus.SUCCESS) {
      return appliedPatch?.status ?: ApplyPatchStatus.FAILURE
    }

    val virtualFile = beforeLocalFilePath.virtualFile ?: return ApplyPatchStatus.FAILURE
    val fileContent = GitCheckinEnvironment.convertDocumentContentToBytesWithBOM(repository, appliedPatch.patchedText, virtualFile)
    val stagedFile = GitIndexUtil.listStaged(repository, beforeLocalFilePath) ?: return ApplyPatchStatus.FAILURE
    GitIndexUtil.write(repository, beforeLocalFilePath, fileContent, stagedFile.isExecutable)

    // Commit suggested change
    val suggestedChangedPath = GitCheckinEnvironment.ChangedPath(beforeLocalFilePath, afterLocalFilePath)
    val commitMessageFile = GitCheckinEnvironment.createCommitMessageFile(project, virtualBaseDir, commitMessage)
    GitCheckinEnvironment.commitUsingIndex(project, repository,
                                           listOf(suggestedChangedPath), setOf(suggestedChangedPath),
                                           commitMessageFile, GitCommitOptions())

    return ApplyPatchStatus.SUCCESS
  }

  private fun createSuggestedChangePatch(suggestedChange: String, suggestedChangeInfo: GHSuggestedChangeInfo): TextFilePatch {
    val suggestedChangeContent = getSuggestedChangeContent(suggestedChange)
    val suggestedChangePatchHunk = createSuggestedChangePatchHunk(suggestedChangeContent, suggestedChangeInfo)

    return TextFilePatch(Charset.defaultCharset()).apply {
      beforeName = suggestedChangeInfo.filePath
      afterName = suggestedChangeInfo.filePath
      addHunk(suggestedChangePatchHunk)
    }
  }

  private fun createLocalFilePath(filename: String): LocalFilePath = LocalFilePath(Path.of(virtualBaseDir.path, filename), false)

  private fun getSuggestedChangeContent(comment: String): List<String> {
    return comment.lines()
      .dropWhile { !it.startsWith("```suggestion") }
      .drop(1)
      .takeWhile { !it.startsWith("```") }
  }
}