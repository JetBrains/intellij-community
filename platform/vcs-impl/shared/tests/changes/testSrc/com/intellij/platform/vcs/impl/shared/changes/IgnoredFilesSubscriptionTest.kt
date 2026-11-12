// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.changes

import com.intellij.platform.project.ProjectId
import com.intellij.platform.vcs.impl.shared.rpc.ChangeListsApi
import com.intellij.platform.vcs.impl.shared.rpc.FilePathDto
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`

internal class IgnoredFilesSubscriptionTest {
  private val settings = ChangesViewSettingsImpl()
  private val api = mock<ChangeListsApi>()
  private val projectId = ProjectId.deserializeFromString("ignored-files-test")

  @Test
  fun `hidden ignored files do not start an RPC subscription`(): Unit = timeoutRunBlocking {
    assertEquals(emptyList<FilePathDto>(), observeIgnoredFiles(settings, api, projectId).first())
    verifyNoInteractions(api)
  }

  @Test
  fun `an enabled saved setting immediately fetches ignored files`(): Unit = timeoutRunBlocking {
    val files = listOf(path("ignored.txt"))
    settings.loadState(ChangesViewSettingsImpl.State().apply { showIgnored = true })
    `when`(api.getIgnoredFiles(projectId)).thenReturn(MutableStateFlow(files))

    assertEquals(files, observeIgnoredFiles(settings, api, projectId).first())
  }

  @Test
  fun `the toggle cancels collection and fetches current files when enabled again`(): Unit = timeoutRunBlocking {
    val original = listOf(path("original.txt"))
    val updated = listOf(path("updated.txt"))
    val latest = listOf(path("latest.txt"))
    val backendFiles = MutableStateFlow(original)
    var activeSubscriptions = 0
    var requests = 0
    `when`(api.getIgnoredFiles(projectId)).thenAnswer {
      requests++
      flow {
        activeSubscriptions++
        try {
          emitAll(backendFiles)
        }
        finally {
          activeSubscriptions--
        }
      }
    }
    val emissions = Channel<List<FilePathDto>>(Channel.UNLIMITED)
    val collector = launch {
      observeIgnoredFiles(settings, api, projectId).collect { emissions.send(it) }
    }
    try {
      assertEquals(emptyList<FilePathDto>(), emissions.receive())
      assertEquals(0, requests)

      settings.showIgnored = true
      assertEquals(original, emissions.receive())
      assertEquals(1, activeSubscriptions)
      assertEquals(1, requests)

      backendFiles.value = updated
      assertEquals(updated, emissions.receive())

      settings.showIgnored = false
      assertEquals(emptyList<FilePathDto>(), emissions.receive())
      assertEquals(0, activeSubscriptions)

      backendFiles.value = latest
      settings.showIgnored = true
      assertEquals(latest, emissions.receive())
      assertEquals(1, activeSubscriptions)
      assertEquals(2, requests)
    }
    finally {
      collector.cancelAndJoin()
      emissions.cancel()
    }
    assertEquals(0, activeSubscriptions)
  }

  @Test
  fun `loaded settings and a reset update the subscription`(): Unit = timeoutRunBlocking {
    val files = listOf(path("ignored.txt"))
    `when`(api.getIgnoredFiles(projectId)).thenReturn(MutableStateFlow(files))
    val emissions = Channel<List<FilePathDto>>(Channel.UNLIMITED)
    val collector = launch {
      observeIgnoredFiles(settings, api, projectId).collect { emissions.send(it) }
    }
    try {
      assertEquals(emptyList<FilePathDto>(), emissions.receive())

      settings.loadState(ChangesViewSettingsImpl.State().apply { showIgnored = true })
      assertEquals(files, emissions.receive())

      settings.noStateLoaded()
      assertEquals(emptyList<FilePathDto>(), emissions.receive())
      assertFalse(settings.showIgnored)
    }
    finally {
      collector.cancelAndJoin()
      emissions.cancel()
    }
  }

  private fun path(name: String): FilePathDto = FilePathDto(null, "/project/$name", false)
}
