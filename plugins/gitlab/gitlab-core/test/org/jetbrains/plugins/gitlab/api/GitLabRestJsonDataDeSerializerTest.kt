// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestShortRestDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestState
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class GitLabRestJsonDataDeSerializerTest {
  data class Primitives(
    val flag: Boolean,
    val count: Int,
  )

  /**
   * The GitLab REST list endpoint has no `mergeable` key, but [GitLabMergeRequestShortRestDTO.mergeable] is a non-nullable `Boolean`.
   * Jackson 3 enables `FAIL_ON_NULL_FOR_PRIMITIVES` by default, which makes the Kotlin module treat that parameter as required.
   * The GitLab mapper must keep the Jackson 2 behavior: an absent primitive gets its default value (IJPL-256217).
   */
  @Test
  fun `merge request list response without mergeable deserializes`() {
    val json = """
      [
        {
          "id": 1,
          "iid": 7,
          "project_id": 40722193,
          "title": "Fix the thing",
          "description": null,
          "state": "opened",
          "merge_status": "can_be_merged",
          "detailed_merge_status": "mergeable",
          "author": {"id": 42, "username": "dev", "name": "Dev", "avatar_url": null, "web_url": "https://gitlab.com/dev"},
          "assignees": [],
          "reviewers": [],
          "labels": ["bug"],
          "created_at": "2026-09-21T10:00:00.000Z",
          "draft": false,
          "web_url": "https://gitlab.com/group/project/-/merge_requests/7",
          "user_notes_count": 3
        }
      ]
    """.trimIndent()

    val list = GitLabRestJsonDataDeSerializer.fromJson(json.reader(), List::class.java, GitLabMergeRequestShortRestDTO::class.java)
    assertNotNull(list)
    assertEquals(1, list!!.size)

    val mr = list.single() as GitLabMergeRequestShortRestDTO
    assertFalse(mr.mergeable)
    assertEquals("7", mr.iid)
    assertEquals(40722193L, mr.projectId)
    assertEquals(GitLabMergeRequestState.OPENED, mr.stateEnum)
    assertEquals(GitLabMergeStatus.CAN_BE_MERGED, mr.mergeStatusEnum)
    assertEquals("dev", mr.author.username)
    assertEquals(listOf("bug"), mr.labels)
    assertEquals(3, mr.userNotesCount)
  }

  @Test
  fun `absent primitives get their default values`() {
    val result = GitLabRestJsonDataDeSerializer.fromJson("{}".reader(), Primitives::class.java)
    assertNotNull(result)
    assertFalse(result!!.flag)
    assertEquals(0, result.count)
  }
}
