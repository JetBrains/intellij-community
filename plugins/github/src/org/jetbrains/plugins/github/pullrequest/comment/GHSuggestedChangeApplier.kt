// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.comment

import com.intellij.openapi.diff.impl.patch.ApplyPatchStatus
import com.intellij.openapi.diff.impl.patch.PatchHunk
import com.intellij.openapi.diff.impl.patch.PatchLine
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.diff.impl.patch.apply.GenericPatchApplier
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.actions.VcsContextFactory
import com.intellij.vcsUtil.VcsImplUtil
import git4idea.GitUtil
import git4idea.checkin.GitCheckinEnvironment
import git4idea.checkin.GitCommitOptions
import git4idea.index.GitIndexUtil
import git4idea.repo.GitRepository
import git4idea.util.GitFileUtils
import org.jetbrains.plugins.github.util.GithubUtil
import java.nio.charset.Charset

internal class GHSuggestedChangeApplier(
  private val project: Project,
  private val repository: GitRepository,
  private val suggestedChange: GHSuggestedChange
) {
  private val virtualBaseDir = repository.root

  fun applySuggestedChange(): ApplyPatchStatus {
    val suggestedChangePatch = createSuggestedChangePatch(suggestedChange)
    val patchApplier = PatchApplier(project, virtualBaseDir, listOf(suggestedChangePatch), null, null)

    return patchApplier.execute(true, false)
  }

  private fun createSuggestedChangePatchHunk(suggestedChange: GHSuggestedChange): PatchHunk {
    val suggestedChangeContent = suggestedChange.cutSuggestedChangeContent()
    val suggestedChangePatchHunk = PatchHunk(suggestedChange.startLineIndex, suggestedChange.endLineIndex,
                                             suggestedChange.startLineIndex,
                                             suggestedChange.startLineIndex + suggestedChangeContent.size - 1)

    suggestedChange.cutContextContent().forEach { suggestedChangePatchHunk.addLine(PatchLine(PatchLine.Type.CONTEXT, it)) }
    suggestedChange.cutChangedContent().forEach { suggestedChangePatchHunk.addLine(PatchLine(PatchLine.Type.REMOVE, it)) }
    suggestedChangeContent.forEach { suggestedChangePatchHunk.addLine(PatchLine(PatchLine.Type.ADD, it)) }

    return suggestedChangePatchHunk
  }

  fun commitSuggestedChanges(commitMessage: String): ApplyPatchStatus {
    // Apply patch
    val suggestedChangePatch = createSuggestedChangePatch(suggestedChange)
    return commitSuggestedChanges(project, repository, suggestedChangePatch, commitMessage)
  }

  private fun createSuggestedChangePatch(suggestedChange: GHSuggestedChange): TextFilePatch {
    val suggestedChangePatchHunk = createSuggestedChangePatchHunk(suggestedChange)

    return TextFilePatch(Charset.defaultCharset()).apply {
      beforeName = suggestedChange.filePath
      afterName = suggestedChange.filePath
      addHunk(suggestedChangePatchHunk)
    }
  }

  companion object {
    fun applySuggestedChange(project: Project,
                             repository: GitRepository,
                             suggestedChangePatch: TextFilePatch): ApplyPatchStatus {
      val patchApplier = PatchApplier(project, repository.root, listOf(suggestedChangePatch), null, null)
      return patchApplier.execute(true, false)
    }

    fun commitSuggestedChanges(project: Project,
                               repository: GitRepository,
                               suggestedChangePatch: TextFilePatch,
                               commitMessage: String): ApplyPatchStatus {
      // Apply patch
      val patchApplier = PatchApplier(project, repository.root, listOf(suggestedChangePatch), null, null)
      val patchStatus = patchApplier.execute(true, false)
      if (patchStatus == ApplyPatchStatus.ALREADY_APPLIED) {
        return patchStatus
      }

      val factory = VcsContextFactory.getInstance()
      // Create suggested change revision
      val beforeLocalFilePath = factory.createFilePathOn(repository.root, suggestedChangePatch.beforeName)
      val afterLocalFilePath = factory.createFilePathOn(repository.root, suggestedChangePatch.afterName)

      val bytes = GitFileUtils.getFileContent(project, repository.root, GitUtil.HEAD, suggestedChangePatch.beforeName)
      val revisionContent = VcsImplUtil.loadTextFromBytes(project, bytes, beforeLocalFilePath)
      val appliedPatch = GenericPatchApplier.apply(revisionContent, suggestedChangePatch.hunks)
      if (appliedPatch == null || appliedPatch.status != ApplyPatchStatus.SUCCESS) {
        return appliedPatch?.status ?: ApplyPatchStatus.FAILURE
      }

      val virtualFile = beforeLocalFilePath.virtualFile ?: return ApplyPatchStatus.FAILURE
      val fileContent = GitCheckinEnvironment.convertDocumentContentToBytesWithBOM(repository, appliedPatch.patchedText, virtualFile)
      val stagedFile = GitIndexUtil.listStaged(repository, beforeLocalFilePath) ?: return ApplyPatchStatus.FAILURE
      GitIndexUtil.write(repository, beforeLocalFilePath, fileContent, stagedFile.isExecutable)

      // Commit suggested change
      val suggestedChangedPath = GitCheckinEnvironment.ChangedPath(beforeLocalFilePath, afterLocalFilePath)
      val commitMessageFile = GitCheckinEnvironment.createCommitMessageFile(project, repository.root, commitMessage)
      val exceptions = GitCheckinEnvironment.commitUsingIndex(project, repository,
                                                              listOf(suggestedChangedPath), setOf(suggestedChangedPath),
                                                              commitMessageFile, GitCommitOptions())

      if (exceptions.isNotEmpty()) {
        val messages = exceptions.flatMap { it.messages.toList() }.toTypedArray()
        GithubUtil.LOG.error("Failed to commit suggested change", *messages)
        return ApplyPatchStatus.FAILURE
      }

      return ApplyPatchStatus.SUCCESS
    }
  }
}