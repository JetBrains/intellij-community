// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptReusableSourceEntry
import com.intellij.agent.workbench.prompt.core.AgentPromptReusableSourceKind
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.CharFilter
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.application.UI
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.ProjectManager
import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionPromptCommandCompletionEntry
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionPromptCommandCompletionKind
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.textCompletion.TextCompletionUtil
import com.intellij.util.ui.EmptyIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

private const val CODEX_SKILL_PREFIX = '$'

private val CLAUDE_PROVIDER: AgentSessionProvider = AgentSessionProvider.from("claude")
private val CODEX_PROVIDER: AgentSessionProvider = AgentSessionProvider.from("codex")

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentPromptCommandCompletionProviderTest {
  @Test
  fun slashCompletionIsDisabledForProvidersWithoutCommandCompletion() {
    val provider = createProvider(selectedProvider = { testProviderDescriptor(provider = CODEX_PROVIDER) })

    assertThat(provider.getPrefix("/review", "/review".length)).isNull()
    assertThat(createProvider(selectedProvider = { null }).getPrefix("/review", "/review".length)).isNull()
  }

  @Test
  fun codexCompletionOnlyUsesDollarPrefixedWhitespaceDelimitedTokens() {
    val provider = createProvider(selectedProvider = { testProviderDescriptor(provider = CODEX_PROVIDER) })
    val teamcitySkill = codexSkillLookup("teamcity-cli")
    val promptedTeamcitySkill = "please $teamcitySkill"

    assertThat(provider.getPrefix(teamcitySkill, teamcitySkill.length)).isEqualTo(teamcitySkill)
    assertThat(provider.getPrefix(promptedTeamcitySkill, promptedTeamcitySkill.length)).isEqualTo(teamcitySkill)
    assertThat(provider.getPrefix("plain text", "plain text".length)).isNull()
    val claudeProvider = createProvider(selectedProvider = { testProviderDescriptor(provider = CLAUDE_PROVIDER) })
    assertThat(claudeProvider.getPrefix(teamcitySkill, teamcitySkill.length)).isNull()
  }

  @Test
  fun completionOnlyUsesSlashPrefixedWhitespaceDelimitedTokens() {
    val provider = createProvider(
      selectedProvider = { testProviderDescriptor(commandEntries = listOf(promptCommandEntry("/safe-push"))) },
    )

    assertThat(provider.getPrefix("please /safe-push", "please /safe-push".length)).isEqualTo("/safe-push")
    assertThat(provider.getPrefix("path/to/file", "path/to/file".length)).isNull()
    assertThat(provider.getPrefix("plain text", "plain text".length)).isNull()
  }

  @Test
  fun completionAutopopupAcceptsCommandCharacters() {
    val provider = createProvider(selectedProvider = { testProviderDescriptor(commandEntries = listOf(promptCommandEntry("/safe-push"))) })

    assertThat(provider.acceptChar('/')).isEqualTo(CharFilter.Result.ADD_TO_PREFIX)
    assertThat(provider.acceptChar('$')).isEqualTo(CharFilter.Result.ADD_TO_PREFIX)
    assertThat(provider.acceptChar('a')).isEqualTo(CharFilter.Result.ADD_TO_PREFIX)
    assertThat(provider.acceptChar('7')).isEqualTo(CharFilter.Result.ADD_TO_PREFIX)
    assertThat(provider.acceptChar('-')).isEqualTo(CharFilter.Result.ADD_TO_PREFIX)
    assertThat(provider.acceptChar('_')).isEqualTo(CharFilter.Result.ADD_TO_PREFIX)
    assertThat(provider.acceptChar(' ')).isNull()
  }

  @Test
  fun codexAutoPopupOnlyTriggersForLeadingDollarTypedAsFirstPromptCharacter() {
    val teamcitySkill = codexSkillLookup("teamcity-cli")
    val promptedDollar = "use $CODEX_SKILL_PREFIX"

    assertThat(
      shouldAutoPopupCodexSkillCompletion(
        selectedProvider = CODEX_PROVIDER,
        text = CODEX_SKILL_PREFIX.toString(),
        offsetAfterChange = 1,
        insertedFragment = CODEX_SKILL_PREFIX.toString(),
      ),
    ).isTrue()

    assertThat(
      shouldAutoPopupCodexSkillCompletion(
        selectedProvider = CODEX_PROVIDER,
        text = promptedDollar,
        offsetAfterChange = promptedDollar.length,
        insertedFragment = CODEX_SKILL_PREFIX.toString(),
      ),
    ).isFalse()

    assertThat(
      shouldAutoPopupCodexSkillCompletion(
        selectedProvider = CODEX_PROVIDER,
        text = teamcitySkill,
        offsetAfterChange = teamcitySkill.length,
        insertedFragment = "teamcity-cli",
      ),
    ).isFalse()

    assertThat(
      shouldAutoPopupCodexSkillCompletion(
        selectedProvider = CLAUDE_PROVIDER,
        text = CODEX_SKILL_PREFIX.toString(),
        offsetAfterChange = 1,
        insertedFragment = CODEX_SKILL_PREFIX.toString(),
      ),
    ).isFalse()
  }

  @Test
  fun autoPopupOnlyTriggersForLeadingSlashTypedAsFirstPromptCharacter() {
    val descriptor = testProviderDescriptor(commandEntries = listOf(promptCommandEntry("/review")))

    assertThat(
      shouldAutoPopupPromptCommandCompletion(
        selectedProvider = descriptor,
        workingProjectPaths = listOf("/project"),
        text = "/",
        offsetAfterChange = 1,
        insertedFragment = "/",
      ),
    ).isTrue()

    assertThat(
      shouldAutoPopupPromptCommandCompletion(
        selectedProvider = descriptor,
        workingProjectPaths = listOf("/project"),
        text = "open /",
        offsetAfterChange = "open /".length,
        insertedFragment = "/",
      ),
    ).isFalse()

    assertThat(
      shouldAutoPopupPromptCommandCompletion(
        selectedProvider = descriptor,
        workingProjectPaths = listOf("/project"),
        text = "/review",
        offsetAfterChange = "/review".length,
        insertedFragment = "review",
      ),
    ).isFalse()

    assertThat(
      shouldAutoPopupPromptCommandCompletion(
        selectedProvider = testProviderDescriptor(provider = CODEX_PROVIDER),
        workingProjectPaths = listOf("/project"),
        text = "/",
        offsetAfterChange = 1,
        insertedFragment = "/",
      ),
    ).isFalse()
  }

  @Test
  fun completionPathFallsBackToProjectBasePath() {
    assertThat(
      resolvePromptCommandCompletionProjectPaths(
        workingProjectPath = null,
        sourceProjectBasePath = null,
        projectBasePath = "/repo",
      ),
    ).containsExactly("/repo")
    assertThat(
      resolvePromptCommandCompletionProjectPaths(
        workingProjectPath = "/workspace",
        sourceProjectBasePath = null,
        projectBasePath = "/repo",
      ),
    ).containsExactly("/workspace", "/repo")
    assertThat(
      resolvePromptCommandCompletionProjectPaths(
        workingProjectPath = null,
        sourceProjectBasePath = "/source",
        projectBasePath = "/dedicated",
      ),
    ).containsExactly("/source", "/dedicated")
  }

  @Test
  fun invokingBasicCompletionOnPromptFieldShowsProviderCommandEntries() {
    runInEdtAndWait {
      val project = ProjectManager.getInstance().defaultProject
      val completionProvider = createProvider(
        selectedProvider = {
          testProviderDescriptor(
            commandEntries = listOf(
              promptCommandEntry("/mcp"),
              promptCommandEntry("/model", argumentHint = "[model]"),
              promptCommandEntry("/review", kind = AgentSessionPromptCommandCompletionKind.COMMAND, argumentHint = "[PR number]"),
              promptCommandEntry("/safe-push", kind = AgentSessionPromptCommandCompletionKind.SKILL, argumentHint = "[focus]"),
            ),
          )
        },
      )
      val textField = AgentPromptTextField(project, completionProvider)
      textField.addNotify()
      try {
        textField.text = "/"
        val editor = checkNotNull(textField.editor) { "Editor was not initialized" }
        editor.caretModel.moveToOffset(1)

        CodeCompletionHandlerBase(CompletionType.BASIC, true, false, true).invokeCompletion(project, editor, 1)

        val lookup = LookupManager.getActiveLookup(editor)
        assertThat(lookup).isNotNull
        val activeLookup = checkNotNull(lookup)
        assertThat(activeLookup.items.map { item -> item.lookupString }).contains("/mcp", "/review", "/safe-push")

        val tailTextByLookup = activeLookup.items.associate { item ->
          item.lookupString to LookupElementPresentation.renderElement(item).tailText
        }
        assertThat(tailTextByLookup["/model"]).isEqualTo(" [model]")
        assertThat(tailTextByLookup["/review"]).isEqualTo(" [PR number]")
        assertThat(tailTextByLookup["/safe-push"]).isEqualTo(" [focus]")
      }
      finally {
        textField.removeNotify()
      }
    }
  }

  @Test
  fun invokingBasicCompletionOnPromptFieldShowsCodexSkillEntries() {
    runInEdtAndWait {
      val project = ProjectManager.getInstance().defaultProject
      val completionProvider = createProvider(
        selectedProvider = { testProviderDescriptor(provider = CODEX_PROVIDER) },
        codexSkillEntries = {
          listOf(
            codexSkillEntry("teamcity-cli", "Inspect TeamCity builds"),
            codexSkillEntry("writing-tests", "Write focused tests"),
          )
        },
      )
      val textField = AgentPromptTextField(project, completionProvider)
      textField.addNotify()
      try {
        textField.text = CODEX_SKILL_PREFIX.toString()
        val editor = checkNotNull(textField.editor) { "Editor was not initialized" }
        editor.caretModel.moveToOffset(1)

        CodeCompletionHandlerBase(CompletionType.BASIC, true, false, true).invokeCompletion(project, editor, 1)

        val lookup = LookupManager.getActiveLookup(editor)
        assertThat(lookup).isNotNull
        val activeLookup = checkNotNull(lookup)
        val teamcitySkill = codexSkillLookup("teamcity-cli")
        val writingTestsSkill = codexSkillLookup("writing-tests")
        assertThat(activeLookup.items.map { item -> item.lookupString })
          .contains(teamcitySkill, writingTestsSkill)
        val tailTextByLookup = activeLookup.items.associate { item ->
          item.lookupString to LookupElementPresentation.renderElement(item).tailText
        }
        assertThat(tailTextByLookup[teamcitySkill]).isEqualTo(" Inspect TeamCity builds")
      }
      finally {
        textField.removeNotify()
      }
    }
  }

  @Test
  fun textFieldUsesTextDocumentAndKeepsCompletionProviderInstalled() {
    val project = ProjectManager.getInstance().defaultProject
    val completionProvider = createProvider(selectedProvider = { testProviderDescriptor(commandEntries = listOf(promptCommandEntry("/mcp"))) })
    val document = runBlocking(Dispatchers.UI) {
      AgentPromptTextField(project, completionProvider).document
    }

    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)

    assertThat(psiFile).isNotNull
    assertThat(psiFile!!.fileType).isEqualTo(FileTypes.PLAIN_TEXT)
    assertThat(TextCompletionUtil.getProvider(psiFile)).isSameAs(completionProvider)
  }

  private fun createProvider(
    selectedProvider: () -> AgentSessionProviderDescriptor?,
    workingProjectPaths: () -> List<String> = { emptyList() },
    codexSkillEntries: () -> List<AgentPromptReusableSourceEntry> = { emptyList() },
  ): AgentPromptCommandCompletionProvider {
    return AgentPromptCommandCompletionProvider(selectedProvider, workingProjectPaths, codexSkillEntries)
  }

  private fun testProviderDescriptor(
    provider: AgentSessionProvider = CLAUDE_PROVIDER,
    commandEntries: List<AgentSessionPromptCommandCompletionEntry> = emptyList(),
  ): AgentSessionProviderDescriptor {
    return object : AgentSessionProviderDescriptor {
      override val provider: AgentSessionProvider = provider
      override val displayNameKey: String = "provider.${provider.value}"
      override val newSessionLabelKey: String = displayNameKey
      override val sessionSource: AgentSessionSource
        get() = error("Not required for this test")
      override val cliMissingMessageKey: String = displayNameKey
      override val icon = EmptyIcon.ICON_16

      override suspend fun isCliAvailable(): Boolean = true

      override suspend fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec {
        return AgentSessionTerminalLaunchSpec(command = emptyList())
      }

      override suspend fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec {
        return AgentSessionTerminalLaunchSpec(command = emptyList())
      }

      override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan {
        return AgentInitialMessagePlan.EMPTY
      }

      override fun collectPromptCommandCompletionEntries(projectPaths: Iterable<String?>): List<AgentSessionPromptCommandCompletionEntry> {
        return commandEntries
      }
    }
  }

  private fun promptCommandEntry(
    command: String,
    kind: AgentSessionPromptCommandCompletionKind = AgentSessionPromptCommandCompletionKind.MENU,
    argumentHint: String = "",
  ): AgentSessionPromptCommandCompletionEntry {
    return AgentSessionPromptCommandCompletionEntry(
      command = command,
      kind = kind,
      sourceKey = "test:$command",
      argumentHint = argumentHint,
    )
  }

  private fun codexSkillEntry(name: String, description: String): AgentPromptReusableSourceEntry {
    return AgentPromptReusableSourceEntry(
      id = "codex:skill:$name",
      label = name,
      insertText = codexSkillLookup(name) + " ",
      kind = AgentPromptReusableSourceKind.SKILL,
      provider = CODEX_PROVIDER,
      description = description,
    )
  }
}

private fun codexSkillLookup(name: String): String = CODEX_SKILL_PREFIX.toString() + name
