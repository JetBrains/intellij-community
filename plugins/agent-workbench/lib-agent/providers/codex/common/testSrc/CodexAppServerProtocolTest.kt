// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.codex.common

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CodexAppServerProtocolTest {
  private val protocol = CodexAppServerProtocol()

  @Test
  fun parsesThreadListVariantsAndCwdFilter() {
    val result = parseResponse(
      """
        {
          "data": [
            {
              "id": "thread-1",
              "title": "First\nThread",
              "cwd": "/work/project/",
              "path": "/work/project/.codex/sessions/thread-1.jsonl",
              "source": {"subAgent": {"thread_spawn": {"parent_thread_id": "parent-1"}}},
              "agentNickname": "Scout",
              "agentRole": "reviewer",
              "status": {"type": "active", "active_flags": ["waiting_on_user_input"]},
              "gitInfo": {"branch": "feature/a"},
              "updated_at": 1700000000
            },
            {
              "id": "thread-2",
              "title": "Other",
              "cwd": "/other/project",
              "source": "cli",
              "status": "idle",
              "updated_at": 1700000100000
            }
          ],
          "next_cursor": "cursor-2"
        }
      """.trimIndent(),
      defaultResult = ThreadListResult(emptyList(), null),
    ) { parser -> protocol.parseThreadListResult(parser, archived = false, cwdFilter = "/work/project") }

    assertThat(result.nextCursor).isEqualTo("cursor-2")
    assertThat(result.threads).hasSize(1)
    val thread = result.threads.single()
    assertThat(thread.id).isEqualTo("thread-1")
    assertThat(thread.title).isEqualTo("First Thread")
    assertThat(thread.updatedAt).isEqualTo(1_700_000_000_000L)
    assertThat(thread.cwd).isEqualTo("/work/project")
    assertThat(thread.sourceKind).isEqualTo(CodexThreadSourceKind.SUB_AGENT_THREAD_SPAWN)
    assertThat(thread.parentThreadId).isEqualTo("parent-1")
    assertThat(thread.agentNickname).isEqualTo("Scout")
    assertThat(thread.agentRole).isEqualTo("reviewer")
    assertThat(thread.statusKind).isEqualTo(CodexThreadStatusKind.ACTIVE)
    assertThat(thread.activeFlags).containsExactly(CodexThreadActiveFlag.WAITING_ON_USER_INPUT)
    assertThat(thread.gitBranch).isEqualTo("feature/a")
  }

  @Test
  fun parsesModelAndSkillCatalogVariants() {
    val models = parseResponse(
      """
        {
          "models": [
            {
              "id": "gpt-5.1-codex",
              "displayName": "GPT-5.1 Codex",
              "supportedReasoningLevels": [{"reasoningEffort": "low"}, {"value": "medium"}],
              "defaultReasoningLevel": "medium",
              "isDefault": true
            },
            {
              "slug": "o4-mini",
              "display_name": "o4 mini",
              "supported_reasoning_efforts": ["low", "high"],
              "default_reasoning_effort": "low",
              "hidden": true
            }
          ],
          "nextCursor": "next"
        }
      """.trimIndent(),
      defaultResult = ModelListResult(emptyList(), null),
    ) { parser -> protocol.parseModelListResult(parser) }

    assertThat(models.nextCursor).isEqualTo("next")
    assertThat(models.models.map { it.id }).containsExactly("gpt-5.1-codex", "o4-mini")
    assertThat(models.models.first().supportedReasoningEfforts).containsExactly("low", "medium")
    assertThat(models.models.first().defaultReasoningEffort).isEqualTo("medium")
    assertThat(models.models.first().isDefault).isTrue()
    assertThat(models.models.last().supportedReasoningEfforts).containsExactly("low", "high")
    assertThat(models.models.last().hidden).isTrue()

    val skills = parseResponse(
      """
        {
          "data": [
            {
              "cwd": "/work/project",
              "skills": [
                {
                  "name": "reviewer",
                  "path": "/work/project/.codex/skills/reviewer/SKILL.md",
                  "description": "Review code changes",
                  "enabled": true,
                  "interface": {
                    "display_name": "Reviewer",
                    "short_description": "Find issues",
                    "default_prompt": "Review the diff."
                  }
                }
              ]
            }
          ]
        }
      """.trimIndent(),
      defaultResult = emptyList(),
    ) { parser -> protocol.parseSkillsListResult(parser) }

    assertThat(skills).hasSize(1)
    assertThat(skills.single().displayName).isEqualTo("Reviewer")
    assertThat(skills.single().shortDescription).isEqualTo("Find issues")
    assertThat(skills.single().defaultPrompt).isEqualTo("Review the diff.")
  }

  @Test
  fun parsesThreadReadActivityProjection() {
    val snapshot = parseResponse(
      """
        {
          "thread": {
            "id": "thread-read-1",
            "updated_at": 1700000031000,
            "status": {"type": "active", "activeFlags": ["waitingOnApproval"]},
            "turns": [
              {"status": "completed", "items": [{"type": "userMessage"}, {"type": "agentMessage"}, {"type": "enteredReviewMode"}]},
              {"id": "turn-2", "status": {"type": "in_progress"}, "items": [{"type": "plan"}]}
            ]
          }
        }
      """.trimIndent(),
      defaultResult = null,
    ) { parser -> protocol.parseThreadReadActivityResult(parser) }

    assertThat(snapshot).isNotNull
    assertThat(snapshot!!.threadId).isEqualTo("thread-read-1")
    assertThat(snapshot.statusKind).isEqualTo(CodexThreadStatusKind.ACTIVE)
    assertThat(snapshot.activeFlags).containsExactly(CodexThreadActiveFlag.WAITING_ON_APPROVAL)
    assertThat(snapshot.hasUnreadAssistantMessage).isTrue()
    assertThat(snapshot.hasPendingPlan).isTrue()
    assertThat(snapshot.isReviewing).isTrue()
    assertThat(snapshot.hasInProgressTurn).isTrue()
    assertThat(snapshot.hasTurnActivity).isTrue()
  }

  @Test
  fun parsesNotificationsForPublicAndPromptTurnFields() {
    val threadStarted = protocol.parseNotification(
      """
        {
          "method": "thread/started",
          "params": {
            "thread": {
              "id": "thread-1",
              "title": "Started",
              "cwd": "/work/project",
              "updated_at": 1700000000000,
              "status": {"type": "idle"}
            }
          }
        }
      """.trimIndent()
    )

    assertThat(threadStarted).isNotNull
    assertThat(threadStarted!!.threadId).isEqualTo("thread-1")
    assertThat(threadStarted.startedThread!!.cwd).isEqualTo("/work/project")
    assertThat(threadStarted.toPublicNotification().startedThread!!.id).isEqualTo("thread-1")

    val itemCompleted = protocol.parseNotification(
      """
        {
          "method": "item/completed",
          "params": {
            "thread_id": "thread-1",
            "turn": {"id": "turn-1", "status": "completed"},
            "item": {"type": "agentMessage", "text": "hello"}
          }
        }
      """.trimIndent()
    )

    assertThat(itemCompleted).isNotNull
    assertThat(itemCompleted!!.threadId).isEqualTo("thread-1")
    assertThat(itemCompleted.turnId).isEqualTo("turn-1")
    assertThat(itemCompleted.agentMessageText).isEqualTo("hello")

    val responseLikePayload = protocol.parseNotification("""{"id":"1","method":"turn/completed","params":{}}""")
    assertThat(responseLikePayload).isNull()
  }

  @Test
  fun throwsAppServerErrorAndRecognizesIncludeTurnsFallback() {
    assertThatThrownBy {
      protocol.parseResponse(
        payload = """{"id":"1","error":{"message":"boom"}}""",
        resultParser = { error("Result parser should not be called") },
        defaultResult = Unit,
      )
    }.isInstanceOf(CodexAppServerException::class.java)
      .hasMessageContaining("boom")

    assertThat(CodexAppServerException("includeTurns is unavailable before first user message").isCodexThreadReadIncludeTurnsFallback())
      .isTrue()
    assertThat(CodexAppServerException("ephemeral threads do not support includeTurns").isCodexThreadReadIncludeTurnsFallback())
      .isTrue()
  }

  private fun <T> parseResponse(
    resultJson: String,
    defaultResult: T,
    resultParser: (tools.jackson.core.JsonParser) -> T,
  ): T {
    return protocol.parseResponse(
      payload = """{"id":"1","result":$resultJson}""",
      resultParser = resultParser,
      defaultResult = defaultResult,
    )
  }
}
