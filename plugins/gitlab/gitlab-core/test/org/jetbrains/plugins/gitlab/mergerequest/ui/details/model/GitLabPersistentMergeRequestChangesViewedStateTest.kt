// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import kotlinx.serialization.json.Json
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabPersistentMergeRequestChangesViewedState.MRId
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabPersistentMergeRequestChangesViewedState.MRViewedState
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabPersistentMergeRequestChangesViewedState.State
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GitLabPersistentMergeRequestChangesViewedStateTest {
  // Mirrors the platform state-storage Json config (see KotlinxSerializationBinding.__json).
  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun `legacy state persisted before serverPath and projectId were introduced deserializes without failing`() {
    // Before IJPL-81282/IJPL-82568, MRId stored a single `project` field instead of serverPath + projectId.
    // Such workspace state must still deserialize (with blank identity fields) instead of crashing component init
    // with a MissingFieldException.
    val legacy = """
      {
        "states": [
          {
            "id": {
              "project": {
                "serverPath": { "uri": "https://gitlab.com" },
                "projectPath": { "owner": "owner", "name": "project" }
              },
              "iid": "5"
            },
            "lastUpdated": 123,
            "viewedFiles": { "file.kt": "abc123" }
          }
        ]
      }
    """.trimIndent()

    val state = json.decodeFromString<State>(legacy)

    assertEquals(1, state.states.size)
    val id = state.states.single().id
    assertTrue(id.serverPath.uri.isEmpty()) { "serverPath is not derivable from legacy data" }
    assertTrue(id.projectId.isEmpty()) { "projectId is not derivable from legacy data" }
    assertEquals("5", id.iid)
  }

  @Test
  fun `current format round-trips`() {
    val original = State(listOf(
      MRViewedState(
        id = MRId(GitLabServerPath("https://gitlab.com"), projectId = "42", iid = "5"),
        lastUpdated = 123,
        viewedFiles = mapOf("file.kt" to "abc123")
      )
    ))

    val restored = json.decodeFromString<State>(json.encodeToString(original))

    assertEquals(original.states, restored.states)
  }
}
