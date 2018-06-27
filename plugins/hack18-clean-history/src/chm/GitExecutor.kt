// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GitExecutor")

package chm

import chm.Executor.*
import com.intellij.openapi.project.Project
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository

fun GitRepository.last() = cd { last(project) }
private fun last(project: Project) = git(project, "log -1 --pretty=%H")

@JvmOverloads
fun GitRepository.git(command: String, ignoreNonZeroExitCode: Boolean = false) = cd { git(project, command, ignoreNonZeroExitCode) }
@JvmOverloads
fun git(project: Project, command: String, ignoreNonZeroExitCode: Boolean = false): String {
  val workingDir = ourCurrentDir()
  val split = splitCommandInParameters(command)
  val handler = GitLineHandler(project, workingDir, getGitCommandInstance(split[0]))
  handler.setWithMediator(false)
  handler.addParameters(split.subList(1, split.size))

  val result = Git.getInstance().runCommand(handler)
  if (result.exitCode != 0 && !ignoreNonZeroExitCode) {
    throw IllegalStateException("Command [$command] failed with exit code ${result.exitCode}\n${result.output}\n${result.errorOutput}")
  }
  return result.errorOutputAsJoinedString + result.outputAsJoinedString
}

private fun GitRepository.cd(command: () -> String): String {
  cd(this)
  return command()
}

fun cd(repository: GitRepository) = cd(repository.root.path)

fun getGitCommandInstance(commandName: String): GitCommand {
  return try {
    val fieldName = commandName.toUpperCase().replace('-', '_')
    GitCommand::class.java.getDeclaredField(fieldName).get(null) as GitCommand
  }
  catch (e: NoSuchFieldException) {
    val constructor = GitCommand::class.java.getDeclaredConstructor(String::class.java, GitCommand.LockingPolicy::class.java)
    constructor.isAccessible = true
    constructor.newInstance(commandName, GitCommand.LockingPolicy.WRITE) as GitCommand
  }
}
