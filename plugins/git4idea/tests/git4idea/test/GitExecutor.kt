/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:JvmName("GitExecutor")

package git4idea.test

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.Executor.*
import com.intellij.testFramework.vcs.ExecutableHelper
import com.intellij.vcs.log.util.VcsLogUtil
import git4idea.commands.Git
import git4idea.commands.GitLineHandler
import git4idea.commands.getGitCommandInstance
import git4idea.repo.GitRepository
import org.junit.Assert.assertFalse
import java.io.File

fun gitExecutable() = GitExecutorHolder.PathHolder.GIT_EXECUTABLE

@JvmOverloads
fun GitRepository.git(command: String, ignoreNonZeroExitCode: Boolean = false) = cd { git(project, command, ignoreNonZeroExitCode) }
fun GitPlatformTest.git(command: String, ignoreNonZeroExitCode: Boolean = false) = git(project, command, ignoreNonZeroExitCode)
@JvmOverloads
fun git(project: Project, command: String, ignoreNonZeroExitCode: Boolean = false): String {
  val workingDir = ourCurrentDir()
  val split = splitCommandInParameters(command)
  val handler = GitLineHandler(project, workingDir, getGitCommandInstance(split[0]))
  handler.addParameters(split.subList(1, split.size))

  val result = Git.getInstance().runCommand(handler)
  if (result.exitCode != 0 && !ignoreNonZeroExitCode) {
    throw IllegalStateException("Command [$command] failed with exit code ${result.exitCode}")
  }
  return result.errorOutputAsJoinedString + result.outputAsJoinedString
}

fun cd(repository: GitRepository) = cd(repository.root.path)

@JvmOverloads
fun GitRepository.add(path: String = ".") = cd { add(project, path) }

fun GitPlatformTest.add(path: String = ".") = add(project, path)
private fun add(project: Project, path: String = ".") = git(project, "add --verbose " + path)

fun GitRepository.addCommit(message: String) = cd { addCommit(project, message) }
fun GitPlatformTest.addCommit(message: String) = addCommit(project, message)
private fun addCommit(project: Project, message: String): String {
  add(project)
  return commit(project, message)
}

fun GitRepository.branch(name: String) = cd { branch(project, name) }
fun GitPlatformTest.branch(name: String) = branch(project, name)
private fun branch(project: Project, name: String) = git(project, "branch $name")

fun GitRepository.checkout(vararg params: String) = cd { checkout(project, *params) }
fun GitPlatformTest.checkout(vararg params: String) = checkout(project, *params)
private fun checkout(project: Project, vararg params: String) = git(project, "checkout ${params.joinToString(" ")}")

fun GitRepository.checkoutNew(branchName: String, startPoint: String = "") = cd { checkoutNew(project, branchName, startPoint) }
private fun checkoutNew(project: Project, branchName: String, startPoint: String) =
  git(project, "checkout -b $branchName $startPoint")

fun GitRepository.commit(message: String) = cd { commit(project, message) }
fun GitPlatformTest.commit(message: String) = commit(project, message)
private fun commit(project: Project, message: String): String {
  git(project, "commit -m '$message'")
  return last(project)
}

@JvmOverloads
fun GitRepository.tac(file: String, content: String = "content" + Math.random()) = cd { tac(project, file, content) }

fun GitPlatformTest.tac(file: String, content: String = "content" + Math.random()) = tac(project, file, content)
private fun tac(project: Project, file: String, content: String): String {
  touch(file, content)
  return addCommit(project, "Touched $file")
}

fun GitRepository.tacp(file: String) = cd { tacp(project, file) }
fun GitPlatformTest.tacp(file: String) = tacp(project, file)
private fun tacp(project: Project, file: String): String {
  touch(file)
  addCommit(project, "Touched $file")
  return git(project, "push")
}

fun GitRepository.appendAndCommit(file: String, additionalContent: String) = cd { appendAndCommit(project, file, additionalContent) }
private fun appendAndCommit(project: Project, file: String, additionalContent: String): String {
  append(file, additionalContent)
  add(project, file)
  return commit(project, "Add more content")
}

fun GitRepository.modify(file: String): String = cd { modify(project, file) }
fun GitPlatformTest.modify(file: String): String = modify(project, file)
private fun modify(project: Project, file: String): String {
  overwrite(file, "content" + Math.random())
  return addCommit(project, "modified " + file)
}

fun GitRepository.last() = cd { last(project) }
fun GitPlatformTest.last() = last(project)
private fun last(project: Project) = git(project, "log -1 --pretty=%H")

fun GitRepository.lastMessage() = cd { lastMessage(project) }
fun GitPlatformTest.lastMessage() = lastMessage(project)
private fun lastMessage(project: Project) = message(project, "HEAD")

fun GitRepository.message(revision: String) = cd { message(project, revision)}
private fun message(project: Project, revision: String) =
  git(project, "log $revision --no-walk --pretty=${getPrettyFormatTagForFullCommitMessage(project)}")

fun GitRepository.log(vararg params: String) = cd { log(project, *params) }
fun GitPlatformTest.log(vararg params: String) = log(project, *params)
private fun log(project: Project, vararg params: String) = git(project, "log " + StringUtil.join(params, " "))

fun GitRepository.mv(fromPath: String, toPath: String) = cd { mv(project, fromPath, toPath) }
fun GitPlatformTest.mv(fromPath: String, toPath: String) = mv(project, fromPath, toPath)
private fun mv(project: Project, fromPath: String, toPath: String) = git(project, "mv $fromPath $toPath")

fun GitRepository.mv(from: File, to: File) {
  mv(from.path, to.path)
}

fun GitRepository.prepareConflict(initialBranch: String = "master",
                                  featureBranch: String = "feature",
                                  conflictingFile: String = "c.txt"): String {
  checkout(initialBranch)
  val file = file(conflictingFile)
  file.create("initial\n").addCommit("initial")
  branch(featureBranch)
  val commit = file.append("master\n").addCommit("on_master").hash()
  checkout(featureBranch)
  file.append("feature\n").addCommit("on_feature")
  return commit
}

private fun GitRepository.cd(command: () -> String): String {
  cd(this)
  return command()
}

internal fun GitRepository.file(fileName: String): TestFile {
  val f = child(fileName)
  return TestFile(this, f)
}

private class GitExecutorHolder {
  //using inner class to avoid extra work during class loading of unrelated tests
  internal object PathHolder {
    internal val GIT_EXECUTABLE = ExecutableHelper.findGitExecutable()!!
  }
}

internal class TestFile internal constructor(val repo: GitRepository, val file: File) {

  fun append(content: String): TestFile {
    FileUtil.writeToFile(file, content.toByteArray(), true)
    return this
  }

  fun write(content: String): TestFile {
    FileUtil.writeToFile(file, content.toByteArray(), false)
    return this
  }

  fun create(content: String = ""): TestFile {
    assertNotExists()
    FileUtil.writeToFile(file, content.toByteArray(), false)
    return this
  }

  fun assertNotExists(): TestFile {
    assertFalse(file.exists())
    return this
  }

  fun add(): TestFile {
    repo.add(file.path)
    return this
  }

  fun addCommit(message: String): TestFile {
    add()
    repo.commit(message)
    return this
  }

  fun hash() = repo.last()

  fun details() = VcsLogUtil.getDetails(findGitLogProvider(repo.project), repo.root, listOf(hash())).first()!!

  fun exists() = file.exists()

  fun read() = FileUtil.loadFile(file)

  fun cat(): String = FileUtil.loadFile(file)

  fun prepend(content: String): TestFile {
    val previousContent = cat()
    FileUtil.writeToFile(file, content + previousContent)
    return this
  }
}
