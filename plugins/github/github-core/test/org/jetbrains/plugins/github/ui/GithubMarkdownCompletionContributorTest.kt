// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ui

import com.intellij.collaboration.util.IncrementallyComputedValue
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.UsefulTestCase.assertSameElements
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHViewModelWithTextCompletion
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHViewModelMentionCompletion
import org.jetbrains.plugins.github.ui.icons.GHAvatarIconsProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull

@TestApplication
class GithubMarkdownCompletionContributorTest {

  private val tempPath = tempPathFixture()
  private val project = projectFixture(openAfterCreation = true)
  @Suppress("unused") // need to register the module for code insight tests
  private val moduleForCodeInsight = project.moduleFixture(tempPath)
  private val codeInsightFixture by codeInsightFixture(project, tempPath)

  @Test
  fun `test mention completion filters users by prefix matching login or name`() {
    val vm = createMockViewModel(
      participants = listOf(
        createUser("participant1", "Participant One"),
        createUser("participant2", "Participant Two")
      ),
      mentionableUsers = listOf(
        createUser("user1", "User One"),
        createUser("user2", "User Two"),
        createUser("user3", "Par")
      )
    )

    configure("Test comment @par<caret>", vm)
    val completeBasic = codeInsightFixture.completeBasic()
    assertNotNull(completeBasic!!)
    assertSameElements(completeBasic.map { it.lookupString },
                       listOf("@participant1", "@participant2", "@user3"))
  }

  @Test
  fun `test mention completion works after newlines when @ symbol is present`() {
    val vm = createMockViewModel(
      participants = listOf(
        createUser("participant1", "Participant One"),
        createUser("participant2", "Participant Two")
      ),
      mentionableUsers = listOf(
        createUser("user1", "User One"),
        createUser("user2", "User Two"),
        createUser("user3", "Par")
      )
    )

    configure("Test comment\n\n@par<caret>", vm)
    val completeBasic = codeInsightFixture.completeBasic()
    assertNotNull(completeBasic!!)
    assertSameElements(completeBasic.map { it.lookupString },
                       listOf("@participant1", "@participant2", "@user3"))
  }

  @Test
  fun `test no completion suggestions appear when @ symbol is missing from text`() {
    val vm = createMockViewModel(
      participants = listOf(createUser("testuser", "Test User")),
      mentionableUsers = emptyList()
    )

    configure("Comment test<caret>", vm)
    val result = codeInsightFixture.completeBasic()
    assertEmpty(result)
  }

  @Test
  fun `test no completion suggestions appear at empty caret position without @ symbol`() {
    val vm = createMockViewModel(
      participants = listOf(createUser("testuser", "Test User")),
      mentionableUsers = emptyList()
    )

    configure("Comment <caret>", vm)
    val result = codeInsightFixture.completeBasic()
    assertEmpty(result)
  }

  @Test
  fun `test mention completion includes mentionable users when filtering by prefix`() {
    val vm = createMockViewModel(
      participants = listOf(createUser("testuser", "Test User")),
      mentionableUsers = listOf(
        createUser("test", "tests"),
        createUser("mentionable1", "Mentionable One"),
        createUser("mentionable2", "Mentionable Two"),
        createUser("mentionable3", "Mentionable Three")
      )
    )

    configure("@men<caret>", vm)
    val lookupElements = codeInsightFixture.completeBasic()

    assertNotNull(lookupElements!!)
    assertSameElements(lookupElements.map { it.lookupString },
                       listOf("@mentionable1", "@mentionable2", "@mentionable3"))
  }

  @Test
  fun `test completion shows both participants and mentionable users when @ symbol is typed`() {
    val vm = createMockViewModel(
      participants = listOf(createUser("participant", "Participant User")),
      mentionableUsers = listOf(createUser("mentionable", "Mentionable User"))
    )

    configure("@<caret>", vm)
    val lookupElements = codeInsightFixture.completeBasic()

    assertNotNull(lookupElements!!)
    assertSameElements(lookupElements.map { it.lookupString },
                       listOf("@participant", "@mentionable"))
  }

  @Test
  fun `test mention completion works with text after caret position`() {
    val vm = createMockViewModel(
      participants = listOf(createUser("participant1", "Participant One"),
                            createUser("participant2", "Participant Two")),
      mentionableUsers = emptyList()
    )

    configure("@part<caret> test", vm)
    val lookupElements = codeInsightFixture.completeBasic()

    assertNotNull(lookupElements!!)
    assertSameElements(lookupElements.map { it.lookupString },
                       listOf("@participant1", "@participant2"))
  }

  @Test
  fun `test mention insertion adds space after username at end of text`() {
    val vm = createMockViewModel(
      participants = listOf(createUser("testuser", "Test User")),
      mentionableUsers = emptyList()
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
      participants = listOf(createUser("participant1", "Participant One"),
                            createUser("participant2", "Participant Two")),
      mentionableUsers = emptyList()
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
      participants = listOf(createUser("participant1", "Participant One"),
                            createUser("participant2", "Participant Two")),
      mentionableUsers = emptyList()
    )

    configure("@part<caret>test", vm)
    codeInsightFixture.completeBasic()
    codeInsightFixture.type('\n')

    val text = codeInsightFixture.editor.document.text
    assertEquals("@participant1 test", text)
  }

  private fun configure(text: String, vm: GHViewModelWithTextCompletion) {

    val fileType = FileTypeRegistry.getInstance().getFileTypeByExtension("md").takeIf { it != FileTypes.UNKNOWN } ?: FileTypes.PLAIN_TEXT

    codeInsightFixture.configureByText(fileType, text)
    codeInsightFixture.editor.putUserData(GHViewModelWithTextCompletion.MENTIONS_COMPLETION_KEY, vm)
  }

  private fun createMockViewModel(
    participants: List<GHUser>,
    mentionableUsers: List<GHUser>,
  ): GHViewModelWithTextCompletion {
    val mentionCompletion = object : GHViewModelMentionCompletion {
      override val pullRequestParticipants: StateFlow<IncrementallyComputedValue<List<GHUser>>> =
        MutableStateFlow(IncrementallyComputedValue.success(participants))

      override val mentionableUsers: StateFlow<IncrementallyComputedValue<List<GHUser>>> =
        MutableStateFlow(IncrementallyComputedValue.success(mentionableUsers))

      override val avatarIconsProvider: GHAvatarIconsProvider = GHAvatarIconsProvider { _, _ -> AllIcons.Stub }
    }

    return object : GHViewModelWithTextCompletion {
      override fun withMentionCompletionModel(consumer: (GHViewModelMentionCompletion) -> Unit) {
        consumer(mentionCompletion)
      }
    }
  }

  private fun createUser(login: String, name: String?): GHUser {
    return GHUser(login, login, "https://github.com/$login", "https://avatars.githubusercontent.com/$login", name)
  }
}