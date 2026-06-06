// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchAction
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchCompletionPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageTimeoutPolicy
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.vfs.VirtualFile
import org.jdom.Element

internal data class AgentChatFileEditorState(
  @JvmField val snapshot: AgentChatTabSnapshot?,
  @JvmField val startupIntent: AgentChatStartupIntent? = null,
) : FileEditorState {
  override fun canBeMergedWith(otherState: FileEditorState, level: FileEditorStateLevel): Boolean = this == otherState
}

internal fun readAgentChatFileEditorState(sourceElement: Element, file: VirtualFile): AgentChatFileEditorState {
  val tabKey = AgentChatTabKey.parsePath(file.path) ?: return AgentChatFileEditorState(null)
  val identity = AgentChatTabIdentity(
    projectHash = sourceElement.getAttributeValue(ATTR_PROJECT_HASH).orEmpty(),
    projectPath = sourceElement.getAttributeValue(ATTR_PROJECT_PATH).orEmpty(),
    threadIdentity = sourceElement.getAttributeValue(ATTR_THREAD_IDENTITY).orEmpty(),
    subAgentId = sourceElement.getAttributeValue(ATTR_SUB_AGENT_ID),
  )
  if (AgentChatTabKey.fromIdentity(identity) != tabKey) {
    return AgentChatFileEditorState(null)
  }

  val steps = readInitialMessageDispatchSteps(sourceElement)
  val snapshot = AgentChatTabSnapshot(
    tabKey = tabKey,
    identity = identity,
    runtime = AgentChatTabRuntime(
      threadId = sourceElement.getAttributeValue(ATTR_THREAD_ID).orEmpty(),
      threadTitle = sourceElement.getAttributeValue(ATTR_THREAD_TITLE).orEmpty(),
      threadActivity = parseEnum(sourceElement.getAttributeValue(ATTR_THREAD_ACTIVITY), AgentThreadActivity.READY),
      pendingCreatedAtMs = sourceElement.getAttributeLongValueOrNull(ATTR_PENDING_CREATED_AT_MS),
      pendingFirstInputAtMs = sourceElement.getAttributeLongValueOrNull(ATTR_PENDING_FIRST_INPUT_AT_MS),
      pendingLaunchMode = sourceElement.getAttributeValue(ATTR_PENDING_LAUNCH_MODE),
      launchMode = normalizeAgentChatLaunchMode(sourceElement.getAttributeValue(ATTR_LAUNCH_MODE)),
      newThreadRebindRequestedAtMs = sourceElement.getAttributeLongValueOrNull(ATTR_NEW_THREAD_REBIND_REQUESTED_AT_MS),
      initialMessageDispatchSteps = steps,
      initialMessageDispatchStepIndex = sourceElement.getAttributeIntValueOrNull(ATTR_INITIAL_MESSAGE_DISPATCH_STEP_INDEX)
                                          ?.coerceIn(0, steps.size) ?: 0,
      initialMessageToken = sourceElement.getAttributeValue(ATTR_INITIAL_MESSAGE_TOKEN),
      initialMessageSent = sourceElement.getAttributeValue(ATTR_INITIAL_MESSAGE_SENT)?.toBooleanStrictOrNull() ?: false,
    ),
  )
  val startupIntent = readStartupIntent(sourceElement, identity.threadIdentity)
  if (file is AgentChatVirtualFile) {
    file.updateRestoreOnRestart(true)
    file.updateFromResolution(AgentChatTabResolution.Resolved(snapshot))
    file.updateStartupIntent(startupIntent)
  }
  return AgentChatFileEditorState(
    snapshot = snapshot,
    startupIntent = startupIntent,
  )
}

internal fun writeAgentChatFileEditorState(state: AgentChatFileEditorState, targetElement: Element) {
  val snapshot = state.snapshot ?: return
  val identity = snapshot.identity
  val runtime = snapshot.runtime
  targetElement.setAttribute(ATTR_VERSION, STATE_VERSION.toString())
  targetElement.setAttribute(ATTR_PROJECT_HASH, identity.projectHash)
  targetElement.setAttribute(ATTR_PROJECT_PATH, identity.projectPath)
  targetElement.setAttribute(ATTR_THREAD_IDENTITY, identity.threadIdentity)
  targetElement.setNullableAttribute(ATTR_SUB_AGENT_ID, identity.subAgentId)
  targetElement.setAttribute(ATTR_THREAD_ID, runtime.threadId)
  targetElement.setAttribute(ATTR_THREAD_TITLE, runtime.threadTitle)
  targetElement.setAttribute(ATTR_THREAD_ACTIVITY, runtime.threadActivity.name)
  targetElement.setNullableAttribute(ATTR_PENDING_CREATED_AT_MS, runtime.pendingCreatedAtMs?.toString())
  targetElement.setNullableAttribute(ATTR_PENDING_FIRST_INPUT_AT_MS, runtime.pendingFirstInputAtMs?.toString())
  targetElement.setNullableAttribute(ATTR_PENDING_LAUNCH_MODE, runtime.pendingLaunchMode)
  targetElement.setNullableAttribute(ATTR_LAUNCH_MODE, runtime.launchMode)
  targetElement.setNullableAttribute(ATTR_NEW_THREAD_REBIND_REQUESTED_AT_MS, runtime.newThreadRebindRequestedAtMs?.toString())
  targetElement.setAttribute(ATTR_INITIAL_MESSAGE_DISPATCH_STEP_INDEX, runtime.initialMessageDispatchStepIndex.toString())
  targetElement.setNullableAttribute(ATTR_INITIAL_MESSAGE_TOKEN, runtime.initialMessageToken)
  targetElement.setAttribute(ATTR_INITIAL_MESSAGE_SENT, runtime.initialMessageSent.toString())
  writeStartupIntent(state.startupIntent, targetElement)
  if (runtime.initialMessageDispatchSteps.isNotEmpty()) {
    targetElement.addContent(Element(ELEMENT_INITIAL_MESSAGE_DISPATCH_STEPS).also { stepsElement ->
      for ((text, timeoutPolicy, completionPolicy, action) in runtime.initialMessageDispatchSteps) {
        stepsElement.addContent(Element(ELEMENT_INITIAL_MESSAGE_DISPATCH_STEP).also { stepElement ->
          stepElement.setAttribute(ATTR_TIMEOUT_POLICY, timeoutPolicy.name)
          stepElement.setAttribute(ATTR_COMPLETION_POLICY, completionPolicy.name)
          stepElement.setAttribute(ATTR_ACTION, action.name)
          stepElement.setText(text)
        })
      }
    })
  }
}

private fun readStartupIntent(sourceElement: Element, threadIdentity: String): AgentChatStartupIntent? {
  val kind = sourceElement.getAttributeValue(ATTR_STARTUP_KIND) ?: return null
  if (kind != STARTUP_KIND_NEW_SESSION) {
    return null
  }
  val provider = sourceElement.getAttributeValue(ATTR_STARTUP_PROVIDER)
                   ?.let(AgentSessionProvider::fromOrNull)
                 ?: pendingProviderForThreadIdentity(threadIdentity)
                 ?: return null
  return AgentChatStartupIntent.NewSession(
    provider = provider,
    launchMode = parseEnum(sourceElement.getAttributeValue(ATTR_STARTUP_LAUNCH_MODE), AgentSessionLaunchMode.STANDARD),
  )
}

private fun writeStartupIntent(startupIntent: AgentChatStartupIntent?, targetElement: Element) {
  when (startupIntent) {
    is AgentChatStartupIntent.NewSession -> {
      targetElement.setAttribute(ATTR_STARTUP_KIND, STARTUP_KIND_NEW_SESSION)
      targetElement.setAttribute(ATTR_STARTUP_PROVIDER, startupIntent.provider.value)
      targetElement.setAttribute(ATTR_STARTUP_LAUNCH_MODE, startupIntent.launchMode.name)
    }
    null -> Unit
  }
}

private fun readInitialMessageDispatchSteps(sourceElement: Element): List<AgentInitialMessageDispatchStep> {
  return sourceElement.getChild(ELEMENT_INITIAL_MESSAGE_DISPATCH_STEPS)
    ?.getChildren(ELEMENT_INITIAL_MESSAGE_DISPATCH_STEP)
    ?.mapNotNull { stepElement ->
      val action = parseEnum(stepElement.getAttributeValue(ATTR_ACTION), AgentInitialMessageDispatchAction.SEND_TEXT)
      val text = stepElement.textTrim
      if (action == AgentInitialMessageDispatchAction.SEND_TEXT && text.isEmpty()) {
        return@mapNotNull null
      }
      AgentInitialMessageDispatchStep(
        text = text,
        timeoutPolicy = parseEnum(stepElement.getAttributeValue(ATTR_TIMEOUT_POLICY),
                                  AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK),
        completionPolicy = parseEnum(stepElement.getAttributeValue(ATTR_COMPLETION_POLICY),
                                      AgentInitialMessageDispatchCompletionPolicy.IMMEDIATE),
        action = action,
      )
    }
    .orEmpty()
}

private inline fun <reified T : Enum<T>> parseEnum(value: String?, defaultValue: T): T {
  return runCatching { enumValueOf<T>(value.orEmpty()) }.getOrDefault(defaultValue)
}

private fun Element.getAttributeLongValueOrNull(name: String): Long? = getAttributeValue(name)?.toLongOrNull()

private fun Element.getAttributeIntValueOrNull(name: String): Int? = getAttributeValue(name)?.toIntOrNull()

private fun Element.setNullableAttribute(name: String, value: String?) {
  if (value != null) {
    setAttribute(name, value)
  }
}

private const val STATE_VERSION = 3
private const val ATTR_VERSION = "version"
private const val ATTR_PROJECT_HASH = "projectHash"
private const val ATTR_PROJECT_PATH = "projectPath"
private const val ATTR_THREAD_IDENTITY = "threadIdentity"
private const val ATTR_SUB_AGENT_ID = "subAgentId"
private const val ATTR_THREAD_ID = "threadId"
private const val ATTR_THREAD_TITLE = "threadTitle"
private const val ATTR_THREAD_ACTIVITY = "threadActivity"
private const val ATTR_PENDING_CREATED_AT_MS = "pendingCreatedAtMs"
private const val ATTR_PENDING_FIRST_INPUT_AT_MS = "pendingFirstInputAtMs"
private const val ATTR_PENDING_LAUNCH_MODE = "pendingLaunchMode"
private const val ATTR_LAUNCH_MODE = "launchMode"
private const val ATTR_NEW_THREAD_REBIND_REQUESTED_AT_MS = "newThreadRebindRequestedAtMs"
private const val ATTR_INITIAL_MESSAGE_DISPATCH_STEP_INDEX = "initialMessageDispatchStepIndex"
private const val ATTR_INITIAL_MESSAGE_TOKEN = "initialMessageToken"
private const val ATTR_INITIAL_MESSAGE_SENT = "initialMessageSent"
private const val ATTR_STARTUP_KIND = "startupKind"
private const val ATTR_STARTUP_PROVIDER = "startupProvider"
private const val ATTR_STARTUP_LAUNCH_MODE = "startupLaunchMode"
private const val ATTR_TIMEOUT_POLICY = "timeoutPolicy"
private const val ATTR_COMPLETION_POLICY = "completionPolicy"
private const val ATTR_ACTION = "action"
private const val ELEMENT_INITIAL_MESSAGE_DISPATCH_STEPS = "initialMessageDispatchSteps"
private const val ELEMENT_INITIAL_MESSAGE_DISPATCH_STEP = "step"
private const val STARTUP_KIND_NEW_SESSION = "newSession"
