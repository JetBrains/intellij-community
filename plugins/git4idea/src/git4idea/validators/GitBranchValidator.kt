// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.validators

import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.text.StringUtil
import git4idea.GitUtil.HEAD
import git4idea.repo.GitRepository

fun validateName(repositories: Collection<GitRepository>, inputString: String): ValidationInfo? =
  checkRefName(inputString) ?: checkBranchConflict(repositories, inputString)

fun checkRefName(inputString: String?): ValidationInfo? =
  if (!GitRefNameValidator.getInstance().checkInput(inputString) || StringUtil.equalsIgnoreCase(inputString, HEAD))
    ValidationInfo("Branch name $inputString is not valid")
  else null

private fun checkBranchConflict(repositories: Collection<GitRepository>, inputString: String) =
  conflictsWithLocalBranch(repositories, inputString) ?: conflictsWithRemoteBranch(repositories, inputString)

fun conflictsWithLocalBranch(repositories: Collection<GitRepository>, inputString: String): ValidationInfo? =
  conflictsWithLocalOrRemote(repositories, inputString, true, " already exists")

fun conflictsWithRemoteBranch(repositories: Collection<GitRepository>, inputString: String): ValidationInfo? =
  conflictsWithLocalOrRemote(repositories, inputString, false, " clashes with remote branch with the same name")

private fun conflictsWithLocalOrRemote(repositories: Collection<GitRepository>,
                                       inputString: String,
                                       local: Boolean,
                                       message: String): ValidationInfo? {
  var conflictsWithCurrentName = 0
  for (repository in repositories) {
    if (inputString == repository.currentBranchName) {
      conflictsWithCurrentName++
    }
    else if (findConflictingBranch(inputString, repository, local) != null) {
      var errorText = "Branch name $inputString$message"
      if (repositories.size > 1 && !allReposHaveBranch(repositories, inputString, local)) {
        errorText += " in repository ${repository.presentableUrl}"
        if (local) return ValidationInfo(errorText).asWarning().withOKEnabled()
      }
      return ValidationInfo(errorText)
    }
  }
  return if (conflictsWithCurrentName == repositories.size) {
    ValidationInfo("You are already on branch $inputString")
  }
  else null
}

internal fun allReposHaveBranch(repositories: Collection<GitRepository>, inputString: String, local: Boolean): Boolean {
  return repositories.all { repository -> findConflictingBranch(inputString, repository, local) != null }
}

private fun findConflictingBranch(inputString: String, repository: GitRepository, local: Boolean) =
  repository.branches.run { if (local) findLocalBranch(inputString) else findRemoteBranch(inputString) }
