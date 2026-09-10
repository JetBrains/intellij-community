// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.Executor
import com.intellij.openapi.vcs.FilePath
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.test.refresh
import com.intellij.vcsUtil.VcsFileUtil
import com.intellij.vcsUtil.VcsUtil
import git4idea.config.GitExecutableManager
import git4idea.repo.GitRepository
import git4idea.test.GitPlatformTestContext
import git4idea.test.createRepository
import git4idea.test.git
import git4idea.test.gitPlatformContextFixture
import org.apache.commons.lang3.RandomStringUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class GitIndexStatusTest {
  private val fixture = gitPlatformContextFixture()
  private val context: GitPlatformTestContext get() = fixture.get()
  private var _repository: GitRepository? = null
  private val repositoryFiles = mutableListOf<FilePath>()

  private val repository
    get() = _repository!!

  private val executable
    get() = GitExecutableManager.getInstance().getExecutable(context.project)

  @BeforeEach
  fun setUp(): Unit = with(context) {

    _repository = createRepository(project, projectPath)

    repositoryFiles.add(VcsUtil.getFilePath(repository.root, "file.txt"))
    repositoryFiles.add(VcsUtil.getFilePath(repository.root, "dir/nested_file.txt"))

    Executor.cd(projectPath)
    for (file in repositoryFiles) {
      Executor.touch(file.relativePath().system(), RandomStringUtils.insecure().next(200))
    }
    refresh()
    git("add .")
    git("commit -m initial")
  }

  @Test
  fun `test no changes`() {
    for (file in repositoryFiles) {
      assertThat(gitFileStatus(file)).isEqualTo(LightFileStatus.NotChanged(file.relativePath()))
    }
  }

  @Test
  fun `test worktree changes`() {
    for (file in repositoryFiles) modify(file)
    for (file in repositoryFiles) {
      assertThat(gitFileStatus(file)).isEqualTo(LightFileStatus.StatusRecord(' ', 'M', file.relativePath()))
    }
  }

  @Test
  fun `test index changes`(): Unit = with(context) {
    for (file in repositoryFiles) {
      modify(file)
      git("add ${file.path}")
    }
    for (file in repositoryFiles) {
      assertThat(gitFileStatus(file)).isEqualTo(LightFileStatus.StatusRecord('M', ' ', file.relativePath()))
    }
  }

  @Test
  fun `test both changes`(): Unit = with(context) {
    for (file in repositoryFiles) {
      modify(file)
      git("add ${file.path}")
      modify(file)
    }
    for (file in repositoryFiles) {
      assertThat(gitFileStatus(file)).isEqualTo(LightFileStatus.StatusRecord('M', 'M', file.relativePath()))
    }
  }

  @Test
  fun `test deleted in worktree`() {
    for (file in repositoryFiles) {
      Executor.rm(file.relativePath().system())
    }
    for (file in repositoryFiles) {
      assertThat(gitFileStatus(file)).isEqualTo(LightFileStatus.StatusRecord(' ', 'D', file.relativePath()))
    }
  }

  @Test
  fun `test deleted in the index`(): Unit = with(context) {
    for (file in repositoryFiles) {
      Executor.touch((file.parentPath!!.relativePath() + "/.keep").system()) // to keep the parent dir
      git("rm ${file.relativePath()}")
    }
    for (file in repositoryFiles) {
      assertThat(gitFileStatus(file)).isEqualTo(LightFileStatus.StatusRecord('D', ' ', file.relativePath()))
    }
  }

  @AfterEach
  fun tearDown() {
    repositoryFiles.clear()
    _repository = null
  }

  private fun modify(file: FilePath) {
    Executor.append(file.relativePath().system(), RandomStringUtils.insecure().next(10))
  }

  private fun gitFileStatus(file: FilePath) = getFileStatus(file.virtualFileParent!!, file, executable)

  private fun FilePath.relativePath() = VcsFileUtil.relativePath(repository.root, this)
  private fun String.system() = FileUtil.toSystemDependentName(this)
}