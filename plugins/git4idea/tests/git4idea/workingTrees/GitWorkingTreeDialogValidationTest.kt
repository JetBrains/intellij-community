// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import git4idea.test.GitPlatformTestContext
import git4idea.test.gitPlatformContextFixture
import git4idea.workingTrees.dialog.GitWorkingTreeDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText

@TestApplication
@RegistryKey("git.enable.working.trees.feature", "true")
internal class GitWorkingTreeDialogValidationTest {
  private val contextFixture = gitPlatformContextFixture()
  private val context: GitPlatformTestContext get() = contextFixture.get()

  @Test
  fun `test parent path validation rejects blank and invalid paths`(): Unit = with(context) {
    assertThat(GitWorkingTreeDialog.validateWorktreeParentPath("")).describedAs("A blank parent path is invalid").isNotNull()
    assertThat(GitWorkingTreeDialog.validateWorktreeParentPath("   ")).describedAs("A blank parent path is invalid").isNotNull()
    val invalidPath = "bad" + Char(0) + "path"
    assertThat(GitWorkingTreeDialog.validateWorktreeParentPath(invalidPath)).describedAs("A path with a NUL char is invalid").isNotNull()
  }

  @Test
  fun `test parent path validation accepts a well-formed path`(): Unit = with(context) {
    assertThat(GitWorkingTreeDialog.validateWorktreeParentPath(testNioRoot.toString())).isNull()
  }

  @Test
  fun `test full path validation rejects an existing non-empty target directory`(): Unit = with(context) {
    val target = testNioRoot.resolve("existing")
    target.createDirectories()
    target.resolve("file.txt").writeText("content")
    assertThat(validatePath(testNioRoot.toString(), "existing")).describedAs("An existing non-empty target must be rejected").isNotNull()
  }

  @Test
  fun `test full path validation rejects a target that is a file`(): Unit = with(context) {
    testNioRoot.resolve("aFile").createFile()
    assertThat(validatePath(testNioRoot.toString(), "aFile")).describedAs("A target that is a regular file must be rejected").isNotNull()
  }

  @Test
  fun `test full path validation accepts a fresh target under an existing parent`(): Unit = with(context) {
    assertThat(validatePath(testNioRoot.toString(), "brandNewWorktree"))
      .describedAs("A non-existent target under a writable parent is valid").isNull()
  }

  private fun validatePath(parentPath: String, dirName: String): String? = runBlocking {
    withContext(Dispatchers.IO) {
      GitWorkingTreeDialog.validateWorktreePath(parentPath, dirName)
    }
  }
}
