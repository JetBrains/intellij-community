// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vcs.Executor
import com.intellij.vcsUtil.VcsUtil
import git4idea.index.vfs.GitIndexFileSystemRefresher
import git4idea.test.GitSingleRepoTest
import junit.framework.TestCase

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
class GitStageContentStalenessTest : GitSingleRepoTest() {
  private val refresher get() = project.service<GitIndexFileSystemRefresher>()

  fun `test staged document reloads when the index changes`() {
    val filePath = commitFile("original")
    val indexFile = refresher.createFile(projectRoot, filePath)!!
    val document = runReadAction { FileDocumentManager.getInstance().getDocument(indexFile)!! }
    TestCase.assertEquals("original", document.text)

    // Advance the index out-of-band (as a checkout would).
    Executor.overwrite("file.txt", "updated")
    git("add .")

    refresher.refresh { it.filePath == filePath }
    waitForRefresh { runReadAction { document.text } == "updated" }

    TestCase.assertEquals("updated", runReadAction { document.text })
  }

  fun `test refresh does not discard pending in-memory stage edits`() {
    val filePath = commitFile("original")
    val indexFile = refresher.createFile(projectRoot, filePath)!!
    val document = runReadAction { FileDocumentManager.getInstance().getDocument(indexFile)!! }

    // Pending, unsaved in-memory stage edit (as produced by staging a hunk).
    invokeAndWaitIfNeeded { runWriteAction { document.setText("mine") } }
    val originalHash = indexFile.data?.hash

    Executor.overwrite("file.txt", "updated")
    git("add .")

    refresher.refresh { it.filePath == filePath }
    // The metadata advances even when the reload is skipped, so this signals the refresh has been applied.
    waitForRefresh { indexFile.data?.hash.let { it != null && it != originalHash } }

    TestCase.assertEquals("mine", runReadAction { document.text })
  }

  private fun commitFile(content: String) = run {
    Executor.touch("file.txt", content)
    git("add .")
    git("commit -m file")
    refresh()
    VcsUtil.getFilePath(projectRoot.findChild("file.txt")!!)
  }

  private fun waitForRefresh(timeoutMs: Long = 30_000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!condition()) {
      if (System.currentTimeMillis() > deadline) TestCase.fail("Timed out waiting for the index refresh")
      Thread.sleep(20)
    }
  }
}
