// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui

import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.IncrementallyComputedValue
import com.intellij.icons.AllIcons
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.UsefulTestCase.assertSameElements
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.plugins.gitlab.api.dto.GitLabGroupRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class GitlabMarkdownCompletionContributorTest {

  private val tempPath = tempPathFixture()
  private val project = projectFixture(openAfterCreation = true)
  @Suppress("unused") // need to register the module for code insight tests
  private val moduleForCodeInsight = project.moduleFixture(tempPath)
  private val codeInsightFixture by codeInsightFixture(project, tempPath)

  @Test
  fun `test mention completion filters participants project users and groups by prefix matching username name or path`() {
    val vm = createMockViewModel(
      author = createUser("author", "Author User"),
      participants = listOf(
        createUser("participant1", "Participant One"),
        createUser("participant2", "Participant Two")
      ),
      projectUsers = listOf(
        createUser("user1", "User One"),
        createUser("user2", "User Two"),
        createUser("user3", "Par")
      ),
      groups = listOf(
        createGroup("group1", "Group One"),
        createGroup("par-group2", "Another Group")
      )
    )

    configure("Test comment @par<caret>", vm)

    val completeBasic = codeInsightFixture.completeBasic()
    assertNotNull(completeBasic)
    assertSameElements(
      completeBasic.map { it.lookupString },
      listOf("@participant1", "@participant2", "@user3", "@par-group2")
    )
  }

  @Test
  fun `test mention completion works after newlines when @ symbol is present`() {
    val vm = createMockViewModel(
      author = createUser("author", "Author User"),
      participants = listOf(
        createUser("participant1", "Participant One"),
        createUser("participant2", "Participant Two")
      ),
      projectUsers = listOf(
        createUser("user1", "User One"),
        createUser("user2", "User Two"),
        createUser("user3", "Par")
      ),
      groups = listOf(
        createGroup("group1", "Group One"),
        createGroup("par-group2", "Another Group")
      )
    )

    configure("Test comment\n\n@par<caret>", vm)
    val completeBasic = codeInsightFixture.completeBasic()
    assertNotNull(completeBasic)
    assertSameElements(
      completeBasic.map { it.lookupString },
      listOf("@participant1", "@participant2", "@user3", "@par-group2")
    )
  }

  @Test
  fun `test no completion suggestions appear when @ symbol is missing from text`() {
    val vm = createMockViewModel(
      author = createUser("author", "Author User"),
      participants = listOf(createUser("testuser", "Test User")),
      projectUsers = emptyList(),
      groups = emptyList()
    )

    configure("Comment test<caret>", vm)
    val result = codeInsightFixture.completeBasic()
    assertEmpty(result)
  }

  @Test
  fun `test no completion suggestions appear at empty caret position without @ symbol`() {
    val vm = createMockViewModel(
      author = createUser("author", "Author User"),
      participants = listOf(createUser("testuser", "Test User")),
      projectUsers = emptyList(),
      groups = emptyList()
    )

    configure("Comment <caret>", vm)
    val result = codeInsightFixture.completeBasic()
    assertEmpty(result)
  }

  @Test
  fun `test mention completion includes project users when filtering by prefix`() {
    val vm = createMockViewModel(
      author = createUser("author", "Author User"),
      participants = listOf(createUser("testuser", "Test User")),
      projectUsers = listOf(
        createUser("test", "tests"),
        createUser("mentionable1", "Mentionable One"),
        createUser("mentionable2", "Mentionable Two"),
        createUser("mentionable3", "Mentionable Three")
      ),
      groups = emptyList()
    )

    configure("@men<caret>", vm)
    val lookupElements = codeInsightFixture.completeBasic()

    assertNotNull(lookupElements)
    assertSameElements(
      lookupElements.map { it.lookupString },
      listOf("@mentionable1", "@mentionable2", "@mentionable3")
    )
  }

  @Test
  fun `test mention completion includes groups when filtering by prefix`() {
    val vm = createMockViewModel(
      author = createUser("author", "Author User"),
      participants = listOf(createUser("testuser", "Test User")),
      projectUsers = emptyList(),
      groups = listOf(
        createGroup("tests", "tests"),
        createGroup("group1", "Frontend Team"),
        createGroup("group2", "Backend Team"),
        createGroup("group3", "Another Group")
      )
    )

    configure("@gro<caret>", vm)
    val lookupElements = codeInsightFixture.completeBasic()

    assertNotNull(lookupElements)
    assertSameElements(lookupElements.map { it.lookupString }, listOf("@group1", "@group2", "@group3"))
  }

  @Test
  fun `test completion shows participants, project users and groups when @ symbol is typed`() {
    val vm = createMockViewModel(
      author = createUser("author", "Author User"),
      participants = listOf(createUser("author", "Author User"),
                            createUser("participant", "Participant User")),
      projectUsers = listOf(createUser("mentionable", "Mentionable User")),
      groups = listOf(createGroup("group", "Group Name"))
    )

    configure("@<caret>", vm)
    val lookupElements = codeInsightFixture.completeBasic()

    assertNotNull(lookupElements)
    assertSameElements(
      lookupElements.map { it.lookupString },
      listOf("@author", "@participant", "@mentionable", "@group")
    )
  }

  @Test
  fun `test mention completion works with text after caret position`() {
    val vm = createMockViewModel(
      author = createUser("author", "Author User"),
      participants = listOf(
        createUser("participant1", "Participant One"),
        createUser("participant2", "Participant Two")
      ),
      projectUsers = emptyList(),
      groups = emptyList()
    )

    configure("@part<caret> test", vm)
    val lookupElements = codeInsightFixture.completeBasic()

    assertNotNull(lookupElements)
    assertSameElements(lookupElements.map { it.lookupString }, listOf("@participant1", "@participant2"))
  }

  @Test
  fun `test mention with participant failure contains author`() {
    suppressFallingOnLogError("Error fetching GitLab users for completion") {
      val vm = createMockViewModel(createUser("author", "Author User"),
                                   IncrementallyComputedValue.failure(Exception("Mock error for testing participants")),
                                   ComputedResult.success(listOf(createUser("user", "User"))),
                                   ComputedResult.success(listOf(createGroup("group", "Group")))
      )

      configure("@<caret>", vm)
      val lookupElements = codeInsightFixture.completeBasic()

      assertNotNull(lookupElements)
      assertSameElements(lookupElements.map { it.lookupString }, listOf("@author", "@user", "@group"))
    }
  }


  @Test
  fun `test mention with project users failure`() {
    suppressFallingOnLogError("Error fetching GitLab project users for completion") {
      val vm = createMockViewModel(createUser("author", "Author User"),
                                   IncrementallyComputedValue.success(listOf(
                                     createUser("author", "Author User"),
                                     createUser("participant", "Participant User"))
                                   ),
                                   ComputedResult.failure(Exception("Error")),
                                   ComputedResult.success(listOf(createGroup("group", "Group")))
      )

      configure("@<caret>", vm)
      val lookupElements = codeInsightFixture.completeBasic()

      assertNotNull(lookupElements)
      assertSameElements(lookupElements.map { it.lookupString }, listOf("@author", "@participant", "@group"))
    }
  }

  @Test
  fun `test mention with groups failure`() {
    suppressFallingOnLogError("Error fetching GitLab groups for completion") {
      val vm = createMockViewModel(createUser("author", "Author User"),
                                   IncrementallyComputedValue.success(listOf(
                                     createUser("author", "Author User"),
                                     createUser("participant", "Participant User"))
                                   ),
                                   ComputedResult.success(listOf(createUser("user", "User"))),
                                   ComputedResult.failure(Exception("Error"))
      )

      configure("@<caret>", vm)
      val lookupElements = codeInsightFixture.completeBasic()

      assertNotNull(lookupElements)
      assertSameElements(lookupElements.map { it.lookupString }, listOf("@author", "@participant", "@user"))
    }
  }

  @Test
  fun `test mention insertion adds space after username at end of text`() {
    val vm = createMockViewModel(
      author = createUser("author", "Author User"),
      participants = listOf(createUser("testuser", "Test User")),
      projectUsers = emptyList(),
      groups = emptyList()
    )

    configure("@test<caret>", vm)
    codeInsightFixture.completeBasic()
    codeInsightFixture.type('\n')

    val text = codeInsightFixture.editor.document.text
    assertTrue(text.contains("@testuser "))
  }

  @Test
  fun `test mention insertion replaces prefix and preserves text after caret`() {
    val vm = createMockViewModel(
      author = createUser("author", "Author User"),
      participants = listOf(
        createUser("participant1", "Participant One"),
        createUser("participant2", "Participant Two")
      ),
      projectUsers = emptyList(),
      groups = emptyList()
    )

    configure("@part<caret> test", vm)
    codeInsightFixture.completeBasic()
    codeInsightFixture.type('\n')

    val text = codeInsightFixture.editor.document.text
    assertEquals("@participant1 test", text)
  }

  @Test
  fun `test mention insertion replaces prefix and adds space before adjacent text`() {
    val vm = createMockViewModel(
      author = createUser("author", "Author User"),
      participants = listOf(
        createUser("participant1", "Participant One"),
        createUser("participant2", "Participant Two")
      ),
      projectUsers = emptyList(),
      groups = emptyList()
    )

    configure("@part<caret>test", vm)
    codeInsightFixture.completeBasic()
    codeInsightFixture.type('\n')

    val text = codeInsightFixture.editor.document.text
    assertEquals("@participant1 test", text)
  }

  private fun configure(text: String, vm: GitLabViewModelWithTextCompletion) {
    codeInsightFixture.configureByText("test.md", text)
    codeInsightFixture.editor.putUserData(GitLabViewModelWithTextCompletion.MENTIONS_COMPLETION_KEY, vm)
  }

  private fun createMockViewModel(
    author: GitLabUserDTO,
    participants: List<GitLabUserDTO>,
    projectUsers: List<GitLabUserDTO>,
    groups: List<GitLabGroupRestDTO>,
  ): GitLabViewModelWithTextCompletion {
    val participantsResult = IncrementallyComputedValue.success(participants)
    val projectUsersResult = ComputedResult.success(projectUsers)
    val groupsResult = ComputedResult.success(groups)

    return createMockViewModel(author, participantsResult, projectUsersResult, groupsResult)
  }

  private fun createMockViewModel(
    author: GitLabUserDTO,
    participantsResult: IncrementallyComputedValue<List<GitLabUserDTO>>,
    projectUsersResult: ComputedResult<List<GitLabUserDTO>>,
    groupsResult: ComputedResult<List<GitLabGroupRestDTO>>,
  ): GitLabViewModelWithTextCompletion {
    val mentionCompletion = object : GitLabTextCompletionViewModel {
      override val avatarIconsProvider = IconsProvider<GitLabUserDTO> { _, _ -> AllIcons.Stub }
      override val author: GitLabUserDTO = author
      override val participants: StateFlow<IncrementallyComputedValue<List<GitLabUserDTO>>> =
        MutableStateFlow(participantsResult)
      override val foundProjectUsers: StateFlow<ComputedResult<List<GitLabUserDTO>>?> =
        MutableStateFlow(projectUsersResult)
      override val foundGroups: StateFlow<ComputedResult<List<GitLabGroupRestDTO>>?> =
        MutableStateFlow(groupsResult)

      override fun setSearchPrefix(prefix: String) {}
    }

    return object : GitLabViewModelWithTextCompletion {
      override fun withMentionCompletionModel(consumer: (GitLabTextCompletionViewModel) -> Unit) {
        consumer(mentionCompletion)
      }
    }
  }

  private fun createUser(username: String, name: String): GitLabUserDTO {
    return GitLabUserDTO(
      id = "gid://gitlab/User/$username",
      username = username,
      name = name,
      avatarUrl = "https://gitlab.com/$username/avatar.png",
      webUrl = "https://gitlab.com/$username"
    )
  }

  private fun createGroup(path: String, name: String): GitLabGroupRestDTO {
    return GitLabGroupRestDTO("gid://gitlab/User/$path", path, name)
  }

  private fun suppressFallingOnLogError(suppressedExceptionMessage: String, call: () -> Unit) {
    LoggedErrorProcessor.executeWith<RuntimeException>(object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): Set<Action> =
        if (message.contains(suppressedExceptionMessage)) {
          Action.NONE
        }
        else Action.ALL
    }) {
      call()
    }
  }
}