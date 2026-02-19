// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GitRecentCheckoutBranches")

package git4idea.repo

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import git4idea.GitLocalBranch
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.config.GitVcsSettings

/**
 * Collect the first "git.recent.checkout.branches.reflog.entries.count" checkout branch entries from reflog.
 * An initial branch after git clone isn't stored as "checkout" entry in reflog,
 * [GitVcsSettings.getRecentBranchesByRepository] will be used instead.
 */
@RequiresBackgroundThread
internal fun collectRecentCheckoutBranches(project: Project, root: VirtualFile, haveLocalBranch: (GitLocalBranch) -> Boolean): List<GitLocalBranch> {
  val recentBranchFromSettings = GitVcsSettings.getInstance(project).recentBranchesByRepository[root.path]?.let(::GitLocalBranch)
  val reflogEntriesCount = Registry.intValue("git.recent.checkout.branches.reflog.entries.count")
  if (reflogEntriesCount <= 0) return emptyList()

  val handler = GitLineHandler(project, root, GitCommand.REF_LOG)
  handler.setSilent(true)
  handler.addParameters("--max-count", reflogEntriesCount.toString(), "--grep-reflog", "checkout:")
  handler.endOptions()
  handler.isEnableInteractiveCallbacks = false // the method might be called in GitRepository constructor

  val result = Git.getInstance().runCommand(handler)
  if (!result.success()) return emptyList()

  val branches = linkedSetOf<GitLocalBranch>()
  var recentBranchFromSettingsAdded = false
  val toClause = " to "
  for (line in result.output) {
    val toIndex = line.lastIndexOf(toClause)
    if (toIndex <= 0) continue

    val branchName = line.substring(toIndex + toClause.length, line.length)

    val localBranch = GitLocalBranch(branchName)
    if (haveLocalBranch(localBranch)) {
      if (recentBranchFromSettings?.name == branchName) {
        recentBranchFromSettingsAdded = true
      }
      branches.add(localBranch)
    }
  }

  if (recentBranchFromSettings != null && !recentBranchFromSettingsAdded && haveLocalBranch(recentBranchFromSettings)) {
    branches.add(recentBranchFromSettings)
  }

  return branches.toList()
}
