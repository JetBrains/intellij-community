// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.platform.vcs.impl.shared.rpc.ChangeDto
import com.intellij.platform.vcs.impl.shared.rpc.ChangeId
import com.intellij.platform.vcs.impl.shared.rpc.ChangeListDto
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Verifies that the [ChangeId] a change gets on the frontend (after being reconstructed from [ChangeListDto]) matches
 * the [ChangeId] the backend keys its cache with, for VCSes both with and without partial changelist support.
 *
 * Regression test: previously [ChangeListDto] unconditionally wrapped every change into [ChangeListChange], producing
 * a `ChangeListChangeId`. For a VCS without partial changelist support (e.g. Mercurial, SVN) the backend keeps the
 * plain [Change] and thus keys the cache by a `NonChangeListChangeId`, so the ids did not match, the backend inclusion
 * stayed empty, and the Commit button reported "Select files to commit".
 *
 * @see ChangeListDto.getChangeList (frontend reconstruction / wrapping decision)
 * @see com.intellij.vcs.changes.ChangesViewChangeIdProvider (backend cache, keyed by [ChangeId.getId])
 */
internal class ChangeListDtoChangeIdRoundTripTest : BasePlatformTestCase() {
  fun `test ChangeId matches for VCS without partial changelist support`() {
    assertFrontendChangeIdMatchesBackend(partialChangelistsSupported = false)
  }

  fun `test ChangeId matches for VCS with partial changelist support`() {
    assertFrontendChangeIdMatchesBackend(partialChangelistsSupported = true)
  }

  private fun assertFrontendChangeIdMatchesBackend(partialChangelistsSupported: Boolean) {
    val listName = "Test List"
    val listId = "test-list-id"
    val filePath = LocalFilePath("/root/file.txt", false)
    val change = Change(
      SimpleContentRevision("base", filePath, "1"),
      SimpleContentRevision("content", filePath, "2"),
    )

    // How the backend represents the change and keys the ChangeId cache, see ChangeListWorker.getChangesMapping:
    // it wraps into a ChangeListChange only for VCSes that support partial changelists.
    val backendChange: Change =
      if (partialChangelistsSupported) ChangeListChange(change, listName, listId) else change
    val backendId = ChangeId.getId(backendChange)

    // How the frontend reconstructs the change from the serialized DTO, see ChangeListDto.getChangeList.
    // The backend serializes its own representation of the change (backendChange), so ChangeDto.toDto picks the
    // matching DTO subtype based on whether the change is wrapped into a ChangeListChange.
    val dto = ChangeListDto(
      name = listName,
      comment = null,
      id = listId,
      isDefault = false,
      changes = listOf(ChangeDto.toDto(backendChange)),
    )
    val frontendChange = dto.getChangeList(project).changes.single()
    val frontendId = ChangeId.getId(frontendChange)

    assertEquals(
      "Frontend and backend must compute the same ChangeId (partialChangelistsSupported=$partialChangelistsSupported)",
      backendId, frontendId,
    )
  }
}
