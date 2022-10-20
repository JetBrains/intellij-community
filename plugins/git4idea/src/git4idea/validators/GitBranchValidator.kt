// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.validators

import com.intellij.dvcs.DvcsUtil.getShortRepositoryName
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.text.StringUtil
import git4idea.GitUtil.HEAD
import git4idea.i18n.GitBundle
import git4idea.i18n.GitBundle.BUNDLE
import git4idea.repo.GitRepository
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

fun validateName(repositories: Collection<GitRepository>, inputString: String): ValidationInfo? =
  checkRefName(inputString) ?: checkBranchConflict(repositories, inputString)

fun checkRefName(inputString: String?): ValidationInfo? =
  checkRefNameEmptyOrHead(inputString) ?:
  if (!GitRefNameValidator.getInstance().checkInput(inputString))
    ValidationInfo(GitBundle.message("new.branch.dialog.error.branch.name.invalid", inputString))
  else null

fun checkRefNameEmptyOrHead(inputString: String?): ValidationInfo? =
  if (StringUtil.isEmptyOrSpaces(inputString))
    ValidationInfo(GitBundle.message("new.branch.dialog.error.branch.name.empty"))
  else if (StringUtil.equalsIgnoreCase(inputString, HEAD))
    ValidationInfo(GitBundle.message("new.branch.dialog.error.branch.name.head"))
  else null

private fun checkBranchConflict(repositories: Collection<GitRepository>, inputString: String) =
  conflictsWithLocalBranch(repositories, inputString) ?: conflictsWithRemoteBranch(repositories, inputString)

fun conflictsWithLocalBranch(repositories: Collection<GitRepository>, inputString: String): ValidationInfo? =
  conflictsWithLocalOrRemote(repositories, inputString, true, "new.branch.dialog.error.branch.already.exists")

fun conflictsWithRemoteBranch(repositories: Collection<GitRepository>, inputString: String): ValidationInfo? =
  conflictsWithLocalOrRemote(repositories, inputString, false, "new.branch.dialog.error.branch.clashes.with.remote")

fun conflictsWithLocalBranchDirectory(directories: Set<String>, inputString: String): ValidationInfo? {
  if (directories.contains(inputString)) {
    return ValidationInfo(GitBundle.message("new.branch.dialog.error.branch.clashes.with.directory", inputString))
  }
  return null
}

private fun conflictsWithLocalOrRemote(repositories: Collection<GitRepository>,
                                       inputString: String,
                                       local: Boolean,
                                       @PropertyKey(resourceBundle = BUNDLE) message: String): ValidationInfo? {
  val reposWithConflictingBranch = getReposWithConflictingBranch(repositories, inputString, local)
  if (reposWithConflictingBranch.isEmpty()) return null
  var errorText = GitBundle.message(message, inputString)
  if (repositories.size > reposWithConflictingBranch.size) {
    errorText += getAdditionalDescription(reposWithConflictingBranch)
    if (local) return ValidationInfo(errorText).asWarning().withOKEnabled()
  }
  return ValidationInfo(errorText)
}

@Nls
private fun getAdditionalDescription(repositories: List<GitRepository>) =
  " " +
  if (repositories.size > 1) GitBundle.message("common.suffix.in.several.repositories", repositories.size)
  else GitBundle.message("common.suffix.in.one.repository", getShortRepositoryName(repositories.first()))

private fun getReposWithConflictingBranch(repositories: Collection<GitRepository>,
                                          inputString: String,
                                          local: Boolean): List<GitRepository> {
  return repositories.filter { repository -> findConflictingBranch(inputString, repository, local) != null }
}

private fun findConflictingBranch(inputString: String, repository: GitRepository, local: Boolean) =
  repository.branches.run { if (local) findLocalBranch(inputString) else findRemoteBranch(inputString) }
