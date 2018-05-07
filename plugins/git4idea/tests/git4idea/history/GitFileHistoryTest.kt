/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package git4idea.history

import com.intellij.dvcs.DvcsUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.Executor
import com.intellij.openapi.vcs.Executor.ourCurrentDir
import com.intellij.openapi.vcs.Executor.touch
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.util.CollectConsumer
import com.intellij.util.Consumer
import com.intellij.util.ExceptionUtil
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitFileRevision
import git4idea.test.*
import junit.framework.TestCase
import java.io.File
import java.io.IOException
import java.util.*

class GitFileHistoryTest : GitSingleRepoTest() {

  fun `test commit message with escape sequence`() {
    touch("a.txt")
    add()
    val message = "Before \u001B[30;47mescaped\u001B[0m after"
    commit(message)

    val history = GitFileHistory.collectHistory(project, VcsUtil.getFilePath(projectRoot, "a.txt"), "-1")
    assertEquals("Commit message is incorrect", message, history[0].commitMessage)
  }

  // Inspired by IDEA-89347
  @Throws(Exception::class)
  fun `test cyclic rename`() {
    val commits = ArrayList<TestCommit>()

    commits.add(add("PostHighlightingPass.java", Executor.mkdir("source")))
    commits.add(modify(commits.last().file))

    commits.add(move(commits.last().file, Executor.mkdir("codeInside-impl")))
    commits.add(modify(commits.last().file))

    commits.add(move(commits.last().file, Executor.mkdir("codeInside")))
    commits.add(modify(commits.last().file))

    commits.add(move(commits.last().file, Executor.mkdir("lang-impl")))
    commits.add(modify(commits.last().file))

    commits.add(move(commits.last().file, Executor.child("source")))
    commits.add(modify(commits.last().file))

    commits.add(move(commits.last().file, Executor.mkdir("java")))
    commits.add(modify(commits.last().file))

    commits.reverse()

    val vFile = VcsUtil.getVirtualFileWithRefresh(commits.first().file)
    TestCase.assertNotNull(vFile)
    val history = GitFileHistory.collectHistory(myProject, VcsUtil.getFilePath(vFile!!))
    assertSameHistory(commits, history)
  }

  @Throws(Exception::class)
  fun `test history`() {
    val commits = ArrayList<TestCommit>()

    commits.add(add("a.txt", ourCurrentDir()))
    commits.add(modify(commits.last().file))

    commits.add(rename(commits.last().file, File(Executor.mkdir("dir"), "b.txt")))

    for (i in 0..3) {
      commits.add(modify(commits.last().file))
    }

    commits.reverse()

    val history = GitFileHistory.collectHistory(myProject, VcsUtil.getFilePath(commits.first().file))
    assertSameHistory(commits, history)
  }

  @Throws(Exception::class)
  fun `test appendable history`() {
    val commits = ArrayList<TestCommit>()

    commits.add(add("a.txt", ourCurrentDir()))
    commits.add(modify(commits.last().file))

    commits.add(rename(commits.last().file, File(Executor.mkdir("dir"), "b.txt")))

    for (i in 0..3) {
      commits.add(modify(commits.last().file))
    }

    commits.reverse()

    val history = ArrayList<GitFileRevision>()
    GitFileHistory.loadHistory(myProject, VcsUtil.getFilePath(commits.first().file), repo.root, null, CollectConsumer(history),
                               Consumer { exception: VcsException ->
                                 TestCase.fail("No exception expected " + ExceptionUtil.getThrowableText(exception))
                               })
    assertSameHistory(commits, history)
  }

  private fun assertSameHistory(expected: List<TestCommit>, actual: List<VcsFileRevision>) {
    TestCase.assertEquals("History size doesn't match. Actual history: \n" + toReadable(actual), expected.size, actual.size)
    TestCase.assertEquals("History is different.", toReadable(expected), toReadable(actual))
  }

  private class TestCommit(val hash: String, val commitMessage: String, val file: File)

  private fun move(file: File, dir: File): TestCommit {
    repo.mv(file, dir)

    val message = "Moved ${file.path} to ${dir.name}"
    repo.addCommit(message)

    return TestCommit(last(), message, File(dir, file.name))
  }

  private fun rename(file: File, newFile: File): TestCommit {
    repo.mv(file, newFile)

    val message = "Renamed ${file.path} to ${newFile.name}"
    repo.addCommit(message)

    return TestCommit(last(), message, newFile)
  }

  @Throws(IOException::class)
  private fun modify(file: File): TestCommit {
    FileUtil.appendToFile(file, "Modified")

    val message = "Modified ${file.path}"
    repo.addCommit(message)

    return TestCommit(last(), message, file)
  }

  private fun add(fileName: String, dir: File): TestCommit {
    val relativePath = dir.relativeTo(ourCurrentDir()).path
    val file = touch("$relativePath/$fileName", "Initial content")

    val message = "Created $fileName in ${dir.name}"
    repo.addCommit(message)

    return TestCommit(last(), message, file)
  }

  private fun toReadable(history: Collection<VcsFileRevision>): String {
    val maxSubjectLength = history.map { it.commitMessage?.length ?: 0 }.max() ?: 0
    val sb = StringBuilder()
    for (revision in history) {
      val rev = revision as GitFileRevision
      val relPath = FileUtil.getRelativePath(File(projectPath), rev.path.ioFile)
      sb.append(String.format("%s  %-" + maxSubjectLength + "s  %s%n", DvcsUtil.getShortHash(rev.hash), rev.commitMessage, relPath))
    }
    return sb.toString()
  }

  private fun toReadable(history: List<TestCommit>): String {
    val maxSubjectLength = history.map { it.commitMessage.length }.max() ?: 0
    val sb = StringBuilder()
    for (commit in history) {
      val relPath = FileUtil.getRelativePath(File(projectPath), commit.file)
      sb.append(String.format("%s  %-" + maxSubjectLength + "s  %s%n", DvcsUtil.getShortHash(commit.hash), commit.commitMessage, relPath))
    }
    return sb.toString()
  }
}