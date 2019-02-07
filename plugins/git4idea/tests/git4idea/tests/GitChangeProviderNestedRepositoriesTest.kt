// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.tests

import com.intellij.openapi.vcs.Executor.*
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

  fun `test new rename forcing old file path refresh`() {
    // 1. prepare roots and files
    val repo = createRepository(project, projectPath)
    cd(repo)

    touch("a.txt", "some file content")
    repo.addCommit("committed file structure")

    rm("a.txt")
    touch("b.txt", "some file content")

    dirtyScopeManager.markEverythingDirty()
    changeListManager.ensureUpToDate()

    assertEquals(1, changeListManager.allChanges.size)
    assertFileStatus("a.txt", FileStatus.DELETED)
    assertFileStatus("b.txt", FileStatus.UNKNOWN)


    git("add b.txt")

    dirtyScopeManager.fileDirty(getFilePath("b.txt"))
    changeListManager.ensureUpToDate()

    assertEquals(2, changeListManager.allChanges.size)
    assertFileStatus("a.txt", FileStatus.DELETED)
    assertFileStatus("b.txt", FileStatus.ADDED)


    git("add a.txt")

    dirtyScopeManager.fileDirty(getFilePath("a.txt"))
    changeListManager.ensureUpToDate()

    assertEquals(1, changeListManager.allChanges.size)
    assertFileStatus("b.txt", FileStatus.MODIFIED)
  }

  private fun createSubRoot(parent: String, name: String) : GitRepository {
    val childRoot = File(parent, name)
    assertTrue(childRoot.mkdir())
    val repo = createRepository(project, childRoot.path)
    cd(parent)
    touch(".gitignore", name)
    addCommit("gitignore")
    return repo
  }

  private fun assertFileStatus(relativePath: String, fileStatus: FileStatus) {
    if (fileStatus == FileStatus.UNKNOWN) {
      val vf = getVirtualFile(relativePath)
      assertTrue("$vf is not known as unversioned", changeListManager.isUnversioned(vf))
    }
    else {
      val change = changeListManager.getChange(getFilePath(relativePath))
      assertEquals(fileStatus, change?.fileStatus ?: FileStatus.NOT_CHANGED)
    }
  }

  private fun getVirtualFile(relativePath: String): VirtualFile {
    return VfsUtil.findFileByIoFile(File(projectPath, relativePath), true)!!
  }

  private fun getFilePath(relativePath: String): FilePath {
    return VcsUtil.getFilePath(File(projectPath, relativePath))
  }
}
