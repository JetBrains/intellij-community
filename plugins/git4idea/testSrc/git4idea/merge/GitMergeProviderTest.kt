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
package git4idea.merge

import com.intellij.openapi.vcs.Executor.overwrite
import com.intellij.openapi.vcs.Executor.rm
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.merge.MergeData
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.LineSeparator
import com.intellij.vcsUtil.VcsFileUtil
import git4idea.branch.GitRebaseParams
import git4idea.repo.GitRepository
import git4idea.test.cd
import git4idea.test.createRepository
import git4idea.test.git
import git4idea.test.gitPlatformContextFixture
import git4idea.test.GitPlatformTestContext
import git4idea.test.gitUsingOrtMergeAlg
import git4idea.test.mv
import git4idea.test.TestGitImpl
import git4idea.util.GitFileUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.EnumSource
import java.io.File
import java.io.FileNotFoundException

@TestApplication
@ParameterizedClass(name = "operation = {0}")
@EnumSource(GitMergeProviderTest.Operation::class)
class GitMergeProviderTest(private val operation: Operation) {
  private val fixture = gitPlatformContextFixture()
  private val context: GitPlatformTestContext get() = fixture.get()

  enum class Operation { MERGE, REBASE, INTERACTIVE_REBASE }

  @Suppress("SameParameterValue")
  private fun `invoke conflicting operation`(branchCurrent: String, branchLast: String) {
    when (operation) {
      Operation.MERGE -> `invoke merge`(branchCurrent, branchLast)
      Operation.REBASE -> `invoke rebase`(branchCurrent, branchLast)
      Operation.INTERACTIVE_REBASE -> `invoke rebase interactive`(branchCurrent, branchLast)
    }
  }

  companion object {
    private const val FILE = "file.txt"
    private const val FILE_RENAME = "file_rename.txt"
    private const val FILE_CONTENT =
      "\nThis\nis\nsome\ncontent\nto\nmake\ngit\ntreat\nthis\nfile\nas\nrename\nrather\nthan\ninsertion\nand\ndeletion\n"
  }

  private lateinit var repository: GitRepository

  @BeforeEach
  fun setUp(): Unit = with(context) {
    repository = createRepository(project, projectPath)

    cd(projectRoot)
    git("commit --allow-empty -m initial")

    touch(FILE, "original$FILE_CONTENT")
    git("add .")
    git("commit -m Base")
  }

  @Suppress("SameParameterValue")
  private fun `assert all revisions and paths loaded`(branchCurrent: String, branchLast: String) {
    `assert all revisions loaded`(branchCurrent, branchLast)

    `assert revision GOOD, path GOOD`(Side.ORIGINAL)
    `assert revision GOOD, path GOOD`(Side.LAST)
    `assert revision GOOD, path GOOD`(Side.CURRENT)
  }

  private fun `assert all revisions loaded`(branchCurrent: String, branchLast: String) {
    `assert merge conflict`()
    `assert merge provider consistent`()

    `assert revision`(Side.ORIGINAL, "master")
    `assert revision`(Side.LAST, "branch-$branchLast")
    `assert revision`(Side.CURRENT, "branch-$branchCurrent")
  }

  private fun `invoke merge`(branchCurrent: String, branchLast: String): Unit = with(context) {
    git("checkout branch-$branchCurrent")
    git("merge branch-$branchLast", true)
    repository.update()
  }

  private fun `invoke rebase`(branchCurrent: String, branchLast: String): Unit = with(context) {
    git("checkout branch-$branchCurrent")
    git("rebase branch-$branchLast", true)
    repository.update()
  }

  private fun `invoke rebase interactive`(branchCurrent: String, branchLast: String): Unit = with(context) {
    git("checkout branch-$branchCurrent")
    doRebaseInteractive(branchLast)
    repository.update()
  }

  private fun doRebaseInteractive(onto: String): Unit = with(context) {
    git.setInteractiveRebaseEditor(TestGitImpl.InteractiveRebaseEditor(
      entriesEditor = {
        it.lines().mapIndexed { i, s ->
          if (i != 0) s
          else s.replace("pick", "reword")
        }.joinToString(LineSeparator.getSystemLineSeparator().separatorString)
      },
      plainTextEditor = null
    ))
    val rebaseParams = GitRebaseParams(vcs.version, null, null, "branch-$onto", interactive = true, preserveMerges = false)
    git.rebase(repository, rebaseParams)
  }

  private fun `init branch - change`(branch: String) {
    doInitBranch(branch, {
      overwrite(FILE, "modified: $branch$FILE_CONTENT")
    })
  }

  private fun `init branch - rename`(branch: String, newFileName: String = FILE_RENAME): Unit = with(context) {
    doInitBranch(branch, {
      mv(FILE, newFileName)
    })
  }

  private fun `init branch - change and rename`(branch: String, newFileName: String = FILE_RENAME): Unit = with(context) {
    doInitBranch(branch, {
      overwrite(FILE, "modified: $branch$FILE_CONTENT")
      mv(FILE, newFileName)
    })
  }

  private fun `init branch - delete`(branch: String) {
    doInitBranch(branch, {
      rm(FILE)
    })
  }

  private fun doInitBranch(branch: String, vararg changesToCommit: () -> Unit): Unit = with(context) {
    cd(repository)
    git("checkout master")
    git("checkout -b branch-$branch")

    changesToCommit.forEachIndexed { index, changes ->
      changes()
      git("add -A .")
      git("commit -m $branch-$index")
    }

    git("checkout master")
  }


  private fun `assert merge conflict`(): Unit = with(context) {
    val files = git("ls-files --unmerged -z")
    assertThat(files).isNotEmpty()
  }

  private fun `assert merge provider consistent`(): Unit = with(context) {
    val provider = repository.vcs.mergeProvider
    val files = getConflictedFiles()
    files.forEach {
      val mergeData = provider.loadRevisions(it.toVF())

      Side.entries.forEach { side ->
        val revision = mergeData.revision(side)
        val path = mergeData.filePath(side)
        val content = mergeData.content(side)

        if (revision != null && path != null) {
          val relativePath = VcsFileUtil.relativePath(projectRoot, path)
          val hash = revision.asString()

          val actualContent = GitFileUtils.getFileContent(project, projectRoot, hash, relativePath)
          assertThat(content).isEqualTo(actualContent)
        }
      }
    }
  }

  private fun `assert revision`(side: Side, revision: String): Unit = with(context) {
    val actualHash = getMergeData().revision(side)!!.asString()
    val expectedHash = git("show-ref -s $revision")
    assertThat(actualHash).isEqualTo(expectedHash)
  }

  private fun `assert revision GOOD, path GOOD`(side: Side) {
    val mergeData = getMergeData()
    assertThat(mergeData.revision(side)).isNotNull()
    assertThat(mergeData.filePath(side)).isNotNull()
  }

  private fun `assert revision GOOD, path BAD `(side: Side) {
    val mergeData = getMergeData()
    assertThat(mergeData.revision(side)).isNotNull()
    assertThat(mergeData.filePath(side)).isNull()
  }

  private fun getConflictedFiles(): List<File> {
    val records = context.git("ls-files --unmerged -z").split('\u0000').filter { it.isNotBlank() }
    val files = records.map { it.split('\t').last() }.toSortedSet()
    return files.map { File(context.projectPath, it) }.toList()
  }

  private fun getConflictFile(): File {
    val files = getConflictedFiles()
    assertThat(files).describedAs("More than one conflict: $files").hasSize(1)
    return files.first()
  }

  private fun getMergeData(): MergeData {
    return getMergeData(getConflictFile())
  }

  private fun getMergeData(file: File): MergeData {
    return repository.vcs.mergeProvider.loadRevisions(file.toVF())
  }

  private fun File.toVF(): VirtualFile {
    return StandardFileSystems.local().refreshAndFindFileByPath(this.absolutePath) ?: throw FileNotFoundException(this.path)
  }

  private fun MergeData.content(side: Side): ByteArray = when (side) {
    Side.ORIGINAL -> this.ORIGINAL
    Side.LAST -> this.LAST
    Side.CURRENT -> this.CURRENT
  }

  private fun MergeData.revision(side: Side): VcsRevisionNumber? = when (side) {
    Side.ORIGINAL -> this.ORIGINAL_REVISION_NUMBER
    Side.LAST -> this.LAST_REVISION_NUMBER
    Side.CURRENT -> this.CURRENT_REVISION_NUMBER
  }

  private fun MergeData.filePath(side: Side): FilePath? = when (side) {
    Side.ORIGINAL -> this.ORIGINAL_FILE_PATH
    Side.LAST -> this.LAST_FILE_PATH
    Side.CURRENT -> this.CURRENT_FILE_PATH
  }

  private enum class Side {
    ORIGINAL, LAST, CURRENT;
  }

  @Test
  fun `test merge - change vs change`() {
    `init branch - change`("A")
    `init branch - change`("B")

    `invoke conflicting operation`("A", "B")

    `assert all revisions and paths loaded`("A", "B")
  }

  @Test
  fun `test merge - change vs change and rename`() {
    `init branch - change`("A")
    `init branch - change and rename`("B")

    `invoke conflicting operation`("A", "B")

    `assert all revisions and paths loaded`("A", "B")
  }

  @Test
  fun `test merge - change and rename vs change`() {
    `init branch - change and rename`("A")
    `init branch - change`("B")

    `invoke conflicting operation`("A", "B")

    `assert all revisions and paths loaded`("A", "B")
  }

  @Test
  fun `test merge - change and rename vs change and rename - same renames`() {
    `init branch - change and rename`("A")
    `init branch - change and rename`("B")

    `invoke conflicting operation`("A", "B")

    `assert all revisions and paths loaded`("A", "B")
  }

  @Test
  fun `test change vs deleted`() {
    `init branch - change`("A")
    `init branch - delete`("B")

    `invoke conflicting operation`("A", "B")

    `assert all revisions loaded`("A", "B")
    `assert revision GOOD, path GOOD`(Side.ORIGINAL)
    `assert revision GOOD, path BAD `(Side.LAST)
    `assert revision GOOD, path GOOD`(Side.CURRENT)
  }

  @Test
  fun `test deleted vs change`() {
    `init branch - delete`("A")
    `init branch - change`("B")

    `invoke conflicting operation`("A", "B")

    `assert all revisions loaded`("A", "B")
    `assert revision GOOD, path GOOD`(Side.ORIGINAL)
    `assert revision GOOD, path GOOD`(Side.LAST)
    `assert revision GOOD, path BAD `(Side.CURRENT)
  }

  @Test
  fun `test rename vs deleted`(): Unit = with(context) {
    `init branch - rename`("A")
    `init branch - delete`("B")

    `invoke conflicting operation`("A", "B")

    `assert all revisions loaded`("A", "B")
    if (gitUsingOrtMergeAlg()) {
      `assert revision GOOD, path GOOD`(Side.ORIGINAL)
      `assert revision GOOD, path BAD `(Side.LAST)
      `assert revision GOOD, path GOOD`(Side.CURRENT)
    }
    else {
      `assert revision GOOD, path BAD `(Side.ORIGINAL)
      `assert revision GOOD, path BAD `(Side.LAST)
      `assert revision GOOD, path GOOD`(Side.CURRENT)
    }
  }

  @Test
  fun `test deleted vs rename`(): Unit = with(context) {
    `init branch - delete`("A")
    `init branch - rename`("B")

    `invoke conflicting operation`("A", "B")

    `assert all revisions loaded`("A", "B")
    if (gitUsingOrtMergeAlg()) {
      `assert revision GOOD, path GOOD`(Side.ORIGINAL)
      `assert revision GOOD, path GOOD`(Side.LAST)
      `assert revision GOOD, path BAD `(Side.CURRENT)
    }
    else {
      `assert revision GOOD, path BAD `(Side.ORIGINAL)
      `assert revision GOOD, path GOOD`(Side.LAST)
      `assert revision GOOD, path BAD `(Side.CURRENT)
    }
  }
}
