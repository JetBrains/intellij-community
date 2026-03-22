// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.AgentSubAgent
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLaunchRequest
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PLAN_MODE_COMMAND
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchCompletionPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchPlan
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.InMemoryAgentSessionProviderRegistry
import com.intellij.agent.workbench.sessions.core.providers.isPlanModeCommand
import com.intellij.agent.workbench.sessions.service.AgentSessionChatOpenExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

fun existingThreadPromptLaunchRequest(
  provider: AgentSessionProvider,
  projectPath: String,
  threadId: String,
  prompt: String = "Refactor selected code",
  contextTitle: String = "Project",
  contextBody: String = "project-a",
  planMode: Boolean = false,
): AgentPromptLaunchRequest {
  return AgentPromptLaunchRequest(
    provider = provider,
    projectPath = projectPath,
    launchMode = AgentSessionLaunchMode.STANDARD,
    initialMessageRequest = AgentPromptInitialMessageRequest(
      prompt = prompt,
      contextItems = listOf(
        AgentPromptContextItem(
          rendererId = "project",
          title = contextTitle,
          body = contextBody,
          source = "test",
        )
      ),
      providerOptionIds = if (planMode) setOf(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE) else emptySet(),
    ),
    targetThreadId = threadId,
    preferredDedicatedFrame = null,
  )
}

fun assertExistingThreadLaunchUsesPostStartDispatch(
  descriptor: AgentSessionProviderDescriptor,
  request: AgentPromptLaunchRequest,
  projectPath: String,
  threadId: String,
  projectName: String = "Project A",
) {
  val provider = descriptor.provider
  val chatOpenExecutor = RecordingChatOpenExecutor()
  AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
    runBlocking(Dispatchers.Default) {
      withTestServiceAndLaunch(
        sessionSourcesProvider = { listOf(descriptor.sessionSource) },
        projectEntriesProvider = { listOf(openTestProjectEntry(projectPath, projectName)) },
        chatOpenExecutor = chatOpenExecutor,
      ) { service, launchService ->
        service.refresh()
        waitForCondition {
          val project = service.state.value.projects.firstOrNull { it.path == projectPath } ?: return@waitForCondition false
          project.hasLoaded && project.threads.any { thread -> thread.id == threadId }
        }

        val result = launchService.launchPromptRequest(request)

        assertThat(result.launched).isTrue()
        assertThat(result.error).isNull()
        waitForCondition {
          chatOpenExecutor.openChatCalls.get() == 1
        }

        val openRequest = checkNotNull(chatOpenExecutor.lastOpenChatRequest.get())
        val initialMessagePlan = descriptor.buildInitialMessagePlan(request.initialMessageRequest)
        val expectedSteps = expectedExistingThreadDispatchSteps(provider, initialMessagePlan)

        assertThat(chatOpenExecutor.openNewChatCalls.get()).isZero()
        assertThat(openRequest.normalizedPath).isEqualTo(projectPath)
        assertThat(openRequest.thread.id).isEqualTo(threadId)
        assertThat(openRequest.thread.provider).isEqualTo(provider)
        assertThat(openRequest.subAgent).isNull()
        assertThat(openRequest.startupLaunchSpecOverride).isNull()
        assertThat(openRequest.postStartDispatchSteps).containsExactlyElementsOf(expectedSteps)
        assertThat(openRequest.initialMessageToken).isNotNull()
        if (expectedSteps.size == 1) {
          assertThat(openRequest.initialComposedMessage).isEqualTo(expectedSteps.single().text)
        }
        else {
          assertThat(openRequest.initialComposedMessage).isNull()
        }
      }
    }
  }
}

private fun expectedExistingThreadDispatchSteps(
  provider: AgentSessionProvider,
  initialMessagePlan: AgentInitialMessagePlan,
): List<AgentInitialMessageDispatchStep> {
  val message = checkNotNull(initialMessagePlan.message)
  if (provider != AgentSessionProvider.CODEX || !message.isPlanModeCommand()) {
    return listOf(
      AgentInitialMessageDispatchStep(
        text = message,
        timeoutPolicy = initialMessagePlan.timeoutPolicy,
      )
    )
  }

  val strippedPrompt = message.removePrefix(AGENT_PROMPT_PLAN_MODE_COMMAND).trim()
  return buildList {
    add(
      AgentInitialMessageDispatchStep(
        text = AGENT_PROMPT_PLAN_MODE_COMMAND,
        timeoutPolicy = initialMessagePlan.timeoutPolicy,
        completionPolicy = AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY,
      )
    )
    if (strippedPrompt.isNotEmpty()) {
      add(
        AgentInitialMessageDispatchStep(
          text = strippedPrompt,
          timeoutPolicy = initialMessagePlan.timeoutPolicy,
        )
      )
    }
  }
}

internal class RecordingChatOpenExecutor(
  private val onOpenChat: (suspend (OpenChatRequest, Int) -> Unit)? = null,
  private val onOpenNewChat: (suspend (OpenNewChatRequest, Int) -> Unit)? = null,
) : AgentSessionChatOpenExecutor {
  val openChatCalls: AtomicInteger = AtomicInteger(0)
  val openNewChatCalls: AtomicInteger = AtomicInteger(0)
  val openChatRequests: CopyOnWriteArrayList<OpenChatRequest> = CopyOnWriteArrayList()
  val lastOpenChatRequest: AtomicReference<OpenChatRequest?> = AtomicReference(null)
  val lastOpenNewChatRequest: AtomicReference<OpenNewChatRequest?> = AtomicReference(null)

  override suspend fun openChat(
    normalizedPath: String,
    thread: AgentSessionThread,
    subAgent: AgentSubAgent?,
    launchSpecOverride: AgentSessionTerminalLaunchSpec?,
    initialMessageDispatchPlan: AgentInitialMessageDispatchPlan,
  ) {
    val request = OpenChatRequest(
      normalizedPath = normalizedPath,
      thread = thread,
      subAgent = subAgent,
      launchSpecOverride = launchSpecOverride,
      startupLaunchSpecOverride = initialMessageDispatchPlan.startupLaunchSpecOverride,
      postStartDispatchSteps = initialMessageDispatchPlan.postStartDispatchSteps,
      initialMessageToken = initialMessageDispatchPlan.initialMessageToken,
    )
    val callIndex = openChatCalls.incrementAndGet()
    openChatRequests.add(request)
    lastOpenChatRequest.set(request)
    onOpenChat?.invoke(request, callIndex)
  }

  override suspend fun openNewChat(
    normalizedPath: String,
    identity: String,
    launchSpec: AgentSessionTerminalLaunchSpec,
    initialMessageDispatchPlan: AgentInitialMessageDispatchPlan,
    preferredDedicatedFrame: Boolean?,
  ) {
    val request = OpenNewChatRequest(
      normalizedPath = normalizedPath,
      identity = identity,
      launchSpec = launchSpec,
      startupLaunchSpecOverride = initialMessageDispatchPlan.startupLaunchSpecOverride,
      postStartDispatchSteps = initialMessageDispatchPlan.postStartDispatchSteps,
      initialMessageToken = initialMessageDispatchPlan.initialMessageToken,
      preferredDedicatedFrame = preferredDedicatedFrame,
    )
    val callIndex = openNewChatCalls.incrementAndGet()
    lastOpenNewChatRequest.set(request)
    onOpenNewChat?.invoke(request, callIndex)
  }
}

internal data class OpenChatRequest(
  @JvmField val normalizedPath: String,
  @JvmField val thread: AgentSessionThread,
  @JvmField val subAgent: AgentSubAgent?,
  @JvmField val launchSpecOverride: AgentSessionTerminalLaunchSpec?,
  @JvmField val startupLaunchSpecOverride: AgentSessionTerminalLaunchSpec?,
  @JvmField val postStartDispatchSteps: List<AgentInitialMessageDispatchStep>,
  @JvmField val initialMessageToken: String?,
) {
  val initialComposedMessage: String?
    get() = postStartDispatchSteps.singleOrNull()?.text
}

internal data class OpenNewChatRequest(
  @JvmField val normalizedPath: String,
  @JvmField val identity: String,
  @JvmField val launchSpec: AgentSessionTerminalLaunchSpec,
  @JvmField val startupLaunchSpecOverride: AgentSessionTerminalLaunchSpec?,
  @JvmField val postStartDispatchSteps: List<AgentInitialMessageDispatchStep>,
  @JvmField val initialMessageToken: String?,
  @JvmField val preferredDedicatedFrame: Boolean?,
) {
  val initialComposedMessage: String?
    get() = postStartDispatchSteps.singleOrNull()?.text
}
