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

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.Executor
import com.intellij.openapi.vcs.Executor.*
import com.intellij.testFramework.vcs.ExecutableHelper
import com.intellij.vcs.log.impl.VcsLogUtil
import git4idea.repo.GitRepository
import org.junit.Assert.assertFalse
import java.io.File

private val LOG: Logger = logger("#git4idea.test.GitExecutor")
private val MAX_RETRIES = 3
private var myVersionPrinted = false

fun gitExecutable() = GitExecutorHolder.PathHolder.GIT_EXECUTABLE

@JvmOverloads fun git(command: String, ignoreNonZeroExitCode: Boolean = false): String {
  printVersionTheFirstTime()
  return doCallGit(command, ignoreNonZeroExitCode)
}

private fun doCallGit(command: String, ignoreNonZeroExitCode: Boolean): String {
  val split = splitCommandInParameters(command)
  split.add(0, gitExecutable())
  val workingDir = ourCurrentDir()
  debug("[" + workingDir.name + "] # git " + command)
  for (attempt in 0..MAX_RETRIES - 1) {
    var stdout: String
    try {
      stdout = run(workingDir, split, ignoreNonZeroExitCode)
      if (!isIndexLockFileError(stdout)) {
        return stdout
      }
    }
    catch (e: Executor.ExecutionException) {
      stdout = e.output
      if (!isIndexLockFileError(stdout)) {
        throw e
      }
    }

    LOG.info("Index lock file error, attempt #$attempt: $stdout")
  }
  throw RuntimeException("fatal error during execution of Git command: \$command")
}

private fun isIndexLockFileError(stdout: String): Boolean {
  return stdout.contains("fatal") && stdout.contains("Unable to create") && stdout.contains(".git/index.lock")
}

fun git(repository: GitRepository?, command: String): String {
  if (repository != null) {
    cd(repository)
  }
  return git(command)
}

fun cd(repository: GitRepository) {
  cd(repository.root.path)
}

@JvmOverloads fun add(path: String = ".") {
  git("add --verbose " + path)
}

fun addCommit(message: String): String {
  add()
  return commit(message)
}

fun branch(name: String) : String {
  return git("branch $name")
}

fun checkout(vararg params: String) {
  git("checkout ${params.joinToString(" ")}")
}

fun checkoutNew(branchName: String, startPoint: String = ""): String {
  return git("checkout -b $branchName $startPoint")
}

fun commit(message: String): String {
  git("commit -m '$message'")
  return last()
}

@JvmOverloads fun tac(file: String, content: String = "content" + Math.random()): String {
  touch(file, content)
  return addCommit("Touched $file")
}

fun tacp(file: String): String {
  touch(file)
  addCommit("Touched $file")
  return git("push")
}

fun appendAndCommit(file: String, additionalContent: String) : String {
  append(file, additionalContent)
  add(file)
  return commit("Add more content")
}

fun modify(file: String): String {
  overwrite(file, "content" + Math.random())
  return addCommit("modified " + file)
}

fun last(): String {
  return git("log -1 --pretty=%H")
}

fun lastMessage(): String {
  return git("log -1 --pretty=%B")
}

fun log(vararg params: String): String {
  return git("log " + StringUtil.join(params, " "))
}

fun mv(fromPath: String, toPath: String) {
  git("mv $fromPath $toPath")
}

fun mv(from: File, to: File) {
  mv(from.path, to.path)
}

private fun printVersionTheFirstTime() {
  if (!myVersionPrinted) {
    myVersionPrinted = true
    doCallGit("version", false)
  }
}

internal fun GitPlatformTest.file(fileName: String): TestFile {
  val f = child(fileName)
  return TestFile(this.project!!, f)
}

private class GitExecutorHolder {
  //using inner class to avoid extra work during class loading of unrelated tests
  internal object PathHolder {
    internal val GIT_EXECUTABLE = ExecutableHelper.findGitExecutable()!!
  }
}

internal class TestFile internal constructor(val project: Project, val file: File) {

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
    add(file.path)
    return this
  }

  fun addCommit(message: String): TestFile {
    add()
    commit(message)
    return this
  }

  fun hash() = last()

  fun details() = VcsLogUtil.getDetails(findGitLogProvider(project), project.baseDir, listOf(hash())).first()!!

  fun exists() = file.exists()

  fun read() = FileUtil.loadFile(file)
}

class TestCommit internal constructor(shortHash: String, hash: String) {

}
