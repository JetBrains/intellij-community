// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.Executor
import com.intellij.openapi.vcs.FilePath
import com.intellij.vcsUtil.VcsFileUtil
import com.intellij.vcsUtil.VcsUtil
import git4idea.config.GitExecutableManager
import git4idea.repo.GitRepository
import git4idea.test.GitPlatformTest
import git4idea.test.git
import junit.framework.TestCase
import org.apache.commons.lang3.RandomStringUtils

class GitIndexStatusTest : GitPlatformTest() {
  private var _repository: GitRepository? = null
  private val repositoryFiles = mutableListOf<FilePath>()

  private val repository
    get() = _repository!!

  private val executable
    get() = GitExecutableManager.getInstance().getExecutable(project)

  override fun setUp() {
    super.setUp()

    _repository = createRepository(projectPath)

    repositoryFiles.add(VcsUtil.getFilePath(repository.root, "file.txt"))
    repositoryFiles.add(VcsUtil.getFilePath(repository.root, "dir/nested_file.txt"))

    Executor.cd(projectPath)
    for (file in repositoryFiles) {
      Executor.touch(file.relativePath().system(), RandomStringUtils.random(200))
    }
    refresh()
    git("add .")
    git("commit -m initial")
  }

  fun `test no changes`() {
    for (file in repositoryFiles) {
      TestCase.assertEquals(LightFileStatus.NotChanged(file.relativePath()), gitFileStatus(file))
    }
  }

  fun `test worktree changes`() {
    for (file in repositoryFiles) modify(file)
    for (file in repositoryFiles) {
      TestCase.assertEquals(LightFileStatus.StatusRecord(' ', 'M', file.relativePath()), gitFileStatus(file))
    }
  }

  fun `test index changes`() {
    for (file in repositoryFiles) {
      modify(file)
      git("add ${file.path}")
    }
    for (file in repositoryFiles) {
      TestCase.assertEquals(LightFileStatus.StatusRecord('M', ' ', file.relativePath()), gitFileStatus(file))
    }
  }

  fun `test both changes`() {
    for (file in repositoryFiles) {
      modify(file)
      git("add ${file.path}")
      modify(file)
    }
    for (file in repositoryFiles) {
      TestCase.assertEquals(LightFileStatus.StatusRecord('M', 'M', file.relativePath()), gitFileStatus(file))
    }
  }

  fun `test deleted in worktree`() {
    for (file in repositoryFiles) {
      Executor.rm(file.relativePath().system())
    }
    for (file in repositoryFiles) {
      TestCase.assertEquals(LightFileStatus.StatusRecord(' ', 'D', file.relativePath()), gitFileStatus(file))
    }
  }

  fun `test deleted in the index`() {
    for (file in repositoryFiles) {
      Executor.touch((file.parentPath!!.relativePath() + "/.keep").system()) // to keep the parent dir
      git("rm ${file.relativePath()}")
    }
    for (file in repositoryFiles) {
      TestCase.assertEquals(LightFileStatus.StatusRecord('D', ' ', file.relativePath()), gitFileStatus(file))
    }
  }

  override fun tearDown() {
    try {
      repositoryFiles.clear()
      _repository = null
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  private fun modify(file: FilePath) {
    Executor.append(file.relativePath().system(), RandomStringUtils.random(10))
  }

  private fun gitFileStatus(file: FilePath) = getFileStatus(file.virtualFileParent!!, file, executable)

  private fun FilePath.relativePath() = VcsFileUtil.relativePath(repository.root, this)
  private fun String.system() = FileUtil.toSystemDependentName(this)
}