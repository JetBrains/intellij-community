// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.prompt.core.AgentPromptContextEnvelopeSummary
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptPayload
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PLAN_MODE_COMMAND
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_PLAN_MODE_OPTION
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchCompletionPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageMode
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageStartupPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageTimeoutPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentThreadRenameContext
import com.intellij.agent.workbench.sessions.core.providers.AgentThreadRenameHandler
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestApplication
class CodexAgentSessionProviderDescriptorTest {
    private val bridge = CodexAgentSessionProviderDescriptor()

    @Test
    fun buildResumeLaunchSpec() {
        assertThat(bridge.buildResumeLaunchSpec("thread-1").command)
            .containsExactly("codex", "-c", "check_for_update_on_startup=false", "resume", "thread-1")
    }

    @Test
    fun promptOptionsUseSharedPlanModeOption() {
        assertThat(bridge.promptOptions).containsExactly(AGENT_PROMPT_PROVIDER_PLAN_MODE_OPTION)
    }

    @Test
    fun buildNewSessionLaunchSpec() {
        assertThat(bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD).command)
            .containsExactly("codex", "-c", "check_for_update_on_startup=false")
        assertThat(bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.YOLO).command)
            .containsExactly("codex", "-c", "check_for_update_on_startup=false", "--full-auto")
    }

    @Test
    fun buildLaunchSpecWithInitialMessageForYoloCommand() {
        assertThat(
            bridge.buildLaunchSpecWithInitialMessage(
                baseLaunchSpec = bridge.buildNewSessionLaunchSpec(AgentSessionLaunchMode.YOLO),
                initialMessagePlan = AgentInitialMessagePlan(message = "-draft plan\nstep 2"),
            ).command
        )
            .containsExactly("codex", "-c", "check_for_update_on_startup=false", "--full-auto", "--", "-draft plan\nstep 2")
    }

    @Test
    fun buildLaunchSpecWithInitialMessageForResumeCommand() {
        val resumeLaunchSpec = bridge.buildResumeLaunchSpec("thread-1")

        assertThat(
            bridge.buildLaunchSpecWithInitialMessage(
                baseLaunchSpec = resumeLaunchSpec,
                initialMessagePlan = AgentInitialMessagePlan(message = "Summarize changes"),
            ).command
        )
            .containsExactly("codex", "-c", "check_for_update_on_startup=false", "resume", "thread-1", "--", "Summarize changes")
    }

    @Test
    fun supportsUnarchiveThread() {
        assertThat(bridge.supportsUnarchiveThread).isTrue()
    }

    @Test
    fun renameThreadHandlerUsesSharedBackendContract() {
        val renameHandler = bridge.threadRenameHandler

        assertThat(renameHandler).isInstanceOf(AgentThreadRenameHandler.Backend::class.java)
        renameHandler as AgentThreadRenameHandler.Backend
        assertThat(renameHandler.supportedContexts)
            .containsExactlyInAnyOrder(AgentThreadRenameContext.TREE_POPUP, AgentThreadRenameContext.EDITOR_TAB)
    }

  @Test
    fun composeInitialMessageWithoutContext() {
        val plan = bridge.buildInitialMessagePlan(
            AgentPromptInitialMessageRequest(prompt = "  Refactor this  ")
        )

        assertThat(plan.message).isEqualTo("Refactor this")
        assertThat(plan.mode).isEqualTo(AgentInitialMessageMode.STANDARD)
        assertThat(plan.startupPolicy).isEqualTo(AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND)
        assertThat(plan.timeoutPolicy).isEqualTo(AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK)
    }

    @Test
    fun composeInitialMessageUsesPlainPromptBodyWhenOptionIsEnabled() {
        val plan = bridge.buildInitialMessagePlan(
            AgentPromptInitialMessageRequest(
                prompt = "Refactor this",
                providerOptionIds = setOf(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE),
            )
        )

        assertThat(plan.message).isEqualTo("Refactor this")
        assertThat(plan.mode).isEqualTo(AgentInitialMessageMode.PLAN)
    }

    @Test
    fun composeInitialMessageStripsManualPlanCommandPrefix() {
        val plan = bridge.buildInitialMessagePlan(
            AgentPromptInitialMessageRequest(
                prompt = " /plan Refactor this ",
            )
        )

        assertThat(plan.message).isEqualTo("Refactor this")
        assertThat(plan.mode).isEqualTo(AgentInitialMessageMode.PLAN)
    }

    @Test
    fun planModeBuildsSplitPostStartDispatchSteps() {
        val steps = bridge.buildPostStartDispatchSteps(
            AgentInitialMessagePlan(
                message = "Refactor this",
                mode = AgentInitialMessageMode.PLAN,
                timeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
            )
        )

        assertThat(steps).containsExactly(
            com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchStep(
                text = AGENT_PROMPT_PLAN_MODE_COMMAND,
                timeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
                completionPolicy = AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY,
            ),
            com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchStep(
                text = "Refactor this",
                timeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
            ),
        )
    }

    @Test
    fun initialMessagePlanPoliciesDependOnPlanModeAndCommand() {
        val defaultPlan = bridge.buildInitialMessagePlan(
            AgentPromptInitialMessageRequest(prompt = "Refactor this")
        )
        assertThat(defaultPlan.mode).isEqualTo(AgentInitialMessageMode.STANDARD)
        assertThat(defaultPlan.startupPolicy).isEqualTo(AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND)
        assertThat(defaultPlan.timeoutPolicy).isEqualTo(AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK)

        val planModePlan = bridge.buildInitialMessagePlan(
            AgentPromptInitialMessageRequest(
                prompt = "Refactor this",
                providerOptionIds = setOf(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE),
            )
        )
        assertThat(planModePlan.mode).isEqualTo(AgentInitialMessageMode.PLAN)
        assertThat(planModePlan.startupPolicy).isEqualTo(AgentInitialMessageStartupPolicy.POST_START_ONLY)
        assertThat(planModePlan.timeoutPolicy).isEqualTo(AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS)

        val plannerPlan = bridge.buildInitialMessagePlan(
            AgentPromptInitialMessageRequest(prompt = "/planner follow-up")
        )
        assertThat(plannerPlan.mode).isEqualTo(AgentInitialMessageMode.STANDARD)
        assertThat(plannerPlan.timeoutPolicy).isEqualTo(AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK)

        val manualPlanCommand = bridge.buildInitialMessagePlan(
            AgentPromptInitialMessageRequest(prompt = "/plan from manual input")
        )
        assertThat(manualPlanCommand.mode).isEqualTo(AgentInitialMessageMode.PLAN)
        assertThat(manualPlanCommand.message).isEqualTo("from manual input")
        assertThat(manualPlanCommand.startupPolicy).isEqualTo(AgentInitialMessageStartupPolicy.POST_START_ONLY)
        assertThat(manualPlanCommand.timeoutPolicy).isEqualTo(AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS)
    }

    @Test
    fun composeInitialMessageUsesCompactContextBlock() {
        val message = messageFor(
            bridge,
            AgentPromptInitialMessageRequest(
                prompt = "Refactor this",
                contextItems = listOf(
                    AgentPromptContextItem(
                        rendererId = AgentPromptContextRendererIds.SNIPPET,
                        title = "Selection",
                        body = "val answer = 42",
                        source = "editor",
                    )
                ),
                contextEnvelopeSummary = AgentPromptContextEnvelopeSummary(
                    softCapChars = 12_000,
                    softCapExceeded = true,
                    autoTrimApplied = false,
                ),
            )
        )

        assertThat(message).startsWith("Refactor this\n\n### IDE Context")
        assertThat(message).contains("soft-cap: limit=12000 auto-trim=no")
        assertThat(message).contains("snippet")
        assertThat(message).doesNotContain("lang=")
        assertThat(message).contains("```\nval answer = 42\n```")
        assertThat(message).doesNotContain("```text")
        assertThat(message).contains("val answer = 42")
        assertThat(message).doesNotContain("Metadata:")
        assertThat(message).doesNotContain("####")
        assertThat(message).doesNotContain("Items:")
        assertThat(message).doesNotContain("<context_envelope>")
        assertThat(message).doesNotContain("<context_item>")
        assertThat(message).doesNotContain("\"schema\"")
    }

    @Test
    fun composeInitialMessageUsesSnippetLanguageWhenProvided() {
        val message = messageFor(
            bridge,
            AgentPromptInitialMessageRequest(
                prompt = "Refactor this",
                contextItems = listOf(
                    AgentPromptContextItem(
                        rendererId = AgentPromptContextRendererIds.SNIPPET,
                        title = "Selection",
                        body = "val answer = 42",
                        payload = AgentPromptPayload.obj(
                            "language" to AgentPromptPayload.str("JAVA"),
                        ),
                        source = "editor",
                    )
                ),
            )
        )

        assertThat(message).doesNotContain("lang=")
        assertThat(message).contains("```java\nval answer = 42\n```")
    }

    @Test
    fun composeInitialMessageOmitsSnippetLanguageForInvalidValue() {
        val invalidLanguage = messageFor(
            bridge,
            AgentPromptInitialMessageRequest(
                prompt = "Refactor this",
                contextItems = listOf(
                    AgentPromptContextItem(
                        rendererId = AgentPromptContextRendererIds.SNIPPET,
                        title = "Selection",
                        body = "val answer = 42",
                        payload = AgentPromptPayload.obj(
                            "language" to AgentPromptPayload.str("java script!"),
                        ),
                        source = "editor",
                    )
                ),
            )
        )

        assertThat(invalidLanguage).doesNotContain("lang=")
        assertThat(invalidLanguage).contains("```\nval answer = 42\n```")
        assertThat(invalidLanguage).doesNotContain("```java")
    }

    @Test
    fun composeInitialMessageResolvesRelativePathsAgainstProjectRoot() {
        val projectRoot = Path.of("/work/project")
        val expectedFile = projectRoot.resolve("src/Main.java").normalize().toString()
        val expectedPathFile = projectRoot.resolve("src/App.kt").normalize().toString()
        val expectedPathDir = projectRoot.resolve("src").normalize().toString()

        val message = messageFor(
            bridge,
            AgentPromptInitialMessageRequest(
                prompt = "Review context",
                projectPath = projectRoot.toString(),
                contextItems = listOf(
                    AgentPromptContextItem(
                        rendererId = AgentPromptContextRendererIds.FILE,
                        title = "Current File",
                        body = "src/Main.java",
                        payload = AgentPromptPayload.obj(
                            "path" to AgentPromptPayload.str("src/Main.java"),
                        ),
                        source = "editor",
                    ),
                    AgentPromptContextItem(
                        rendererId = AgentPromptContextRendererIds.PATHS,
                        title = "Selection",
                        body = "file: src/App.kt\ndir: src",
                        payload = AgentPromptPayload.obj(
                            "entries" to AgentPromptPayload.arr(
                                AgentPromptPayload.obj(
                                    "kind" to AgentPromptPayload.str("file"),
                                    "path" to AgentPromptPayload.str("src/App.kt"),
                                ),
                                AgentPromptPayload.obj(
                                    "kind" to AgentPromptPayload.str("dir"),
                                    "path" to AgentPromptPayload.str("src"),
                                ),
                            ),
                        ),
                        source = "projectView",
                    ),
                ),
            )
        )

        assertThat(message).contains("file: $expectedFile")
        assertThat(message).contains("paths:")
        assertThat(message).contains(expectedPathFile)
        assertThat(message).contains(expectedPathDir)
    }

    @Test
    fun composeInitialMessageMarksUnresolvedRelativePathWithoutProjectRoot() {
        val message = messageFor(
            bridge,
            AgentPromptInitialMessageRequest(
                prompt = "Review context",
                contextItems = listOf(
                    AgentPromptContextItem(
                        rendererId = AgentPromptContextRendererIds.FILE,
                        title = "Current File",
                        body = "src/Main.java",
                        payload = AgentPromptPayload.obj(
                            "path" to AgentPromptPayload.str("src/Main.java"),
                        ),
                        source = "editor",
                    )
                ),
            )
        )

        assertThat(message).contains("file: src/Main.java [path-unresolved]")
    }
}

private fun messageFor(bridge: CodexAgentSessionProviderDescriptor, request: AgentPromptInitialMessageRequest): String {
    return checkNotNull(bridge.buildInitialMessagePlan(request).message)
}
