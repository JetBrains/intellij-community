// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.test

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.VcsTestUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.test.updateChangeListManager
import git4idea.GitUtil
import git4idea.repo.GitRepository
import java.nio.file.Files
import kotlin.test.DefaultAsserter.assertTrue

interface GitSingleRepoContext : GitPlatformTestContext {
  val repo: GitRepository
}

fun TestFixture<GitPlatformTestContext>.gitSingleRepoFixture(makeInitialCommit: Boolean): TestFixture<GitSingleRepoContext> = testFixture {
  val gitPlatformContext = init()
  val repo = createRepository(gitPlatformContext.project, gitPlatformContext.projectNioRoot, makeInitialCommit)
  val result = object : GitSingleRepoContext, GitPlatformTestContext by gitPlatformContext {
    override val repo = repo
  }
  initialized(result) {}
}

fun GitSingleRepoContext.build(f: RepoBuilder.() -> Unit) = build(repo, f)

fun GitSingleRepoContext.git(command: String, ignoreExitCode: Boolean = false) = repo.git(command, ignoreExitCode)

fun GitSingleRepoContext.file(path: String) = repo.file(path)

fun GitSingleRepoContext.updateUntrackedFiles() {
  updateUntrackedFiles(repo)
}

fun GitSingleRepoContext.prepareUnversionedFile(filePath: String, content: String = "initial\ncontent\n"): VirtualFile {
  val path = projectNioRoot.resolve(filePath)
  Files.createDirectories(path.parent)
  Files.createFile(path)

  FileUtil.writeToFile(path.toFile(), content)

  val file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(path.toFile())!!
  updateChangeListManager()
  updateUntrackedFiles()
  assertUnversioned(file)
  return file
}

fun GitSingleRepoContext.commitDetails(hash: String): VcsFullCommitDetails =
  VcsLogUtil.getDetails(findGitLogProvider(repo.project), repo.root, listOf(hash)).first()

fun GitSingleRepoContext.renameFile(file: VirtualFile, newName: String) {
  VcsTestUtil.renameFileInCommand(project, file, newName)
  updateChangeListManager()
  updateUntrackedFiles()
}

fun GitSingleRepoContext.assertUnversioned(file: VirtualFile) {
  assertTrue("File should be unversioned! All changes: " + GitUtil.getLogString(projectPath, changeListManager.allChanges),
             changeListManager.isUnversioned(file))
}