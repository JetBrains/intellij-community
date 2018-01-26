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
package git4idea.index

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.Executor.*
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.CharsetToolkit
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

    FileUtil.setExecutableAttribute(FILE.path.path, true)
    git("add .")
    assertEquals(true, readFilePermissions())

    FileUtil.setExecutableAttribute(FILE.path.path, false)
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
    val stagedFile = GitIndexUtil.list(repository, path.path)
    val bytes = GitIndexUtil.read(repository, stagedFile!!.blobHash)
    return String(bytes, CharsetToolkit.UTF8_CHARSET)
  }

  private fun writeFileContent(path: String, content: String, executable: Boolean = false) {
    val bytes = content.toByteArray(CharsetToolkit.UTF8_CHARSET)
    GitIndexUtil.write(repository, path.path, bytes, executable)
  }

  private fun readFilePermissions() = GitIndexUtil.list(repository, FILE.path)!!.isExecutable

  private val String.path: FilePath get() = VcsUtil.getFilePath(repository.root, this)

  private fun setExecutableFlagInIndex(path: String, executable: Boolean) {
    val mode = if (executable) "+x" else "-x"
    git("update-index --chmod=$mode '$path'")
  }
}
