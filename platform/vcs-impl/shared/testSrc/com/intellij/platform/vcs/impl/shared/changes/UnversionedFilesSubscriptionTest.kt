// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.changes

import com.intellij.platform.project.ProjectId
import com.intellij.platform.vcs.impl.shared.rpc.ChangeListsApi
import com.intellij.platform.vcs.impl.shared.rpc.FilePathDto
import com.intellij.platform.vcs.impl.shared.rpc.UnversionedFilesDto
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

internal class UnversionedFilesSubscriptionTest {
  private val api = mock<ChangeListsApi>()
  private val projectId = ProjectId.deserializeFromString("unversioned-files-test")

  @Test
  fun `a changed limit restarts the subscription with the new value`(): Unit = timeoutRunBlocking {
    val counts = UnversionedFilesDto.Counts(1_500, 0)
    val files = UnversionedFilesDto.Files(listOf(FilePathDto(null, "/project/file.txt", false)))
    val limit = MutableStateFlow(1_000)
    val requestedLimits = mutableListOf<Int>()
    var activeSubscriptions = 0
    suspend fun stub(requestedLimit: Int, dto: UnversionedFilesDto) {
      `when`(api.getUnversionedFiles(projectId, requestedLimit)).thenAnswer {
        requestedLimits.add(requestedLimit)
        flow {
          activeSubscriptions++
          try {
            emitAll(MutableStateFlow(dto))
          }
          finally {
            activeSubscriptions--
          }
        }
      }
    }
    stub(1_000, counts)
    stub(2_000, files)
    val emissions = Channel<UnversionedFilesDto>(Channel.UNLIMITED)
    val collector = launch {
      observeUnversionedFiles(limit, api, projectId).collect { emissions.send(it) }
    }
    try {
      assertEquals(counts, emissions.receive())

      limit.value = 2_000
      assertEquals(files, emissions.receive())
      assertEquals(listOf(1_000, 2_000), requestedLimits)
      assertEquals(1, activeSubscriptions)
    }
    finally {
      collector.cancelAndJoin()
      emissions.cancel()
    }
    assertEquals(0, activeSubscriptions)
  }
}
