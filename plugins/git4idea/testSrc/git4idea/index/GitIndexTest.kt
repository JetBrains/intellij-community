// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vcs.Executor.overwrite
import com.intellij.openapi.vcs.FilePath
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcsUtil.VcsUtil
import git4idea.commands.Git
import git4idea.commands.GitObjectType
import git4idea.repo.GitRepository
import git4idea.test.GitPlatformTestContext
import git4idea.test.createRepository
import git4idea.test.git
import git4idea.test.gitPlatformContextFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test


private const val FILE = "file.txt"

@TestApplication
class GitIndexTest {
  private val fixture = gitPlatformContextFixture()
  private val context: GitPlatformTestContext get() = fixture.get()

  private lateinit var repository: GitRepository

  @BeforeEach
  fun setUp(): Unit = with(context) {

    repository = createRepository(project, projectPath)

    cd(projectPath)

    touch(FILE, "initial")
    git("add .")
    git("commit -m initial")
  }

  @Test
  fun `test read staged file`(): Unit = with(context) {
    assertThat(readFileContent()).isEqualTo("initial")

    overwrite(FILE, "modified")
    assertThat(readFileContent()).isEqualTo("initial")

    git("add .")
    assertThat(readFileContent()).isEqualTo("modified")

    overwrite(FILE, "modi\nfied")
    git("add .")
    assertThat(readFileContent()).isEqualTo("modi\nfied")
  }

  @Test
  fun `test write staged file`() {
    assertThat(readFileContent()).isEqualTo("initial")

    writeFileContent(FILE, "modified")
    assertThat(readFileContent()).isEqualTo("modified")

    overwrite(FILE, "modi\nfied")
    assertThat(readFileContent()).isEqualTo("modified")
  }

  @Test
  fun `test read permissions1`(): Unit = with(context) {
    assumeFalse(SystemInfo.isWindows) // Can't set executable flag on windows

    assertThat(readFilePermissions()).isEqualTo(false)

    assertThat(FILE.path.ioFile.setExecutable(true)).isTrue()
    git("add .")
    assertThat(readFilePermissions()).isEqualTo(true)

    assertThat(FILE.path.ioFile.setExecutable(false)).isTrue()
    assertThat(readFilePermissions()).isEqualTo(true)

    git("add .")
    assertThat(readFilePermissions()).isEqualTo(false)
  }

  @Test
  fun `test read permissions2`() {
    assertThat(readFilePermissions()).isEqualTo(false)

    setExecutableFlagInIndex(true)
    assertThat(readFilePermissions()).isEqualTo(true)

    setExecutableFlagInIndex(false)
    assertThat(readFilePermissions()).isEqualTo(false)
  }

  @Test
  fun `test write permissions`() {
    assertThat(readFilePermissions()).isEqualTo(false)

    writeFileContent(FILE, "modified", true)
    assertThat(readFilePermissions()).isEqualTo(true)

    writeFileContent(FILE, "modified", false)
    assertThat(readFilePermissions()).isEqualTo(false)

    setExecutableFlagInIndex(true)
    assertThat(readFilePermissions()).isEqualTo(true)
  }

  @Test
  fun `test object types`() {
    assertObjectType(null, "0".repeat(40))
    assertObjectType(GitObjectType.COMMIT, "HEAD")
    assertObjectType(GitObjectType.BLOB, "HEAD:$FILE")
    assertObjectType(GitObjectType.TREE, "HEAD:")
  }

  private fun assertObjectType(expected: GitObjectType?, obj: String) {
    assertThat(Git.getInstance().getObjectTypeEnum(repository, obj)).isEqualTo(expected)
  }

  private fun readFileContent(): String {
    val stagedFile = GitIndexUtil.listStaged(repository, FILE.path)
    val bytes = GitIndexUtil.read(repository, stagedFile!!.blobHash)
    return String(bytes, Charsets.UTF_8)
  }

  private fun writeFileContent(path: String, content: String, executable: Boolean = false) {
    val bytes = content.toByteArray(Charsets.UTF_8)
    GitIndexUtil.write(repository, path.path, bytes, executable)
  }

  private fun readFilePermissions() = GitIndexUtil.listStaged(repository, FILE.path)!!.isExecutable

  private val String.path: FilePath get() = VcsUtil.getFilePath(repository.root, this)

  private fun setExecutableFlagInIndex(executable: Boolean): Unit = with(context) {
    val mode = if (executable) "+x" else "-x"
    git("update-index --chmod=$mode '$FILE'")
  }
}
