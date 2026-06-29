// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.task.folders

import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.io.Ksuid
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
    assertThat(Ksuid.isValid(folder?.id)).isTrue()
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

    assertThat(service.listFolderThreadAssignments(first.id)).isEmpty()
    assertThat(service.listFolderThreadAssignments(second.id).map { it.threadId }).containsExactly("thread-1")
    assertThat(service.getFolderForThread("/work/project-a", provider, "thread-1")?.id).isEqualTo(second.id)
  }

  @Test
  fun doneFolderIsHiddenFromDefaultListAndRejectsNewAssignments() {
    val service = AgentTaskFolderService()
    val provider = AgentSessionProvider.from("codex")
    val folder = requireNotNull(service.createFolder("/work/project-a", "Review"))

    assertThat(service.setFolderStatus(folder.id, AgentTaskFolderStatus.DONE)).isTrue()

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

    assertThat(service.deleteFolder(folder.id)).isTrue()

    assertThat(service.listFolders("/work/project-a", includeDone = true)).isEmpty()
    assertThat(service.getFolderForThread("/work/project-a", provider, "thread-1")).isNull()
  }

  @Test
  fun loadStateDropsInvalidAssignmentsAndMetadataKeys() {
    val service = AgentTaskFolderService()
    service.loadState(
      AgentTaskFolderService.TaskFolderState(
        foldersById = mapOf(
          " folder1 " to AgentTaskFolderService.FolderState(
            name = " Folder ",
            metadata = mapOf(" issue " to "IJPL-1", " " to "ignored"),
            paths = listOf("/work/project-a/", " "),
          )
        ),
        assignments = listOf(
          AgentTaskFolderService.AssignmentState(path = "/work/project-a/", providerId = "codex", threadId = " thread-1 ", folderId = " folder1 "),
          AgentTaskFolderService.AssignmentState(path = "/work/project-a/", providerId = "1invalid", threadId = "thread-2", folderId = "folder1"),
          AgentTaskFolderService.AssignmentState(path = "/work/project-a/", providerId = "codex", threadId = "thread-3", folderId = "missing"),
          AgentTaskFolderService.AssignmentState(path = "/work/project-b", providerId = "codex", threadId = "thread-4", folderId = "folder1"),
        ),
      )
    )

    val folder = service.listFolders("/work/project-a").single()
    assertThat(folder.id).isEqualTo("folder1")
    assertThat(folder.name).isEqualTo("Folder")
    assertThat(folder.metadata).containsExactlyEntriesOf(mapOf("issue" to "IJPL-1"))
    assertThat(service.listFolderThreadAssignments("folder1").map { it.threadId }).containsExactly("thread-1")
  }

  @Test
  fun folderMutationsUseGlobalFolderIdAcrossAssociatedPaths() {
    val service = AgentTaskFolderService()
    val provider = AgentSessionProvider.from("codex")
    service.loadState(
      AgentTaskFolderService.TaskFolderState(
        foldersById = mapOf(
          "folder1" to AgentTaskFolderService.FolderState(
            name = "Folder",
            paths = listOf("/work/project-a", "/work/project-b"),
          )
        ),
      )
    )

    assertThat(service.renameFolder("folder1", "Renamed")).isTrue()
    assertThat(service.setMetadata("folder1", "issue", "IJPL-1")).isTrue()
    assertThat(service.assignThread("/work/project-a", provider, "thread-a", "folder1")).isTrue()
    assertThat(service.assignThread("/work/project-b", provider, "thread-b", "folder1")).isTrue()

    assertThat(service.listFolders("/work/project-a").single().name).isEqualTo("Renamed")
    assertThat(service.listFolders("/work/project-b").single().metadata).containsEntry("issue", "IJPL-1")
    assertThat(service.listFolderThreadAssignments("folder1").map { it.threadId }).containsExactly("thread-a", "thread-b")

    assertThat(service.deleteFolder("folder1")).isTrue()

    assertThat(service.listFolders("/work/project-a", includeDone = true)).isEmpty()
    assertThat(service.listFolders("/work/project-b", includeDone = true)).isEmpty()
    assertThat(service.getFolderForThread("/work/project-a", provider, "thread-a")).isNull()
    assertThat(service.getFolderForThread("/work/project-b", provider, "thread-b")).isNull()
  }

  @Test
  fun legacyUuidFolderIdsAreDroppedOnLoad() {
    val service = AgentTaskFolderService()
    service.loadState(
      AgentTaskFolderService.TaskFolderState(
        foldersById = mapOf(
          "550e8400-e29b-41d4-a716-446655440000" to AgentTaskFolderService.FolderState(
            name = "Legacy",
            paths = listOf("/work/project-a"),
          )
        ),
      )
    )

    assertThat(service.listFolders("/work/project-a", includeDone = true)).isEmpty()
  }
}
