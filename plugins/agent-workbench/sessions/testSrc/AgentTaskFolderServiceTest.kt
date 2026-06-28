// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.folders.AgentTaskFolderService
import com.intellij.platform.ai.agent.sessions.core.folders.AgentTaskFolderStatus
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentTaskFolderServiceTest {
  @Test
  fun createFolderNormalizesPathNameAndMetadata() {
    val service = AgentTaskFolderService()

    val folder = service.createFolder(
      path = "/work/project-a/",
      name = "  Authentication rewrite  ",
      metadata = mapOf(" issue " to "IJPL-1", "" to "ignored"),
    )

    assertThat(folder?.path).isEqualTo("/work/project-a")
    assertThat(folder?.name).isEqualTo("Authentication rewrite")
    assertThat(folder?.metadata).containsExactlyEntriesOf(mapOf("issue" to "IJPL-1"))
    assertThat(service.listFolders("/work/project-a")).containsExactly(folder)
  }

  @Test
  fun assignThreadKeepsOneFolderPerThread() {
    val service = AgentTaskFolderService()
    val provider = AgentSessionProvider.from("codex")
    val first = requireNotNull(service.createFolder("/work/project-a", "Research"))
    val second = requireNotNull(service.createFolder("/work/project-a", "Implementation"))

    assertThat(service.assignThread("/work/project-a", provider, "thread-1", first.id)).isTrue()
    assertThat(service.assignThread("/work/project-a", provider, "thread-1", second.id)).isTrue()

    assertThat(service.listFolderThreadAssignments("/work/project-a", first.id)).isEmpty()
    assertThat(service.listFolderThreadAssignments("/work/project-a", second.id).map { it.threadId }).containsExactly("thread-1")
    assertThat(service.getFolderForThread("/work/project-a", provider, "thread-1")?.id).isEqualTo(second.id)
  }

  @Test
  fun doneFolderIsHiddenFromDefaultListAndRejectsNewAssignments() {
    val service = AgentTaskFolderService()
    val provider = AgentSessionProvider.from("codex")
    val folder = requireNotNull(service.createFolder("/work/project-a", "Review"))

    assertThat(service.setFolderStatus("/work/project-a", folder.id, AgentTaskFolderStatus.DONE)).isTrue()

    assertThat(service.listFolders("/work/project-a")).isEmpty()
    assertThat(service.listFolders("/work/project-a", includeDone = true).map { it.id }).containsExactly(folder.id)
    assertThat(service.assignThread("/work/project-a", provider, "thread-1", folder.id)).isFalse()
  }

  @Test
  fun deleteFolderRemovesAssignments() {
    val service = AgentTaskFolderService()
    val provider = AgentSessionProvider.from("codex")
    val folder = requireNotNull(service.createFolder("/work/project-a", "Docs"))
    assertThat(service.assignThread("/work/project-a", provider, "thread-1", folder.id)).isTrue()

    assertThat(service.deleteFolder("/work/project-a", folder.id)).isTrue()

    assertThat(service.listFolders("/work/project-a", includeDone = true)).isEmpty()
    assertThat(service.getFolderForThread("/work/project-a", provider, "thread-1")).isNull()
  }

  @Test
  fun loadStateDropsInvalidAssignmentsAndMetadataKeys() {
    val service = AgentTaskFolderService()
    service.loadState(
      AgentTaskFolderService.TaskFolderState(
        foldersByPath = mapOf(
          "/work/project-a/" to listOf(
            AgentTaskFolderService.FolderState(
              id = " folder-1 ",
              name = " Folder ",
              metadata = mapOf(" issue " to "IJPL-1", " " to "ignored"),
            )
          )
        ),
        assignmentsByPath = mapOf(
          "/work/project-a/" to listOf(
            AgentTaskFolderService.AssignmentState(providerId = "codex", threadId = " thread-1 ", folderId = " folder-1 "),
            AgentTaskFolderService.AssignmentState(providerId = "1invalid", threadId = "thread-2", folderId = "folder-1"),
            AgentTaskFolderService.AssignmentState(providerId = "codex", threadId = "thread-3", folderId = "missing-folder"),
          )
        ),
      )
    )

    val folder = service.listFolders("/work/project-a").single()
    assertThat(folder.id).isEqualTo("folder-1")
    assertThat(folder.name).isEqualTo("Folder")
    assertThat(folder.metadata).containsExactlyEntriesOf(mapOf("issue" to "IJPL-1"))
    assertThat(service.listFolderThreadAssignments("/work/project-a", "folder-1").map { it.threadId }).containsExactly("thread-1")
  }
}
