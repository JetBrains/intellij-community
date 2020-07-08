// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.log

import com.intellij.execution.Executor
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.terminal.TerminalShellCommandHandler
import com.intellij.util.execution.ParametersListUtil
import com.intellij.vcs.log.VcsLogBranchFilter
import com.intellij.vcs.log.VcsLogUserFilter
import com.intellij.vcs.log.VcsUserRegistry
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitBranch
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

class GitLogTerminalCustomCommandHandler : TerminalShellCommandHandler {
  override fun matches(project: Project, workingDirectory: String?, localSession: Boolean, command: String): Boolean =
    parse(project, workingDirectory, command) != null

  override fun execute(project: Project, workingDirectory: String?, localSession: Boolean, command: String, executor: Executor?): Boolean {
    if (workingDirectory == null) {
      LOG.warn("Cannot open git log for unknown root.")
      return false
    }

    val repository = GitRepositoryManager.getInstance(project).getRepositoryForFileQuick(VcsUtil.getFilePath(workingDirectory))
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
      VcsLogFilterObject.fromRoot(repository.root),
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

  private fun parse(project: Project, workingDirectory: String?, command: String): Parameters? {
    if (command != GIT_LOG_COMMAND && !command.startsWith("${GIT_LOG_COMMAND} ")) {
      return null
    }

    if (workingDirectory == null
        || GitRepositoryManager.getInstance(project).getRepositoryForFileQuick(VcsUtil.getFilePath(workingDirectory)) == null) {
      return null
    }

    val commands = ParametersListUtil.parse(command)
    assert(commands.size >= 2)
    commands.removeAt(0)
    commands.removeAt(0)

    val userRegexps = mutableListOf<String>()
    val iterator = commands.listIterator()
    iterator.forEach { currentCommand ->
      when {
        currentCommand == AUTHOR_PARAMETER -> {
          if (iterator.hasNext()) {
            iterator.remove()
            userRegexps.add(iterator.next())
            iterator.remove()
          }
        }
        currentCommand.startsWith(AUTHOR_PARAMETER_WITH_SUFFIX) -> {
          val parameter = currentCommand.substringAfter(AUTHOR_PARAMETER_WITH_SUFFIX)
          if (parameter.isNotBlank()) {
            userRegexps.add(parameter)
            iterator.remove()
          }
        }
      }
    }

    val userNames = userRegexps.map { regexp ->
      val regex = ".*$regexp.*".toRegex()
      project.service<VcsUserRegistry>().users.filter { user ->
        regex.matches(user.name.toLowerCase())
      }
    }
      .flatten()
      .distinct()
      .map { it.name }

    val branchesParameter = commands.find { it.startsWith(BRANCHES_PARAMETER) }
    var branchesPatterns: String? = null
    if (branchesParameter != null) {
      branchesPatterns = branchesParameter.substringAfter(BRANCHES_PARAMETER)
      commands.remove(branchesParameter)
    }

    var branch: String? = null
    if (commands.isNotEmpty()) {
      branch = commands[0]
      if (branch.startsWith('-')) return null
      commands.remove(branch)
    }

    if (commands.isEmpty()) {
      return Parameters(branch, branchesPatterns, userNames)
    }

    return null
  }

  companion object {
    data class Parameters(val branch: String? = null, val branchesPatterns: String? = null, val users: List<String>)

    private val LOG = Logger.getInstance(GitLogTerminalCustomCommandHandler::class.java)
    private const val GIT_LOG_COMMAND = "git log"
    private const val AUTHOR_PARAMETER = "--author"
    private const val AUTHOR_PARAMETER_WITH_SUFFIX = "$AUTHOR_PARAMETER="
    private const val BRANCHES_PARAMETER = "--branches="
  }
}