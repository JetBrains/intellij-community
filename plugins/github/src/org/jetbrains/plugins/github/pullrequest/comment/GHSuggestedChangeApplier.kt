// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.comment

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diff.impl.patch.ApplyPatchStatus
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.diff.impl.patch.apply.GenericPatchApplier
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.actions.VcsContextFactory
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.vcsUtil.VcsImplUtil
import git4idea.GitUtil
import git4idea.checkin.GitCheckinEnvironment
import git4idea.checkin.GitCommitOptions
import git4idea.index.GitIndexUtil
import git4idea.repo.GitRepository
import git4idea.repo.isSubmodule
import git4idea.util.GitFileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.github.util.GithubUtil
import java.io.File

internal object GHSuggestedChangeApplier {
  suspend fun applySuggestedChange(project: Project,
                                   repository: GitRepository,
                                   suggestedChangePatch: TextFilePatch): ApplyPatchStatus =
    withContext(Dispatchers.Default) {
      val patchApplier = PatchApplier(project, repository.root, listOf(suggestedChangePatch), null, null)
      patchApplier.execute(true, false)
    }

  suspend fun commitSuggestedChanges(project: Project,
                                     repository: GitRepository,
                                     suggestedChangePatch: TextFilePatch,
                                     commitMessage: String): ApplyPatchStatus =
    withContext(Dispatchers.Default) {
      doCommitSuggestedChanges(project, repository, suggestedChangePatch, commitMessage)
    }


  private suspend fun doCommitSuggestedChanges(project: Project,
                                               repository: GitRepository,
                                               suggestedChangePatch: TextFilePatch,
                                               commitMessage: String): ApplyPatchStatus {
    // Apply patch
    val patchApplier = PatchApplier(project, repository.root, listOf(suggestedChangePatch), null, null)
    val patchStatus = patchApplier.execute(false, false)
    if (patchStatus == ApplyPatchStatus.ALREADY_APPLIED) {
      return patchStatus
    }

    val factory = serviceAsync<VcsContextFactory>()
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

    val exceptions = mutableListOf<VcsException>()
    GitCheckinEnvironment.runWithMessageFile(project, repository.root, commitMessage) { messageFile: File ->
      GitCheckinEnvironment.commitUsingIndex(project, repository,
                                             listOf(suggestedChangedPath), setOf(suggestedChangedPath),
                                             messageFile, GitCommitOptions()).also {
        exceptions.addAll(it)
      }
    }

    if (exceptions.isNotEmpty()) {
      val messages = exceptions.flatMap { it.messages.toList() }.toTypedArray()
      GithubUtil.LOG.error("Failed to commit suggested change", *messages)
      return ApplyPatchStatus.FAILURE
    }

    repository.update()
    if (repository.isSubmodule()) {
      VcsDirtyScopeManager.getInstance(project).dirDirtyRecursively(repository.root.parent)
    }

    return ApplyPatchStatus.SUCCESS
  }
}