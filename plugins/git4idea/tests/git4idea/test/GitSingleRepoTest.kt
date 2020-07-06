// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.test

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.Executor.cd
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY
import com.intellij.openapi.vcs.VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY
import com.intellij.openapi.vcs.VcsTestUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.vcs.AbstractVcsTestCase
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.repo.GitRepository
import java.nio.file.Files

abstract class GitSingleRepoTest : GitPlatformTest() {
  protected lateinit var repo: GitRepository

  override fun setUp() {
    super.setUp()
    repo = createRepository(project, projectNioRoot, makeInitialCommit())
    cd(projectPath)
  }

  protected open fun makeInitialCommit() = true

  protected fun VcsConfiguration.StandardConfirmation.doSilently() =
    AbstractVcsTestCase.setStandardConfirmation(project, GitVcs.NAME, this, DO_ACTION_SILENTLY)

  protected fun VcsConfiguration.StandardConfirmation.doNothing() =
    AbstractVcsTestCase.setStandardConfirmation(project, GitVcs.NAME, this, DO_NOTHING_SILENTLY)

  protected fun prepareUnversionedFile(filePath: String): VirtualFile {
    val path = projectNioRoot.resolve(filePath)
    Files.createDirectories(path.parent)
    Files.createFile(path)

    FileUtil.writeToFile(path.toFile(), "initial\ncontent\n")

    val file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(path.toFile())!!
    updateChangeListManager()
    assertUnversioned(file)
    return file
  }

  protected fun VirtualFile.createDir(dir: String) = VcsTestUtil.findOrCreateDir(project, this, dir)!!

  protected fun VirtualFile.createFile(fileName: String, content: String = Math.random().toString()) =
    VcsTestUtil.createFile(project, this, fileName, content)!!

  protected fun renameFile(file: VirtualFile, newName: String) {
    VcsTestUtil.renameFileInCommand(project, file, newName)
    updateChangeListManager()
  }

  protected fun build(f: RepoBuilder.() -> Unit) = build(repo, f)

  protected fun assertUnversioned(file: VirtualFile) {
    assertTrue("File should be unversioned! All changes: " + GitUtil.getLogString(projectPath, changeListManager.allChanges),
               changeListManager.isUnversioned(file))
  }

  @JvmOverloads
  protected fun git(command: String, ignoreExitCode: Boolean = false) = repo.git(command, ignoreExitCode)

  internal fun file(path: String) = repo.file(path)
}
