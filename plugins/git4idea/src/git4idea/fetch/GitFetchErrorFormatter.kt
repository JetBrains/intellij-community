// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.fetch

import com.intellij.openapi.ui.getPresentablePath
import git4idea.branch.GitBranchUtil
import git4idea.i18n.GitBundle
import org.jetbrains.annotations.Nls
import java.util.regex.Pattern

object GitFetchErrorFormatter {
  private val BRANCH_CHECKED_OUT_PATTERN = Pattern.compile("refusing to fetch into branch '(.*)' checked out at '(.*)'")

  fun isCheckedOutBranchError(errorMessage: @Nls String): Boolean {
    return BRANCH_CHECKED_OUT_PATTERN.matcher(errorMessage).matches()
  }

  fun format(errorMessage: @Nls String): @Nls String {
    return tryFormatCheckedOutBranchError(errorMessage) ?: errorMessage
  }

  private fun tryFormatCheckedOutBranchError(errorMessage: @Nls String): @Nls String? {
    val matcher = BRANCH_CHECKED_OUT_PATTERN.matcher(errorMessage)
    if (!matcher.matches()) return null

    val branchRef = matcher.group(1)
    val path = matcher.group(2)

    val shortBranchName = GitBranchUtil.stripRefsPrefix(branchRef)
    val shortPath = getPresentablePath(path)

    return GitBundle.message("branches.update.error.branch.checked.out", shortBranchName, shortPath)
  }
}