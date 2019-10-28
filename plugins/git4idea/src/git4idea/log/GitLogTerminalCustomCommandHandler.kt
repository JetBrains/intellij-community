// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.log

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vcs.actions.VcsContextFactory
import com.intellij.terminal.TerminalShellCommandHandler
import com.intellij.util.execution.ParametersListUtil
import com.intellij.vcs.log.VcsLogBranchFilter
import com.intellij.vcs.log.VcsLogFilter
import com.intellij.vcs.log.VcsLogUserFilter
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitBranch
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

class GitLogTerminalCustomCommandHandler : TerminalShellCommandHandler {
  private val LOG = Logger.getInstance(GitLogTerminalCustomCommandHandler::class.java)
  private val AUTHOR_PARAMETER = "--author="
  private val BRANCHES_PARAMETER = "--branches="

  override fun matches(project: Project, workingDirectory: String?, localSession: Boolean, command: String): Boolean =
    parse(project, workingDirectory, command) != null

  override fun execute(project: Project, workingDirectory: String?, localSession: Boolean, command: String): Boolean {
    if (workingDirectory == null) {
      LOG.warn("Cannot open git log for unknown root.")
      return false
    }

    val repository = GitRepositoryManager.getInstance(project).getRepositoryForFile(VcsUtil.getFilePath(workingDirectory))
    if (repository == null) {
      LOG.warn("Cannot find repository for working directory: $workingDirectory")
      return false
    }

    val parameters = parse(project, workingDirectory, command)
    val branch = parameters?.branch
    val branchesPatterns = parameters?.branchesPatterns
    val users = parameters?.users
    val projectLog = VcsProjectLog.getInstance(project)

    projectLog.openLogTab(VcsLogFilterObject.collection(
      getRootFilter(workingDirectory, project),
      getBranchPatternsFilter(branchesPatterns, repository),
      getBranchFilter(branch),
      getUsersFilter(users, projectLog)))

    return true
  }

  private fun getBranchFilter(branch: String?): VcsLogBranchFilter? {
    if (branch == null) return null
    return VcsLogFilterObject.fromBranch(branch)
  }

  private fun getBranchPatternsFilter(branchesPatterns: String?, repository: GitRepository): VcsLogBranchFilter? {
    if (branchesPatterns == null) return null

    val allBranches = sequenceOf<GitBranch>()
      .plus(repository.branches.localBranches)
      .plus(repository.branches.remoteBranches)
      .mapTo(mutableSetOf()) { it.name }

    return VcsLogFilterObject.fromBranchPatterns(listOf(branchesPatterns), allBranches)
  }

  private fun getUsersFilter(users: List<String>?, projectLog: VcsProjectLog): VcsLogUserFilter? {
    if (users.isNullOrEmpty()) return null
    return VcsLogFilterObject.fromUserNames(users, projectLog.dataManager!!)
  }

  private fun getRootFilter(workingDirectory: String, project: Project): VcsLogFilter? {
    val path = VcsContextFactory.SERVICE.getInstance().createFilePath(workingDirectory, true)
    val rootObject: VcsRoot = ProjectLevelVcsManager.getInstance(project).getVcsRootObjectFor(path) ?: return null
    return VcsLogFilterObject.fromRoot(rootObject.path)
  }

  private fun parse(project: Project, workingDirectory: String?, command: String): Parameters? {
    if (!command.startsWith("git log")) {
      return null
    }

    if (workingDirectory == null
        || GitRepositoryManager.getInstance(project).getRepositoryForFile(VcsUtil.getFilePath(workingDirectory)) == null) {
      return null
    }

    val commands = ParametersListUtil.parse(command)
    assert(commands.size >= 2)
    commands.removeAt(0)
    commands.removeAt(0)

    val authorParameters = commands.filter { it.startsWith(AUTHOR_PARAMETER) }
    val users = authorParameters.mapNotNull { it.substringAfter(AUTHOR_PARAMETER) }
    if (users.isNotEmpty()) {
      commands.removeAll(authorParameters)
    }

    val branchesParameter = commands.find { it.startsWith(BRANCHES_PARAMETER) }
    var branchesPatterns: String? = null
    if (branchesParameter != null) {
      branchesPatterns = branchesParameter.substringAfter(BRANCHES_PARAMETER)
      commands.remove(branchesParameter)
    }

    var branch: String? = null
    if (commands.isNotEmpty()) {
      branch = commands[0]
      commands.remove(branch)
    }

    if (commands.isEmpty()) {
      return Parameters(branch, branchesPatterns, users)
    }

    return null
  }

  companion object {
    data class Parameters(val branch: String? = null, val branchesPatterns: String? = null, val users: List<String>)
  }
}