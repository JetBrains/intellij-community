/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.tests

import com.intellij.openapi.vcs.Executor.overwrite
import com.intellij.openapi.vcs.Executor.touch
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import git4idea.repo.GitRepository
import git4idea.test.*
import java.io.File

class GitChangeProviderNestedRepositoriesTest : GitPlatformTest() {
  private lateinit var dirtyScopeManager: VcsDirtyScopeManager

  @Throws(Exception::class)
  public override fun setUp() {
    super.setUp()
    dirtyScopeManager = VcsDirtyScopeManager.getInstance(project)
  }

  override fun getDebugLogCategories() = super.getDebugLogCategories().plus(listOf("#com.intellij.openapi.vcs.changes", "#GitStatus"))

  // IDEA-149060
  fun `test changes in 3-level nested root`() {
    // 1. prepare roots and files
    val repo = createRepository(project, projectPath)
    val childRepo = createSubRoot(projectPath, "child")
    val grandChildRepo = createSubRoot(childRepo.root.path, "grand")

    createFileStructure(repo.root, "a.txt")
    createFileStructure(childRepo.root, "in1.txt", "in2.txt", "grand/inin1.txt", "grand/inin2.txt")
    repo.addCommit("committed file structure")
    childRepo.addCommit("committed file structure")
    grandChildRepo.addCommit("committed file structure")
    refresh()

    // 2. make changes and make sure they are recognized
    cd(repo)
    overwrite("a.txt", "321")
    overwrite("child/in1.txt", "321")
    overwrite("child/in2.txt", "321")
    overwrite("child/grand/inin1.txt", "321")

    dirtyScopeManager.markEverythingDirty()
    changeListManager.ensureUpToDate()

    assertFileStatus("a.txt", FileStatus.MODIFIED)
    assertFileStatus("child/in1.txt", FileStatus.MODIFIED)
    assertFileStatus("child/in2.txt", FileStatus.MODIFIED)
    assertFileStatus("child/grand/inin1.txt", FileStatus.MODIFIED)

    // refresh parent root recursively
    dirtyScopeManager.filePathsDirty(listOf(getFilePath("child/in1.txt")), listOf(VcsUtil.getFilePath(repo.root)))
    changeListManager.ensureUpToDate()

    assertFileStatus("a.txt", FileStatus.MODIFIED)
    assertFileStatus("child/in1.txt", FileStatus.MODIFIED)
    assertFileStatus("child/in2.txt", FileStatus.MODIFIED)
    assertFileStatus("child/grand/inin1.txt", FileStatus.MODIFIED)
    assertEquals(4, changeListManager.allChanges.size)
  }

  private fun createSubRoot(parent: String, name: String) : GitRepository {
    val childRoot = File(parent, name)
    assertTrue(childRoot.mkdir())
    val repo = createRepository(project, childRoot.path)
    cd(repo)
    touch(".gitignore", name)
    addCommit("gitignore")
    return repo
  }

  private fun assertFileStatus(relativePath: String, fileStatus: FileStatus) {
    val change = changeListManager.getChange(getFilePath(relativePath))
    assertEquals(fileStatus, change?.fileStatus ?: FileStatus.NOT_CHANGED)
  }

  private fun getVirtualFile(relativePath: String): VirtualFile {
    return VfsUtil.findFileByIoFile(File(projectPath, relativePath), true)!!
  }

  private fun getFilePath(relativePath: String): FilePath {
    return VcsUtil.getFilePath(File(projectPath, relativePath))
  }
}
