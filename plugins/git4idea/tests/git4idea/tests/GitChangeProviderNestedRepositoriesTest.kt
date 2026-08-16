// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.tests

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.EnableTracingFor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.test.refresh
import com.intellij.vcsUtil.VcsUtil
import git4idea.test.GitPlatformTestContext
import git4idea.test.addCommit
import git4idea.test.cd
import git4idea.test.createFileStructure
import git4idea.test.createRepository
import git4idea.test.createSubRepository
import git4idea.test.git
import git4idea.test.gitPlatformContextFixture
import git4idea.test.updateUntrackedFiles
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

@TestApplication
@EnableTracingFor(categories = ["#com.intellij.openapi.vcs.changes", "#GitStatus"])
internal class GitChangeProviderNestedRepositoriesTest {
  private val contextFixture = gitPlatformContextFixture()
  private val context: GitPlatformTestContext get() = contextFixture.get()

  private lateinit var dirtyScopeManager: VcsDirtyScopeManager

  @BeforeEach
  fun setUp() {
    dirtyScopeManager = VcsDirtyScopeManager.getInstance(context.project)
  }

  // IDEA-149060
  @Test
  fun `test changes in 3-level nested root`(): Unit = with(context) {
    // 1. prepare roots and files
    val repo = createRepository(project, projectPath)
    val childRepo = repo.createSubRepository("child")
    val grandChildRepo = childRepo.createSubRepository("grand")

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
    assertThat(changeListManager.allChanges).hasSize(4)
  }

  @Test
  fun `test new rename forcing old file path refresh`(): Unit = with(context) {
    // 1. prepare roots and files
    val repo = createRepository(project, projectPath)
    cd(repo)

    touch("a.txt", "some file content")
    repo.addCommit("committed file structure")

    rm("a.txt")
    touch("b.txt", "some file content")

    dirtyScopeManager.markEverythingDirty()
    changeListManager.ensureUpToDate()
    updateUntrackedFiles(repo)

    assertThat(changeListManager.allChanges).hasSize(1)
    assertFileStatus("a.txt", FileStatus.DELETED)
    assertFileStatus("b.txt", FileStatus.UNKNOWN)

    git("add b.txt")

    dirtyScopeManager.fileDirty(getFilePath("b.txt"))
    changeListManager.ensureUpToDate()

    assertThat(changeListManager.allChanges).hasSize(2)
    assertFileStatus("a.txt", FileStatus.DELETED)
    assertFileStatus("b.txt", FileStatus.ADDED)

    git("add a.txt")

    dirtyScopeManager.fileDirty(getFilePath("a.txt"))
    changeListManager.ensureUpToDate()

    assertThat(changeListManager.allChanges).hasSize(1)
    assertFileStatus("b.txt", FileStatus.MODIFIED)
  }

  @Test
  fun `test marking root dirty`(): Unit = with(context) {
    val repo = createRepository(project, projectPath)
    val subrepo = repo.createSubRepository("subrepo")

    createFileStructure(repo.root, "a.txt")
    createFileStructure(subrepo.root, "sub.txt")
    repo.addCommit("commit in repo")
    subrepo.addCommit("commit in subrepo")

    val repoPath = VcsUtil.getFilePath(repo.root)
    val repoFilePath = VcsUtil.getFilePath(repo.root, "a.txt")

    val subRepoPath = VcsUtil.getFilePath(subrepo.root)
    val subRepoFilePath = VcsUtil.getFilePath(subrepo.root, "sub.txt")

    changeListManager.ensureUpToDate()
    dirtyScopeManager.rootDirty(repo.root)

    var dirtyFiles = dirtyScopeManager.whatFilesDirty(listOf(repoPath, repoFilePath, subRepoPath, subRepoFilePath))
    assertThat(dirtyFiles).contains(repoPath, repoFilePath)
    assertThat(dirtyFiles).doesNotContain(subRepoPath, subRepoFilePath)

    changeListManager.ensureUpToDate()
    dirtyScopeManager.dirDirtyRecursively(repo.root)

    dirtyFiles = dirtyScopeManager.whatFilesDirty(listOf(repoPath, repoFilePath, subRepoPath, subRepoFilePath))
    assertThat(dirtyFiles).contains(repoPath, repoFilePath, subRepoPath, subRepoFilePath)
  }

  private fun GitPlatformTestContext.assertFileStatus(relativePath: String, fileStatus: FileStatus) {
    if (fileStatus == FileStatus.UNKNOWN) {
      val vf = getVirtualFile(relativePath)
      assertThat(changeListManager.isUnversioned(vf)).describedAs("$vf is not known as unversioned").isTrue()
    }
    else {
      val change = changeListManager.getChange(getFilePath(relativePath))
      assertThat(change?.fileStatus ?: FileStatus.NOT_CHANGED).isEqualTo(fileStatus)
    }
  }

  private fun GitPlatformTestContext.getVirtualFile(relativePath: String): VirtualFile {
    return VfsUtil.findFileByIoFile(File(projectPath, relativePath), true)!!
  }

  private fun GitPlatformTestContext.getFilePath(relativePath: String): FilePath {
    return VcsUtil.getFilePath(File(projectPath, relativePath))
  }
}
