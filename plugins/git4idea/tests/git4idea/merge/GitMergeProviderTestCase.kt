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

import com.intellij.openapi.vcs.Executor.*
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.merge.MergeData
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.LineSeparator
import com.intellij.vcsUtil.VcsFileUtil
import git4idea.branch.GitRebaseParams
import git4idea.repo.GitRepository
import git4idea.test.*
import git4idea.util.GitFileUtils
import java.io.File
import java.io.FileNotFoundException

abstract class GitMergeProviderTestCase : GitPlatformTest() {
  protected val FILE = "file.txt"
  protected val FILE_RENAME = "file_rename.txt"
  protected val FILE_CONTENT = "\nThis\nis\nsome\ncontent\nto\nmake\ngit\ntreat\nthis\nfile\nas\nrename\nrather\nthan\ninsertion\nand\ndeletion\n"

  protected lateinit var repository: GitRepository

  override fun runInDispatchThread(): Boolean = true

  public override fun setUp() {
    super.setUp()

    repository = createRepository(projectPath)

    cd(projectRoot)
    git("commit --allow-empty -m initial")

    touch(FILE, "original" + FILE_CONTENT)
    git("add .")
    git("commit -m Base")
  }

  protected fun `assert all revisions and paths loaded`(branchCurrent: String, branchLast: String) {
    `assert all revisions loaded`(branchCurrent, branchLast)

    `assert revision GOOD, path GOOD`(Side.ORIGINAL)
    `assert revision GOOD, path GOOD`(Side.LAST)
    `assert revision GOOD, path GOOD`(Side.CURRENT)
  }

  protected fun `assert all revisions loaded`(branchCurrent: String, branchLast: String) {
    `assert merge conflict`()
    `assert merge provider consistent`()

    `assert revision`(Side.ORIGINAL, "master")
    `assert revision`(Side.LAST, "branch-$branchLast")
    `assert revision`(Side.CURRENT, "branch-$branchCurrent")
  }

  protected fun `invoke merge`(branchCurrent: String, branchLast: String) {
    git("checkout branch-$branchCurrent")
    git("merge branch-$branchLast", true)
    repository.update()
  }

  protected fun `invoke rebase`(branchCurrent: String, branchLast: String) {
    git("checkout branch-$branchCurrent")
    git("rebase branch-$branchLast", true)
    repository.update()
  }

  protected fun `invoke rebase interactive`(branchCurrent: String, branchLast: String) {
    git("checkout branch-$branchCurrent")
    doRebaseInteractive(branchLast)
    repository.update()
  }

  //
  // Impl
  //

  private fun doRebaseInteractive(onto: String) {
    git.setInteractiveRebaseEditor (TestGitImpl.InteractiveRebaseEditor({
      it.lines().mapIndexed { i, s ->
        if (i != 0) s
        else s.replace("pick", "reword")
      }.joinToString(LineSeparator.getSystemLineSeparator().separatorString)
    }, null))
    val rebaseParams = GitRebaseParams(null, null, "branch-$onto", true, false)
    git.rebase(repository, rebaseParams)
  }

  protected fun `init branch - change`(branch: String) {
    doInitBranch(branch, {
      overwrite(FILE, "modified: $branch" + FILE_CONTENT)
    })
  }

  protected fun `init branch - rename`(branch: String, newFileName: String = FILE_RENAME) {
    doInitBranch(branch, {
      mv(FILE, newFileName)
    })
  }

  protected fun `init branch - change and rename`(branch: String, newFileName: String = FILE_RENAME) {
    doInitBranch(branch, {
      overwrite(FILE, "modified: $branch" + FILE_CONTENT)
      mv(FILE, newFileName)
    })
  }

  protected fun `init branch - delete`(branch: String) {
    doInitBranch(branch, {
      rm(FILE)
    })
  }

  private fun doInitBranch(branch: String, vararg changesToCommit: () -> Unit) {
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


  protected fun `assert merge conflict`() {
    val files = git("ls-files --unmerged -z")
    assertTrue(files.isNotEmpty())
  }

  protected fun `assert merge provider consistent`() {
    val provider = repository.vcs.mergeProvider
    val files = getConflictedFiles()
    files.forEach {
      val mergeData = provider.loadRevisions(it.toVF())

      Side.values().forEach {
        val revision = mergeData.revision(it)
        val path = mergeData.filePath(it)
        val content = mergeData.content(it)

        if (revision != null && path != null) {
          val relativePath = VcsFileUtil.relativePath(projectRoot, path)
          val hash = revision.asString()

          val actualContent = GitFileUtils.getFileContent(project, projectRoot, hash, relativePath)
          assertOrderedEquals(content, actualContent)
        }
      }
    }
  }

  protected fun `assert revision`(side: Side, revision: String) {
    val actualHash = getMergeData().revision(side)!!.asString()
    val expectedHash = git("show-ref -s $revision")
    assertEquals(expectedHash, actualHash)
  }

  protected fun `assert revision GOOD, path GOOD`(side: Side) {
    val mergeData = getMergeData()
    assertNotNull(mergeData.revision(side))
    assertNotNull(mergeData.filePath(side))
  }

  protected fun `assert revision GOOD, path BAD `(side: Side) {
    val mergeData = getMergeData()
    assertNotNull(mergeData.revision(side))
    assertNull(mergeData.filePath(side))
  }

  private fun getConflictedFiles(): List<File> {
    val records = git("ls-files --unmerged -z").split('\u0000').filter { !it.isBlank() }
    val files = records.map { it.split('\t').last() }.toSortedSet()
    return files.map { File(projectPath, it) }.toList()
  }

  private fun getConflictFile(): File {
    val files = getConflictedFiles()
    if (files.size != 1) fail("More than one conflict: $files")
    return files.first()
  }

  private fun getMergeData(): MergeData {
    return getMergeData(getConflictFile())
  }

  private fun getMergeData(file: File): MergeData {
    return repository.vcs.mergeProvider.loadRevisions(file.toVF())
  }

  private fun File.toVF(): VirtualFile {
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(this) ?: throw FileNotFoundException(this.path)
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

  protected enum class Side {
    ORIGINAL, LAST, CURRENT;
  }
}
