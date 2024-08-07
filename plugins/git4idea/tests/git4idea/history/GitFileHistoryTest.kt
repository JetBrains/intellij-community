// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.history

import com.intellij.dvcs.DvcsUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.Executor.*
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.CollectConsumer
import com.intellij.util.ExceptionUtil
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitFileRevision
import git4idea.GitUtil
import git4idea.test.*
import junit.framework.TestCase
import org.apache.commons.lang3.RandomStringUtils
import java.io.File
import java.io.IOException

class GitFileHistoryTest : GitSingleRepoTest() {

  override fun makeInitialCommit(): Boolean = false

  @Throws(VcsException::class)
  fun `test commit message with escape sequence`() {
    touch("a.txt")
    add()
    val message = "Before \u001B[30;47mescaped\u001B[0m after"
    commit(message)

    markEverythingDirtyAndSync()
    val history = GitFileHistory.collectHistory(project, VcsUtil.getFilePath(projectRoot, "a.txt"), "-1")
    assertEquals("Commit message is incorrect", message, history[0].commitMessage)
  }

  // Inspired by IDEA-89347
  @Throws(VcsException::class, IOException::class)
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
    val history = collectFileHistory(commits.first().file)
    assertSameHistory(commits, history)
  }

  @Throws(VcsException::class, IOException::class)
  fun `test history`() {
    val commits = ArrayList<TestCommit>()

    commits.add(add("a.txt", ourCurrentDir()))
    commits.add(modify(commits.last().file))

    commits.add(rename(commits.last().file, File(mkdir("dir"), "b.txt")))

    for (i in 0..3) {
      commits.add(modify(commits.last().file))
    }

    commits.reverse()

    val file = commits.first().file

    val history = collectFileHistory(file)
    assertSameHistory(commits, history, file)
  }

  @Throws(VcsException::class, IOException::class)
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
    markEverythingDirtyAndSync()
    GitFileHistory.loadHistory(myProject, VcsUtil.getFilePath(commits.first().file, false), null, CollectConsumer(history),
                               { exception: VcsException ->
                                 TestCase.fail("No exception expected " + ExceptionUtil.getThrowableText(exception))
                               })
    assertSameHistory(commits, history)
  }

  @Throws(VcsException::class, IOException::class)
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

    val history = collectFileHistory(commits.first().file)
    assertSameHistory(commits, history)
  }

  @Throws(VcsException::class, IOException::class)
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

    val history = collectFileHistory(commits.first().file)
    assertSameHistory(commits, history)
  }

  @Throws(VcsException::class, IOException::class)
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

    val history = collectFileHistory(file)
    assertSameHistory(commits, history)
  }

  @Throws(VcsException::class, IOException::class)
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

    val history = collectFileHistory(file)
    assertSameHistory(commits, history)
  }

  @Throws(VcsException::class)
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
    repo1Commits.add(TestCommit(monorepoMerge, monorepoMergeMessage, repo.root, repo1MovedFile))
    repo2Commits.add(TestCommit(monorepoMerge, monorepoMergeMessage, repo.root, repo2MovedFile))

    assertSameHistory(repo1Commits.asReversed(), collectFileHistory(repo1MovedFile))
    assertSameHistory(repo2Commits.asReversed(), collectFileHistory(repo2MovedFile))
  }

  @Throws(VcsException::class)
  fun `test history through submodule merge`() {
    val commits = ArrayList<TestCommit>()

    val fileName = "file.txt"
    commits.add(add(fileName, ourCurrentDir()))
    val file = commits.last().file

    repo.checkout("--orphan", "submodule-master")
    rm(file)
    commits.add(add(fileName, ourCurrentDir()))

    repo.checkout("master")
    git.merge(repo, "submodule-master", mutableListOf("--allow-unrelated-histories"))

    val submoduleMerge = "monorepo merge"
    val monorepoMerge = repo.addCommit(submoduleMerge)
    commits.add(TestCommit(monorepoMerge, submoduleMerge, repo.root, file))

    assertSameHistory(commits.asReversed(), collectFileHistory(file, full = true))
  }

  @Throws(VcsException::class, IOException::class)
  fun `test history through parallel renames`() {
    val commits = ArrayList<TestCommit>()

    val fileName = "file.txt"
    commits.add(add(fileName, ourCurrentDir()))
    val file = commits.last().file

    val renamedFileName = "renamed.txt"

    repo.checkout("-b", "other")
    commits.add(modify(file, appendContent = RandomStringUtils.randomAlphanumeric(20)))
    commits.add(rename(file, renamedFileName))

    repo.checkout("master")
    commits.add(modify(file, appendContent = RandomStringUtils.randomAlphanumeric(20)))
    commits.add(rename(file, renamedFileName))

    val renamedFile = commits.last().file

    git.merge(repo, "other", mutableListOf("--no-ff"))
    echo(renamedFile.path, RandomStringUtils.randomAlphanumeric(20))
    repo.add(renamedFile.path)
    commits.add(commit(renamedFile, "merge other branch"))

    assertSameHistory(commits.sortedBy { it.hash },
                      collectFileHistory(renamedFile, full = true).sortedBy { it.revisionNumber.asString() })
  }

  @Throws(VcsException::class, IOException::class)
  fun `test history through incorrectly detected rename in monorepo merge`() {
    val commits = ArrayList<TestCommit>()

    val content = RandomStringUtils.randomAlphanumeric(200)

    val repo1FileName = "file1.txt"
    commits.add(add(repo1FileName, ourCurrentDir(), initialContent = content))
    val repo1File = commits.last().file

    val repo2FileName = "file2.txt"
    repo.checkout("--orphan", "repo2-master")
    rm(repo1File)
    commits.add(add(repo2FileName, ourCurrentDir(), initialContent = "$content\n123"))
    val repo2File = commits.last().file

    repo.checkout("master")
    git.merge(repo, "repo2-master", mutableListOf("--no-commit", "--allow-unrelated-histories"))

    val repo1MovedFile = File(mkdir("repo1"), repo1FileName)
    val repo2MovedFile = File(mkdir("repo2"), repo2FileName)
    repo.mv(repo1File, repo1MovedFile)
    repo.mv(repo2File, repo2MovedFile)
    val monorepoMergeMessage = "monorepo merge"
    val monorepoMerge = repo.addCommit(monorepoMergeMessage)
    val monorepoMergeFile1 = TestCommit(monorepoMerge, monorepoMergeMessage, repo.root, repo1MovedFile)
    val monorepoMergeFile2 = TestCommit(monorepoMerge, monorepoMergeMessage, repo.root, repo2MovedFile)

    assertSameHistory((commits + monorepoMergeFile1).sortedBy { it.hash },
                      collectFileHistory(repo1MovedFile, full = true).sortedBy { it.revisionNumber.asString() }, repo1MovedFile)
    assertSameHistory((commits + monorepoMergeFile2).sortedBy { it.hash },
                      collectFileHistory(repo2MovedFile, full = true).sortedBy { it.revisionNumber.asString() }, repo2MovedFile)
  }

  @Throws(VcsException::class, IOException::class)
  fun `test branches history`() {
    val commonCommits = ArrayList<TestCommit>()
    val masterCommits = ArrayList<TestCommit>()
    val branchCommits = ArrayList<TestCommit>()

    val fileName = "a.txt"
    commonCommits.add(add(fileName, ourCurrentDir()))
    val file = commonCommits.last().file

    commonCommits.add(modify(file))

    val branchingPoint = commonCommits.last().hash

    add("b.txt", ourCurrentDir())
    masterCommits.add(modify(file))

    repo.checkoutNew("newBranch", branchingPoint)
    add("c.txt", ourCurrentDir())
    branchCommits.add(modify(file))

    commonCommits.reverse()
    masterCommits.reverse()
    branchCommits.reverse()

    assertSameHistory(branchCommits + masterCommits + commonCommits, collectFileHistory(file, GitLogUtil.LOG_ALL, true))
    assertSameHistory(masterCommits + commonCommits, collectFileHistory(file, listOf("master"), true))
    assertSameHistory(branchCommits + commonCommits, collectFileHistory(file, listOf("newBranch"), true))
    assertSameHistory(commonCommits, collectFileHistory(file, listOf(branchingPoint), true))
  }

  @Throws(VcsException::class, IOException::class)
  fun `test branches history with rename`() {
    val commitsBeforeRename = ArrayList<TestCommit>()
    val commitsAfterRename = ArrayList<TestCommit>()

    commitsBeforeRename.add(add("a.txt", ourCurrentDir()))
    commitsBeforeRename.add(modify(commitsBeforeRename.last().file))

    val pointBeforeRename = commitsBeforeRename.last().hash
    val fileBeforeRename = commitsBeforeRename.last().file

    commitsAfterRename.add(rename(fileBeforeRename, File(mkdir("dir"), "b.txt")))
    commitsAfterRename.add(modify(commitsAfterRename.last().file))

    val fileAfterRename = commitsAfterRename.last().file

    commitsBeforeRename.reverse()
    commitsAfterRename.reverse()

    assertSameHistory(commitsAfterRename + commitsBeforeRename, collectFileHistory(fileAfterRename, listOf("master"), true))
    assertSameHistory(emptyList(), collectFileHistory(fileAfterRename, listOf(pointBeforeRename), true))
    assertSameHistory(commitsBeforeRename, collectFileHistory(fileBeforeRename, listOf(pointBeforeRename), true))
  }

  private fun collectFileHistory(file: File, full: Boolean = false): List<VcsFileRevision> {
    return collectFileHistory(file, listOf(GitUtil.HEAD), full)
  }

  private fun collectFileHistory(file: File, startingRevisions: List<String>, full: Boolean): List<VcsFileRevision> {
    markEverythingDirtyAndSync()

    val path = VcsUtil.getFilePath(file, false)
    val gitFileHistory = GitFileHistory(myProject, repo.root, path, startingRevisions, full)
    TestCase.assertEquals("Last commit path differs from the requested one", path, gitFileHistory.getFilePath())
    return buildList {
      gitFileHistory.load(::add)
    }
  }

  private fun markEverythingDirtyAndSync() {
    VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
    ChangeListManagerImpl.getInstanceImpl(project).waitEverythingDoneInTestMode()
  }

  private fun assertSameHistory(expected: List<TestCommit>, actual: List<VcsFileRevision>, file: File? = null) {
    val description = if (file == null) "History is different." else "History for ${file.relativePath()} is different."
    TestCase.assertEquals(description, expected, actual.map { it.toTestCommit() })
  }

  private data class TestCommit(val hash: String, val commitMessage: String, val root: VirtualFile, val file: File) {
    override fun toString(): String {
      val relativePath = FileUtil.getRelativePath(root.toNioPath().toFile(), file)
      return "${DvcsUtil.getShortHash(hash)}:${relativePath}:$commitMessage"
    }
  }

  private fun VcsFileRevision.toTestCommit(): TestCommit {
    return TestCommit(revisionNumber.asString(), commitMessage!!, repo.root, (this as GitFileRevision).path.ioFile)
  }

  private fun move(file: File, dir: File): TestCommit {
    repo.mv(file, dir)
    return commit(File(dir, file.name), "Moved ${file.relativePath()} to ${dir.relativePath()}")
  }

  private fun rename(file: File, newFile: File): TestCommit {
    repo.mv(file, newFile)
    return commit(newFile, "Renamed ${file.relativePath()} to ${newFile.relativePath()}")
  }

  private fun rename(file: File, newFileName: String): TestCommit {
    val newFile = File(file.parentFile, newFileName)
    return rename(file, newFile)
  }

  @Throws(IOException::class)
  private fun modify(file: File, appendContent: String = "Modified"): TestCommit {
    FileUtil.appendToFile(file, appendContent)
    return commit(file, "Modified ${file.relativePath()}")
  }

  private fun add(fileName: String, dir: File, initialContent: String = RandomStringUtils.randomAlphanumeric(200)): TestCommit {
    val relativePath = dir.relativeTo(ourCurrentDir()).path
    val file = touch("$relativePath/$fileName", initialContent)
    return commit(file, "Created ${file.relativePath()}")
  }

  private fun commit(file: File, message: String): TestCommit {
    repo.addCommit(message)
    return TestCommit(last(), message, repo.root, file)
  }

  private fun File.relativePath(): String? = FileUtil.getRelativePath(repo.root.toNioPath().toFile(), this)
}