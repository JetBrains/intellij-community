// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vcs.Executor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.test.refresh
import com.intellij.vcsUtil.VcsUtil
import git4idea.index.vfs.GitIndexFileSystemRefresher
import git4idea.test.GitSingleRepoContext
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Regression test for IJPL-249948 ("Stale content in editor diffs").
 *
 * The staged pane of an editor diff is backed by a [git4idea.index.vfs.GitIndexVirtualFile] whose document is loaded
 * from the git index. When the index changes, [GitIndexFileSystemRefresher] refreshes the file — but its content-change
 * events (published on `VFS_CHANGES`) do not reach [FileDocumentManager], which reloads documents from an async listener
 * driven by `VFS_CHANGES_BG`. So the metadata advanced while the cached document kept the previous content, and
 * consumers (the line-status tracker, diffs) showed stale/phantom staged content until IDE restart.
 *
 * The fix makes the refresher reload the affected documents explicitly. These tests change the index out-of-band, run a
 * refresh, and assert the staged document reflects the current index — without discarding pending in-memory stage edits.
 */
@TestApplication
class GitStageContentStalenessTest {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()
  private val refresher get() = context.project.service<GitIndexFileSystemRefresher>()

  @Test
  fun `test staged document reloads when the index changes`(): Unit = with(context) {
    val filePath = commitFile()
    val indexFile = refresher.createFile(projectRoot, filePath)!!
    val document = runReadActionBlocking { FileDocumentManager.getInstance().getDocument(indexFile)!! }
    assertThat(document.text).isEqualTo("original")

    // Advance the index out-of-band (as a checkout would).
    Executor.overwrite("file.txt", "updated")
    git("add .")

    refresher.refresh { it.filePath == filePath }
    waitForRefresh { runReadActionBlocking { document.text } == "updated" }

    assertThat(runReadActionBlocking { document.text }).isEqualTo("updated")
  }

  @Test
  fun `test refresh does not discard pending in-memory stage edits`(): Unit = with(context) {
    val filePath = commitFile()
    val indexFile = refresher.createFile(projectRoot, filePath)!!
    val document = runReadActionBlocking { FileDocumentManager.getInstance().getDocument(indexFile)!! }

    // Pending, unsaved in-memory stage edit (as produced by staging a hunk).
    invokeAndWaitIfNeeded { runWriteAction { document.setText("mine") } }
    val originalHash = indexFile.data?.hash

    Executor.overwrite("file.txt", "updated")
    git("add .")

    refresher.refresh { it.filePath == filePath }
    // The metadata advances even when the reload is skipped, so this signals the refresh has been applied.
    waitForRefresh { indexFile.data?.hash.let { it != null && it != originalHash } }

    assertThat(runReadActionBlocking { document.text }).isEqualTo("mine")
  }

  private fun commitFile() = with(context) {
    Executor.touch("file.txt", "original")
    git("add .")
    git("commit -m file")
    refresh()
    VcsUtil.getFilePath(projectRoot.findChild("file.txt")!!)
  }

  private fun waitForRefresh(timeoutMs: Long = 30_000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!condition()) {
      assertThat(System.currentTimeMillis()).describedAs("Timed out waiting for the index refresh").isLessThanOrEqualTo(deadline)
      Thread.sleep(20)
    }
  }
}
