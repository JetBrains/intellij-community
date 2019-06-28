// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vcs.Executor.*
import com.intellij.openapi.vcs.FilePath
import com.intellij.vcsUtil.VcsUtil
import git4idea.repo.GitRepository
import git4idea.test.GitPlatformTest
import git4idea.test.git
import org.junit.Assume

class GitIndexTest : GitPlatformTest() {
  private val FILE = "file.txt"

  private lateinit var repository: GitRepository

  public override fun setUp() {
    super.setUp()

    repository = createRepository(projectPath)

    cd(projectPath)

    touch(FILE, "initial")
    git("add .")
    git("commit -m initial")
  }

  fun `test read staged file`() {
    assertEquals("initial", readFileContent(FILE))

    overwrite(FILE, "modified")
    assertEquals("initial", readFileContent(FILE))

    git("add .")
    assertEquals("modified", readFileContent(FILE))

    overwrite(FILE, "modi\nfied")
    git("add .")
    assertEquals("modi\nfied", readFileContent(FILE))
  }

  fun `test write staged file`() {
    assertEquals("initial", readFileContent(FILE))

    writeFileContent(FILE, "modified")
    assertEquals("modified", readFileContent(FILE))

    overwrite(FILE, "modi\nfied")
    assertEquals("modified", readFileContent(FILE))
  }

  fun `test read permissions1`() {
    Assume.assumeFalse(SystemInfo.isWindows) // Can't set executable flag on windows

    assertEquals(false, readFilePermissions())

    assertTrue(FILE.path.ioFile.setExecutable(true))
    git("add .")
    assertEquals(true, readFilePermissions())

    assertTrue(FILE.path.ioFile.setExecutable(false))
    assertEquals(true, readFilePermissions())

    git("add .")
    assertEquals(false, readFilePermissions())
  }

  fun `test read permissions2`() {
    assertEquals(false, readFilePermissions())

    setExecutableFlagInIndex(FILE, true)
    assertEquals(true, readFilePermissions())

    setExecutableFlagInIndex(FILE, false)
    assertEquals(false, readFilePermissions())
  }

  fun `test write permissions`() {
    assertEquals(false, readFilePermissions())

    writeFileContent(FILE, "modified", true)
    assertEquals(true, readFilePermissions())

    writeFileContent(FILE, "modified", false)
    assertEquals(false, readFilePermissions())

    setExecutableFlagInIndex(FILE, true)
    assertEquals(true, readFilePermissions())
  }

  private fun readFileContent(path: String): String {
    val stagedFile = GitIndexUtil.listStaged(repository, path.path)
    val bytes = GitIndexUtil.read(repository, stagedFile!!.blobHash)
    return String(bytes, Charsets.UTF_8)
  }

  private fun writeFileContent(path: String, content: String, executable: Boolean = false) {
    val bytes = content.toByteArray(Charsets.UTF_8)
    GitIndexUtil.write(repository, path.path, bytes, executable)
  }

  private fun readFilePermissions() = GitIndexUtil.listStaged(repository, FILE.path)!!.isExecutable

  private val String.path: FilePath get() = VcsUtil.getFilePath(repository.root, this)

  private fun setExecutableFlagInIndex(path: String, executable: Boolean) {
    val mode = if (executable) "+x" else "-x"
    git("update-index --chmod=$mode '$path'")
  }
}
