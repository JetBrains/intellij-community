// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.vcs.BaseChangeListsTest
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.platform.vcs.impl.shared.commit.EditedCommitDetails
import com.intellij.platform.vcs.impl.shared.rpc.ChangeId
import com.intellij.vcs.changes.ChangesViewChangeIdProvider
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.util.VcsUserUtil

internal class ChangesViewChangeIdCacheTest : BaseChangeListsTest() {
  private lateinit var cache: ChangesViewChangeIdProvider

  override fun setUp() {
    super.setUp()
    cache = ChangesViewChangeIdProvider.getInstance(project)
  }

  fun `test resolve change by id`() {
    addLocalFile(name = "file1.txt", content = "content1", baseContent = "base1")
    addLocalFile(name = "file2.txt", content = "content2", baseContent = "base2")
    refreshCLM()

    val changes = getChanges()
    assertEquals(2, changes.size)

    changes.forEach {
      assertChangeIsResolved(it)
    }
  }

  fun `test resolve change from different changelists`() {
    createChangelist("Test List")
    addLocalFile(name = "file1.txt", content = "content1", baseContent = "base1")
    val file2 = addLocalFile(name = "file2.txt", content = "content2", baseContent = "base2")
    refreshCLM()
    file2.moveAllChangesTo("Test List")

    val changes = getChanges()
    assertEquals(2, changes.size)

    changes.forEach {
      assertChangeIsResolved(it)
    }
  }

  fun `test return null for unknown id`() {
    val change = Change(null,
                        SimpleContentRevision("text", LocalFilePath("q", false), "2"))

    val fakeChange = ChangeListChange(change, "test", "test")
    val nonExistentId = ChangeId.getId(fakeChange)
    val resolvedChange = cache.getChangeListChange(nonExistentId)
    assertNull(resolvedChange)
  }

  fun `test changes from edited commit presentation`() {
    val file1Path = "file1.txt".toFilePath
    val file2Path = "file2.txt".toFilePath
    val change1 = Change(
      SimpleContentRevision("base1", file1Path, "rev1"),
      SimpleContentRevision("content1", file1Path, "rev2")
    )
    val change2 = Change(
      SimpleContentRevision("base2", file2Path, "rev1"),
      SimpleContentRevision("content2", file2Path, "rev2")
    )
    
    val editedCommit = EditedCommitDetails(
      currentUser = null,
      committer = VcsUserUtil.createUser("John Doe", "john@example.com"),
      author = VcsUserUtil.createUser("Jane Doe", "jane@example.com"),
      commitHash = HashImpl.build("abc123"),
      subject = "Test commit",
      fullMessage = "Test commit\n\nDetailed message",
      changes = listOf(change1, change2)
    )

    ChangesViewWorkflowManager.getInstance(project).setEditedCommit(editedCommit)

    assertChangeIsResolved(change1, changeListChange = false)
    assertChangeIsResolved(change2, changeListChange = false)
  }

  private fun assertChangeIsResolved(change: Change, changeListChange: Boolean = true) {
    val resolvedChange = if (changeListChange) {
      cache.getChangeListChange(ChangeId.getId(change))
    } else {
      cache.getEditedCommitDetailsChange(ChangeId.getId(change))
    }
    assertSame(change, resolvedChange)
  }

  // Accessing change lists populates cache
  private fun getChanges(): List<Change> =
    ChangeListManager.getInstance(project).changeLists.flatMap { it.changes }
}