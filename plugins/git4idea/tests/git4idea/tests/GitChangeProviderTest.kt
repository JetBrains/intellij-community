// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.tests

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsTestUtil.copyFileInCommand
import com.intellij.openapi.vcs.VcsTestUtil.deleteFileInCommand
import com.intellij.openapi.vcs.VcsTestUtil.editFileInCommand
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.vcs.MockChangeListManagerGate
import com.intellij.testFramework.vcs.MockChangelistBuilder
import com.intellij.util.containers.CollectionFactory
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitVcsDirtyScope
import git4idea.config.GitVersion
import git4idea.status.GitChangeProvider
import git4idea.test.GitSingleRepoContext
import git4idea.test.addCommit
import git4idea.test.createFile
import git4idea.test.createFileStructure
import git4idea.test.gitSingleRepoContextFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import java.io.File

/**
 * Tests GitChangeProvider functionality. Scenario is the same for all tests:
 * 1. Modifies files on disk (creates, edits, deletes, etc.)
 * 2. Manually adds them to a dirty scope.
 * 3. Calls ChangeProvider.getChanges() and checks that the changes are there.
 */
internal abstract class GitChangeProviderTest {
  private val contextFixture = gitSingleRepoContextFixture(makeInitialCommit = false)
  protected val context: GitSingleRepoContext get() = contextFixture.get()

  private lateinit var changeProvider: GitChangeProvider
  protected lateinit var dirtyScope: GitVcsDirtyScope

  protected lateinit var atxt: VirtualFile
  protected lateinit var dir_ctxt: VirtualFile
  protected lateinit var subdir_dtxt: VirtualFile

  @BeforeEach
  fun setUpChangeProvider() {
    with(context) {
      changeProvider = vcs.changeProvider as GitChangeProvider

      createFileStructure(projectRoot, "a.txt", "b.txt", "dir/c.txt", "dir/subdir/d.txt")
      addCommit("initial")

      atxt = getVirtualFile("a.txt")
      dir_ctxt = getVirtualFile("dir/c.txt")
      subdir_dtxt = getVirtualFile("dir/subdir/d.txt")

      dirtyScope = GitVcsDirtyScope(project)

      cd(projectPath)
    }
  }

  private fun GitSingleRepoContext.getVirtualFile(relativePath: String) =
    VfsUtil.findFileByIoFile(File(projectPath, relativePath), true)!!

  /**
   * Checks that the given files have respective statuses in the change list retrieved from the change provider.
   * Pass null in the [fileStatuses] list to indicate that the corresponding file has not changed.
   */
  protected fun GitSingleRepoContext.assertProviderChanges(virtualFiles: List<VirtualFile>, fileStatuses: List<FileStatus?>) {
    assertProviderChangesInPaths(virtualFiles.map { VcsUtil.getFilePath(it) }, fileStatuses)
  }

  protected fun GitSingleRepoContext.assertProviderChangesInPaths(paths: List<FilePath>, fileStatuses: List<FileStatus?>) {
    assertThat(fileStatuses).hasSameSizeAs(paths)
    val result = getProviderChanges()
    for (i in paths.indices) {
      val fp = paths[i]
      val status = fileStatuses[i]
      if (status == null) {
        assertThat(result)
          .describedAs("File [${tos(fp)}] shouldn't be in the changelist, but it was.")
          .doesNotContainKey(fp)
        continue
      }
      assertThat(result)
        .describedAs("File [${tos(fp)}] didn't change. Changes: [${result.values.joinToString(",") { tos(it) }}]")
        .containsKey(fp)
      assertThat(result[fp]!!.fileStatus)
        .describedAs("File statuses don't match for file [${tos(fp)}]")
        .isEqualTo(status)
    }
  }

  protected fun GitSingleRepoContext.assertProviderChanges(virtualFile: VirtualFile, fileStatus: FileStatus?) {
    assertProviderChanges(listOf(virtualFile), listOf(fileStatus))
  }

  protected fun GitSingleRepoContext.assumeWorktreeRenamesSupported() {
    assumeTrue(vcs.version.isLaterOrEqual(GitVersion(2, 17, 0, 0))) {
      "Worktree renames are not supported by git: ${vcs.version}"
    }
  }

  /**
   * It is assumed that only one change for a file has happened.
   */
  private fun GitSingleRepoContext.getProviderChanges(): Map<FilePath, Change> {
    val builder = MockChangelistBuilder()
    changeProvider.getChanges(dirtyScope, builder, EmptyProgressIndicator(), MockChangeListManagerGate(changeListManager))
    val map = CollectionFactory.createCustomHashingStrategyMap<FilePath, Change>(ChangesUtil.CASE_SENSITIVE_FILE_PATH_HASHING_STRATEGY)
    return builder.changes.associateByTo(map) { ChangesUtil.getFilePath(it) }
  }

  protected fun GitSingleRepoContext.create(parent: VirtualFile, name: String): VirtualFile {
    val file = createFile(parent, name, "content" + Math.random())
    dirty(file)
    return file
  }

  protected fun GitSingleRepoContext.edit(file: VirtualFile, content: String) {
    editFileInCommand(project, file, content)
    dirty(file)
  }

  protected fun GitSingleRepoContext.deleteFile(file: VirtualFile) {
    dirty(file)
    deleteFileInCommand(project, file)
  }

  @Suppress("unused") // kept for symmetry with the other file operations
  protected fun GitSingleRepoContext.copy(file: VirtualFile, newParent: VirtualFile): VirtualFile {
    dirty(file)
    val newFile = copyFileInCommand(project, file, newParent, file.name)
    dirty(newFile)
    return newFile
  }

  protected fun dirty(file: VirtualFile?) {
    dirtyScope.addDirtyFile(VcsUtil.getFilePath(file ?: return))
  }

  private fun GitSingleRepoContext.tos(fp: FilePath) = FileUtil.getRelativePath(File(projectPath), fp.ioFile)

  private fun GitSingleRepoContext.tos(change: Change) = when (change.type) {
    Change.Type.NEW -> "A: " + tos(change.afterRevision)!!
    Change.Type.DELETED -> "D: " + tos(change.beforeRevision)!!
    Change.Type.MOVED -> "M: " + tos(change.beforeRevision) + " -> " + tos(change.afterRevision)
    Change.Type.MODIFICATION -> "M: " + tos(change.afterRevision)!!
    else -> "~: " + tos(change.beforeRevision) + " -> " + tos(change.afterRevision)
  }

  private fun GitSingleRepoContext.tos(revision: ContentRevision?) = tos(revision!!.file)
}
