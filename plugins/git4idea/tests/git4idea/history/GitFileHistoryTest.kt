// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history

import com.intellij.dvcs.DvcsUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.Executor.*
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.util.CollectConsumer
import com.intellij.util.Consumer
import com.intellij.util.ExceptionUtil
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitFileRevision
import git4idea.test.*
import junit.framework.TestCase
import org.apache.commons.lang.RandomStringUtils
import java.io.File
import java.io.IOException

class GitFileHistoryTest : GitSingleRepoTest() {

  override fun makeInitialCommit(): Boolean = false

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

    commits.add(add("PostHighlightingPass.java", mkdir("source")))
    commits.add(modify(commits.last().file))

    commits.add(move(commits.last().file, mkdir("codeInside-impl")))
    commits.add(modify(commits.last().file))

    commits.add(move(commits.last().file, mkdir("codeInside")))
    commits.add(modify(commits.last().file))

    commits.add(move(commits.last().file, mkdir("lang-impl")))
    commits.add(modify(commits.last().file))

    commits.add(move(commits.last().file, child("source")))
    commits.add(modify(commits.last().file))

    commits.add(move(commits.last().file, mkdir("java")))
    commits.add(modify(commits.last().file))

    commits.reverse()

    updateChangeListManager()
    val history = GitFileHistory.collectHistory(myProject, VcsUtil.getFilePath(commits.first().file))
    assertSameHistory(commits, history)
  }

  @Throws(Exception::class)
  fun `test history`() {
    val commits = ArrayList<TestCommit>()

    commits.add(add("a.txt", ourCurrentDir()))
    commits.add(modify(commits.last().file))

    commits.add(rename(commits.last().file, File(mkdir("dir"), "b.txt")))

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

    commits.add(rename(commits.last().file, File(mkdir("dir"), "b.txt")))

    for (i in 0..3) {
      commits.add(modify(commits.last().file))
    }

    commits.reverse()

    val history = ArrayList<GitFileRevision>()
    GitFileHistory.loadHistory(myProject, VcsUtil.getFilePath(commits.first().file), null, CollectConsumer(history),
                               Consumer { exception: VcsException ->
                                 TestCase.fail("No exception expected " + ExceptionUtil.getThrowableText(exception))
                               })
    assertSameHistory(commits, history)
  }

  @Throws(Exception::class)
  fun `test history through merged rename`() {
    add("initial.txt", ourCurrentDir(), "initial commit")

    val commits = ArrayList<TestCommit>()

    val branchingPoint = last()
    add("unrelated.txt", ourCurrentDir())

    repo.checkoutNew("newBranch", branchingPoint)

    commits.add(add("a.txt", ourCurrentDir()))
    commits.add(modify(commits.last().file))

    repo.checkout("master")
    git.merge(repo, "newBranch", mutableListOf("--no-ff", "--no-commit"))

    commits.add(rename(commits.last().file, File(mkdir("dir"), "b.txt")))
    commits.reverse()

    val history = GitFileHistory.collectHistory(myProject, VcsUtil.getFilePath(commits.first().file))
    assertSameHistory(commits, history)
  }

  @Throws(Exception::class)
  fun `test history through non-trivial merge`() {
    val commits = ArrayList<TestCommit>()

    commits.add(add("a.txt", ourCurrentDir()))

    val branchingPoint = commits.last().hash
    val file = commits.last().file

    commits.add(modify(file, "\nmaster"))

    repo.checkoutNew("newBranch", branchingPoint)
    commits.add(modify(file, "\nbranch"))

    repo.checkout("master")
    git.merge(repo, "newBranch", mutableListOf("--no-ff", "--no-commit"))

    FileUtil.writeToFile(file, "merged")

    commits.add(commit(file, "merge commit"))
    commits.reverse()

    val history = GitFileHistory.collectHistory(myProject, VcsUtil.getFilePath(commits.first().file))
    assertSameHistory(commits, history)
  }

  @Throws(Exception::class)
  fun `test history through trivial merge`() {
    val commits = ArrayList<TestCommit>()

    commits.add(add("a.txt", ourCurrentDir()))
    val file = commits.last().file
    val branchingPoint = commits.last().hash

    add("unrelated.txt", ourCurrentDir())

    repo.checkoutNew("newBranch", branchingPoint)
    commits.add(modify(file))

    repo.checkout("master")
    git.merge(repo, "newBranch", mutableListOf("--no-ff"))

    commits.reverse()

    val history = GitFileHistory.collectHistory(myProject, VcsUtil.getFilePath(file))
    assertSameHistory(commits, history)
  }

  @Throws(Exception::class)
  fun `test history through reverting merge`() {
    val commits = ArrayList<TestCommit>()

    val initialContent = "initial content"
    commits.add(add("a.txt", ourCurrentDir(), initialContent = initialContent))
    val file = commits.last().file
    val branchingPoint = commits.last().hash

    add("unrelated.txt", ourCurrentDir())

    repo.checkoutNew("newBranch", branchingPoint)
    commits.add(modify(file))

    repo.checkout("master")
    git.merge(repo, "newBranch", mutableListOf("--no-ff", "--no-commit"))

    FileUtil.writeToFile(file, initialContent) // revert file to initial content
    commits.add(commit(file, "merge commit"))

    commits.reverse()

    val history = GitFileHistory.collectHistory(myProject, VcsUtil.getFilePath(file))
    assertSameHistory(commits, history)
  }

  @Throws(Exception::class)
  fun `test history through monorepo merge`() {
    val repo1Commits = ArrayList<TestCommit>()
    val repo2Commits = ArrayList<TestCommit>()

    val repo1FileName = "file1.txt"
    repo1Commits.add(add(repo1FileName, ourCurrentDir()))
    val repo1File = repo1Commits.last().file

    val repo2FileName = "file2.txt"
    repo.checkout("--orphan", "repo2-master")
    rm(repo1File)
    repo2Commits.add(add(repo2FileName, ourCurrentDir()))
    val repo2File = repo2Commits.last().file

    repo.checkout("master")
    git.merge(repo, "repo2-master", mutableListOf("--no-commit", "--allow-unrelated-histories"))

    val repo1MovedFile = File(mkdir("repo1"), repo1FileName)
    val repo2MovedFile = File(mkdir("repo2"), repo2FileName)
    repo.mv(repo1File, repo1MovedFile)
    repo.mv(repo2File, repo2MovedFile)
    val monorepoMergeMessage = "monorepo merge"
    val monorepoMerge = repo.addCommit(monorepoMergeMessage)
    repo1Commits.add(TestCommit(monorepoMerge, monorepoMergeMessage, repo1MovedFile))
    repo2Commits.add(TestCommit(monorepoMerge, monorepoMergeMessage, repo2MovedFile))

    assertSameHistory(repo1Commits.asReversed(), GitFileHistory.collectHistory(myProject, VcsUtil.getFilePath(repo1MovedFile)))
    assertSameHistory(repo2Commits.asReversed(), GitFileHistory.collectHistory(myProject, VcsUtil.getFilePath(repo2MovedFile)))
  }

  private fun assertSameHistory(expected: List<TestCommit>, actual: List<VcsFileRevision>) {
    TestCase.assertEquals("History size doesn't match. Actual history: \n" + toReadable(actual), expected.size, actual.size)
    TestCase.assertEquals("History is different.", toReadable(expected), toReadable(actual))
  }

  private class TestCommit(val hash: String, val commitMessage: String, val file: File)

  private fun move(file: File, dir: File): TestCommit {
    repo.mv(file, dir)
    return commit(File(dir, file.name), "Moved ${file.path} to ${dir.name}")
  }

  private fun rename(file: File, newFile: File): TestCommit {
    repo.mv(file, newFile)
    return commit(newFile, "Renamed ${file.path} to ${newFile.name}")
  }

  @Throws(IOException::class)
  private fun modify(file: File, appendContent: String = "Modified"): TestCommit {
    FileUtil.appendToFile(file, appendContent)
    return commit(file, "Modified ${file.path}")
  }

  private fun add(fileName: String, dir: File, initialContent: String = RandomStringUtils.randomAlphanumeric(200)): TestCommit {
    val relativePath = dir.relativeTo(ourCurrentDir()).path
    val file = touch("$relativePath/$fileName", initialContent)
    return commit(file, "Created $fileName in ${dir.name}")
  }

  private fun commit(file: File, message: String): TestCommit {
    repo.addCommit(message)
    return TestCommit(last(), message, file)
  }

  private fun toReadable(history: Collection<VcsFileRevision>): String {
    val maxSubjectLength = history.maxOfOrNull { it.commitMessage?.length ?: 0 } ?: 0
    val sb = StringBuilder()
    for (revision in history) {
      val rev = revision as GitFileRevision
      val relPath = FileUtil.getRelativePath(File(projectPath), rev.path.ioFile)
      sb.append(String.format("%s  %-" + maxSubjectLength + "s  %s%n", DvcsUtil.getShortHash(rev.hash), rev.commitMessage, relPath))
    }
    return sb.toString()
  }

  private fun toReadable(history: List<TestCommit>): String {
    val maxSubjectLength = history.maxOfOrNull { it.commitMessage.length } ?: 0
    val sb = StringBuilder()
    for (commit in history) {
      val relPath = FileUtil.getRelativePath(File(projectPath), commit.file)
      sb.append(String.format("%s  %-" + maxSubjectLength + "s  %s%n", DvcsUtil.getShortHash(commit.hash), commit.commitMessage, relPath))
    }
    return sb.toString()
  }
}