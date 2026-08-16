// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log

import com.intellij.openapi.vcs.changes.patch.BlobIndexUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.HeavyPlatformTestCase.setFileText
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.test.updateChangeListManager
import git4idea.test.GitSingleRepoContext
import git4idea.test.add
import git4idea.test.addCommit
import git4idea.test.createFileStructure
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Paths

private const val A_FILE = "a.txt"

@TestApplication
internal class GitSha1Test {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @BeforeEach
  fun setUp() {
    with(context) {
      createFileStructure(projectRoot, A_FILE)
      addCommit("initial")
    }
  }

  @Test
  fun `test sha for add`(): Unit = with(context) {
    cd(projectPath)
    val newFile = "newFile.txt"
    val path = Paths.get(projectPath, newFile)
    touch(newFile, "Hello World!")
    add(newFile)

    VfsUtil.markDirtyAndRefresh(false, false, false, VfsUtil.findFile(path, true))
    checkSha1ForSingleChange(BlobIndexUtil.NOT_COMMITTED_HASH, git("hash-object $newFile"))
  }

  @Test
  fun `test sha for del`(): Unit = with(context) {
    cd(projectPath)
    val path = Paths.get(projectPath, A_FILE)
    val expectedBefore = git("hash-object $path")
    git("rm $path")
    checkSha1ForSingleChange(expectedBefore, BlobIndexUtil.NOT_COMMITTED_HASH)
  }

  @Test
  fun `test sha for modified`(): Unit = with(context) {
    cd(projectPath)
    val path = Paths.get(projectPath, A_FILE)
    val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)!!
    val expectedBefore = git("hash-object $path")
    setFileText(virtualFile, "echo content\n with line separator")

    VfsUtil.markDirtyAndRefresh(false, false, false, VfsUtil.findFile(path, true))
    checkSha1ForSingleChange(expectedBefore, git("hash-object $path"))
  }

  private fun GitSingleRepoContext.checkSha1ForSingleChange(expectedBefore: String?, expectedAfter: String?) {
    updateChangeListManager()
    val changes = changeListManager.allChanges
    assertThat(changes).hasSize(1)
    val beforeAfterSha1 = BlobIndexUtil.getBeforeAfterSha1(changes.first())
    assertThat(beforeAfterSha1.first).isEqualTo(expectedBefore)
    assertThat(beforeAfterSha1.second).isEqualTo(expectedAfter)
  }
}
