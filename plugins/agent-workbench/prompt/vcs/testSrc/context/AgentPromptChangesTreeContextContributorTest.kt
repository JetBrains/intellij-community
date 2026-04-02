// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.vcs.context

import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncationReason
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.array
import com.intellij.agent.workbench.prompt.core.bool
import com.intellij.agent.workbench.prompt.core.number
import com.intellij.agent.workbench.prompt.core.objOrNull
import com.intellij.agent.workbench.prompt.core.string
import com.intellij.agent.workbench.prompt.vcs.AgentPromptVcsBundle
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeList
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.LocalChangeListImpl
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentPromptChangesTreeContextContributorTest {
  private val contributor = AgentPromptChangesTreeContextContributor()

  @Test
  fun returnsEmptyWhenDataContextIsMissing() {
    val result = contributor.collect(invocationData(dataContext = null))

    assertThat(result).isEmpty()
  }

  @Test
  fun returnsEmptyWhenNoVcsDataKeysPresent() {
    val dataContext = SimpleDataContext.builder().build()

    val result = contributor.collect(invocationData(dataContext))

    assertThat(result).isEmpty()
  }

  @Test
  fun collectsEmptyChangelist() {
    val dataContext = SimpleDataContext.builder()
      .add(VcsDataKeys.CHANGE_LISTS, arrayOf<ChangeList>(emptyChangeList("Fix login bug", isDefault = true)))
      .build()

    val result = contributor.collect(invocationData(dataContext))

    assertThat(result).hasSize(1)
    val item = result.single()
    assertThat(item.rendererId).isEqualTo(AgentPromptContextRendererIds.SNIPPET)
    assertThat(item.title).isEqualTo(AgentPromptVcsBundle.message("context.changes.title"))
    assertThat(item.itemId).isEqualTo("changes.selection")
    assertThat(item.source).isEqualTo("changes")
    assertThat(item.body).contains("Changelist: Fix login bug (active)")
    assertThat(item.body).contains("Changes: none")
    assertThat(item.truncation.reason).isEqualTo(AgentPromptContextTruncationReason.NONE)

    val payload = item.payload.objOrNull()!!
    assertThat(payload.string("kind")).isEqualTo("changelists")
    assertThat(payload.number("selectedCount")).isEqualTo("1")
    assertThat(payload.number("includedCount")).isEqualTo("1")
    val entries = payload.array("entries")!!.map { it.objOrNull()!! }
    assertThat(entries).hasSize(1)
    assertThat(entries[0].string("name")).isEqualTo("Fix login bug")
    assertThat(entries[0].bool("isDefault")).isTrue()
    assertThat(entries[0].number("changeCount")).isEqualTo("0")
  }

  @Test
  fun collectsChangelistWithComment() {
    val dataContext = SimpleDataContext.builder()
      .add(VcsDataKeys.CHANGE_LISTS, arrayOf<ChangeList>(emptyChangeList("My CL", isDefault = false, comment = "Refactoring auth")))
      .build()

    val result = contributor.collect(invocationData(dataContext))

    assertThat(result).hasSize(1)
    val item = result.single()
    assertThat(item.body).contains("Changelist: My CL")
    assertThat(item.body).doesNotContain("(active)")
    assertThat(item.body).contains("Comment: Refactoring auth")

    val entry = item.payload.objOrNull()!!.array("entries")!!.map { it.objOrNull()!! }.single()
    assertThat(entry.bool("isDefault")).isFalse()
    assertThat(entry.string("comment")).isEqualTo("Refactoring auth")
  }

  @Test
  fun collectsChangelistWithFiles() {
    val changes = listOf(
      modification("/project/src/Login.kt"),
      addition("/project/src/Auth.kt"),
      deletion("/project/src/OldAuth.kt"),
    )
    val dataContext = SimpleDataContext.builder()
      .add(VcsDataKeys.CHANGE_LISTS, arrayOf<ChangeList>(changeListWithChanges("Fix", changes, isDefault = true)))
      .build()

    val result = contributor.collect(invocationData(dataContext))

    assertThat(result).hasSize(1)
    val item = result.single()
    assertThat(item.body).contains("Changelist: Fix (active)")
    assertThat(item.body).contains("Changes (3):")
    assertThat(item.body).contains("- modified: /project/src/Login.kt")
    assertThat(item.body).contains("- added: /project/src/Auth.kt")
    assertThat(item.body).contains("- deleted: /project/src/OldAuth.kt")

    val entry = item.payload.objOrNull()!!.array("entries")!!.map { it.objOrNull()!! }.single()
    assertThat(entry.number("changeCount")).isEqualTo("3")
    val payloadChanges = entry.array("changes")!!.map { it.objOrNull()!! }
    assertThat(payloadChanges).hasSize(3)
    val changeTypes = payloadChanges.map { it.string("type") }
    assertThat(changeTypes).containsExactlyInAnyOrder("modified", "added", "deleted")
  }

  @Test
  fun collectsMultipleChangelists() {
    val dataContext = SimpleDataContext.builder()
      .add(VcsDataKeys.CHANGE_LISTS, arrayOf<ChangeList>(
        emptyChangeList("Default", isDefault = true),
        changeListWithChanges("Feature", listOf(modification("/project/f.kt")), isDefault = false),
      ))
      .build()

    val result = contributor.collect(invocationData(dataContext))

    assertThat(result).hasSize(1)
    val item = result.single()
    assertThat(item.body).contains("Changelist: Default (active)")
    assertThat(item.body).contains("Changelist: Feature")
    assertThat(item.body).contains("- modified: /project/f.kt")

    val payload = item.payload.objOrNull()!!
    assertThat(payload.number("selectedCount")).isEqualTo("2")
    assertThat(payload.number("includedCount")).isEqualTo("2")
  }

  @Test
  fun collectsSelectedChangesViaLeadSelection() {
    val changes = arrayOf(
      modification("/project/src/A.kt"),
      addition("/project/src/B.kt"),
    )
    val dataContext = SimpleDataContext.builder()
      .add(VcsDataKeys.CHANGE_LEAD_SELECTION, changes)
      .build()

    val result = contributor.collect(invocationData(dataContext))

    assertThat(result).hasSize(1)
    val item = result.single()
    assertThat(item.body).contains("Selected changes:")
    assertThat(item.body).contains("- modified: /project/src/A.kt")
    assertThat(item.body).contains("- added: /project/src/B.kt")

    val payload = item.payload.objOrNull()!!
    assertThat(payload.string("kind")).isEqualTo("changes")
    assertThat(payload.number("selectedCount")).isEqualTo("2")
    assertThat(payload.number("includedCount")).isEqualTo("2")
  }

  @Test
  fun collectsSelectedChangesViaSelectedChanges() {
    val changes = arrayOf(deletion("/project/src/Removed.kt"))
    val dataContext = SimpleDataContext.builder()
      .add(VcsDataKeys.SELECTED_CHANGES, changes)
      .build()

    val result = contributor.collect(invocationData(dataContext))

    assertThat(result).hasSize(1)
    val item = result.single()
    assertThat(item.body).contains("- deleted: /project/src/Removed.kt")

    val payload = item.payload.objOrNull()!!
    assertThat(payload.string("kind")).isEqualTo("changes")
  }

  @Test
  fun leadSelectionTakesPriorityOverSelectedChanges() {
    val dataContext = SimpleDataContext.builder()
      .add(VcsDataKeys.CHANGE_LEAD_SELECTION, arrayOf(modification("/project/lead.kt")))
      .add(VcsDataKeys.SELECTED_CHANGES, arrayOf(addition("/project/selected.kt")))
      .build()

    val result = contributor.collect(invocationData(dataContext))

    assertThat(result).hasSize(1)
    val item = result.single()
    assertThat(item.body).contains("lead.kt")
    assertThat(item.body).doesNotContain("selected.kt")
  }

  @Test
  fun changeListsTakePriorityOverChanges() {
    val dataContext = SimpleDataContext.builder()
      .add(VcsDataKeys.CHANGE_LISTS, arrayOf<ChangeList>(emptyChangeList("MyCL", isDefault = true)))
      .add(VcsDataKeys.CHANGE_LEAD_SELECTION, arrayOf(modification("/project/file.kt")))
      .build()

    val result = contributor.collect(invocationData(dataContext))

    assertThat(result).hasSize(1)
    val item = result.single()
    assertThat(item.body).contains("Changelist: MyCL")
    assertThat(item.body).doesNotContain("Selected changes:")
  }

  @Test
  fun deduplicatesChangesByPath() {
    val dataContext = SimpleDataContext.builder()
      .add(VcsDataKeys.CHANGE_LEAD_SELECTION, arrayOf(
        modification("/project/src/Same.kt"),
        modification("/project/src/Same.kt"),
        addition("/project/src/Other.kt"),
      ))
      .build()

    val result = contributor.collect(invocationData(dataContext))

    assertThat(result).hasSize(1)
    val payload = result.single().payload.objOrNull()!!
    assertThat(payload.number("selectedCount")).isEqualTo("2")
    assertThat(payload.number("includedCount")).isEqualTo("2")
  }

  @Test
  fun movedChangeType() {
    val change = moved("/project/old/File.kt", "/project/new/File.kt")
    val dataContext = SimpleDataContext.builder()
      .add(VcsDataKeys.CHANGE_LEAD_SELECTION, arrayOf(change))
      .build()

    val result = contributor.collect(invocationData(dataContext))

    assertThat(result).hasSize(1)
    assertThat(result.single().body).contains("- moved: /project/new/File.kt")
  }

  @Test
  fun nonDefaultChangelistDoesNotShowActive() {
    val dataContext = SimpleDataContext.builder()
      .add(VcsDataKeys.CHANGE_LISTS, arrayOf<ChangeList>(emptyChangeList("Experiment", isDefault = false)))
      .build()

    val result = contributor.collect(invocationData(dataContext))

    assertThat(result).hasSize(1)
    assertThat(result.single().body).contains("Changelist: Experiment")
    assertThat(result.single().body).doesNotContain("(active)")
  }

  private fun invocationData(dataContext: DataContext?): AgentPromptInvocationData {
    val project = ProjectManager.getInstance().defaultProject
    val attributes = if (dataContext == null) {
      emptyMap()
    }
    else {
      mapOf(AGENT_PROMPT_VCS_INVOCATION_DATA_CONTEXT_KEY to dataContext)
    }
    return AgentPromptInvocationData(
      project = project,
      actionId = "AgentWorkbenchPrompt.OpenGlobalPalette",
      actionText = "Ask Agent",
      actionPlace = "ChangesView",
      invokedAtMs = 0L,
      attributes = attributes,
    )
  }

  private fun emptyChangeList(name: String, isDefault: Boolean = false, comment: String = ""): ChangeList {
    val project = ProjectManager.getInstance().defaultProject
    return LocalChangeListImpl.Builder(project, name)
      .setDefault(isDefault)
      .setComment(comment)
      .build()
  }

  private fun changeListWithChanges(name: String, changes: List<Change>, isDefault: Boolean = false): ChangeList {
    val project = ProjectManager.getInstance().defaultProject
    return LocalChangeListImpl.Builder(project, name)
      .setDefault(isDefault)
      .setChanges(changes)
      .build()
  }

  private fun modification(path: String): Change {
    val filePath = LocalFilePath(path, false)
    return Change(testRevision(filePath), testRevision(filePath))
  }

  private fun addition(path: String): Change {
    return Change(null, testRevision(LocalFilePath(path, false)))
  }

  private fun deletion(path: String): Change {
    return Change(testRevision(LocalFilePath(path, false)), null)
  }

  private fun moved(oldPath: String, newPath: String): Change {
    return Change(testRevision(LocalFilePath(oldPath, false)), testRevision(LocalFilePath(newPath, false)))
  }

  private fun testRevision(filePath: com.intellij.openapi.vcs.FilePath): ContentRevision {
    return object : ContentRevision {
      override fun getContent(): String? = null
      override fun getFile() = filePath
      override fun getRevisionNumber() = VcsRevisionNumber.NULL
    }
  }
}
