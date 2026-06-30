// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.thread.view

import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.vfs.VirtualFile
import org.jdom.Element

internal data class AgentThreadViewFileEditorState(
  @JvmField val snapshot: AgentThreadViewTabSnapshot?,
  @JvmField val startupIntent: AgentThreadViewStartupIntent? = null,
) : FileEditorState {
  override fun canBeMergedWith(otherState: FileEditorState, level: FileEditorStateLevel): Boolean = this == otherState
}

internal fun readAgentThreadViewFileEditorState(sourceElement: Element, file: VirtualFile): AgentThreadViewFileEditorState {
  val tabKey = AgentThreadViewTabKey.parsePath(file.path) ?: return AgentThreadViewFileEditorState(null)
  val identity = AgentThreadViewTabIdentity(
    projectHash = sourceElement.getAttributeValue(ATTR_PROJECT_HASH).orEmpty(),
    projectPath = sourceElement.getAttributeValue(ATTR_PROJECT_PATH).orEmpty(),
    projectDirectory = sourceElement.getAttributeValue(ATTR_PROJECT_DIRECTORY),
    threadIdentity = sourceElement.getAttributeValue(ATTR_THREAD_IDENTITY).orEmpty(),
    subAgentId = sourceElement.getAttributeValue(ATTR_SUB_AGENT_ID),
  )
  if (AgentThreadViewTabKey.fromIdentity(identity) != tabKey) {
    return AgentThreadViewFileEditorState(null)
  }

  val snapshot = AgentThreadViewTabSnapshot(
    tabKey = tabKey,
    identity = identity,
    runtime = AgentThreadViewTabRuntime(
      threadId = sourceElement.getAttributeValue(ATTR_THREAD_ID).orEmpty(),
      threadTitle = sourceElement.getAttributeValue(ATTR_THREAD_TITLE).orEmpty(),
      threadActivity = parseEnum(sourceElement.getAttributeValue(ATTR_THREAD_ACTIVITY), AgentThreadActivity.READY),
      pendingCreatedAtMs = sourceElement.getAttributeLongValueOrNull(ATTR_PENDING_CREATED_AT_MS),
      pendingFirstInputAtMs = sourceElement.getAttributeLongValueOrNull(ATTR_PENDING_FIRST_INPUT_AT_MS),
      pendingLaunchMode = sourceElement.getAttributeValue(ATTR_PENDING_LAUNCH_MODE),
      launchMode = normalizeAgentThreadViewLaunchMode(sourceElement.getAttributeValue(ATTR_LAUNCH_MODE)),
      launchProfileId = sourceElement.getAttributeValue(ATTR_LAUNCH_PROFILE_ID),
      launchTargetId = sourceElement.getAttributeValue(ATTR_LAUNCH_TARGET_ID),
      surfaceId = normalizeAgentThreadViewSurfaceId(sourceElement.getAttributeValue(ATTR_SURFACE_ID)),
      newThreadRebindRequestedAtMs = sourceElement.getAttributeLongValueOrNull(ATTR_NEW_THREAD_REBIND_REQUESTED_AT_MS),
      // Prompt text, tokens, delivery state, and dispatch queues are live-session metadata. Do not restore them from editor state:
      // persisted prompt data is a privacy risk, and restoring queued terminal input can duplicate prompts.
      initialPromptRecord = null,
      terminalPromptDispatch = null,
    ),
  )
  val startupIntent = readStartupIntent(
    sourceElement = sourceElement,
    threadIdentity = identity.threadIdentity,
    pendingLaunchMode = snapshot.runtime.pendingLaunchMode,
  )
  if (file is AgentThreadViewVirtualFile) {
    file.updateRestoreOnRestart(true)
    file.updateFromResolution(AgentThreadViewTabResolution.Resolved(snapshot))
    file.updateStartupIntent(startupIntent)
  }
  return AgentThreadViewFileEditorState(
    snapshot = snapshot,
    startupIntent = startupIntent,
  )
}

internal fun writeAgentThreadViewFileEditorState(state: AgentThreadViewFileEditorState, targetElement: Element) {
  val snapshot = state.snapshot ?: return
  val identity = snapshot.identity
  val runtime = snapshot.runtime
  targetElement.setAttribute(ATTR_VERSION, STATE_VERSION.toString())
  targetElement.setAttribute(ATTR_PROJECT_HASH, identity.projectHash)
  targetElement.setAttribute(ATTR_PROJECT_PATH, identity.projectPath)
  targetElement.setNullableAttribute(ATTR_PROJECT_DIRECTORY, identity.projectDirectory)
  targetElement.setAttribute(ATTR_THREAD_IDENTITY, identity.threadIdentity)
  targetElement.setNullableAttribute(ATTR_SUB_AGENT_ID, identity.subAgentId)
  targetElement.setAttribute(ATTR_THREAD_ID, runtime.threadId)
  targetElement.setAttribute(ATTR_THREAD_TITLE, runtime.threadTitle)
  targetElement.setAttribute(ATTR_THREAD_ACTIVITY, runtime.threadActivity.name)
  targetElement.setNullableAttribute(ATTR_PENDING_CREATED_AT_MS, runtime.pendingCreatedAtMs?.toString())
  targetElement.setNullableAttribute(ATTR_PENDING_FIRST_INPUT_AT_MS, runtime.pendingFirstInputAtMs?.toString())
  targetElement.setNullableAttribute(ATTR_PENDING_LAUNCH_MODE, runtime.pendingLaunchMode)
  targetElement.setNullableAttribute(ATTR_LAUNCH_MODE, runtime.launchMode)
  targetElement.setNullableAttribute(ATTR_LAUNCH_PROFILE_ID, runtime.launchProfileId)
  targetElement.setNullableAttribute(ATTR_LAUNCH_TARGET_ID, runtime.launchTargetId)
  targetElement.setNullableAttribute(ATTR_SURFACE_ID, runtime.surfaceId)
  targetElement.setNullableAttribute(ATTR_NEW_THREAD_REBIND_REQUESTED_AT_MS, runtime.newThreadRebindRequestedAtMs?.toString())
  // Prompt text, tokens, delivery state, and terminal dispatch metadata are live-session-only and must not be persisted.
  writeStartupIntent(state.startupIntent, targetElement)
}

private fun readStartupIntent(
  sourceElement: Element,
  threadIdentity: String,
  pendingLaunchMode: String?,
): AgentThreadViewStartupIntent? {
  val kind = sourceElement.getAttributeValue(ATTR_STARTUP_KIND) ?: return null
  if (kind != STARTUP_KIND_NEW_SESSION) {
    return null
  }
  val provider = sourceElement.getAttributeValue(ATTR_STARTUP_PROVIDER)
                   ?.let(AgentSessionProvider::fromOrNull)
                 ?: pendingProviderForThreadIdentity(threadIdentity)
                 ?: return null
  return AgentThreadViewStartupIntent.NewSession(
    provider = provider,
    launchMode = parseEnum(sourceElement.getAttributeValue(ATTR_STARTUP_LAUNCH_MODE), parseAgentThreadViewLaunchMode(pendingLaunchMode)),
    launchProfileId = sourceElement.getAttributeValue(ATTR_STARTUP_LAUNCH_PROFILE_ID),
    launchTargetId = sourceElement.getAttributeValue(ATTR_STARTUP_LAUNCH_TARGET_ID),
    surfaceId = parseAgentThreadViewSurfaceId(sourceElement.getAttributeValue(ATTR_STARTUP_SURFACE_ID)),
  )
}

private fun writeStartupIntent(startupIntent: AgentThreadViewStartupIntent?, targetElement: Element) {
  when (startupIntent) {
    is AgentThreadViewStartupIntent.NewSession -> {
      targetElement.setAttribute(ATTR_STARTUP_KIND, STARTUP_KIND_NEW_SESSION)
      targetElement.setAttribute(ATTR_STARTUP_PROVIDER, startupIntent.provider.value)
      targetElement.setAttribute(ATTR_STARTUP_LAUNCH_MODE, startupIntent.launchMode.name)
      targetElement.setNullableAttribute(ATTR_STARTUP_LAUNCH_PROFILE_ID, startupIntent.launchProfileId)
      targetElement.setNullableAttribute(ATTR_STARTUP_LAUNCH_TARGET_ID, startupIntent.launchTargetId)
      targetElement.setNullableAttribute(ATTR_STARTUP_SURFACE_ID, startupIntent.surfaceId?.value)
    }
    null -> Unit
  }
}

private inline fun <reified T : Enum<T>> parseEnum(value: String?, defaultValue: T): T {
  return runCatching { enumValueOf<T>(value.orEmpty()) }.getOrDefault(defaultValue)
}

private fun Element.getAttributeLongValueOrNull(name: String): Long? = getAttributeValue(name)?.toLongOrNull()

private fun Element.setNullableAttribute(name: String, value: String?) {
  if (value != null) {
    setAttribute(name, value)
  }
}

private const val STATE_VERSION = 6
private const val ATTR_VERSION = "version"
private const val ATTR_PROJECT_HASH = "projectHash"
private const val ATTR_PROJECT_PATH = "projectPath"
private const val ATTR_PROJECT_DIRECTORY = "projectDirectory"
private const val ATTR_THREAD_IDENTITY = "threadIdentity"
private const val ATTR_SUB_AGENT_ID = "subAgentId"
private const val ATTR_THREAD_ID = "threadId"
private const val ATTR_THREAD_TITLE = "threadTitle"
private const val ATTR_THREAD_ACTIVITY = "threadActivity"
private const val ATTR_PENDING_CREATED_AT_MS = "pendingCreatedAtMs"
private const val ATTR_PENDING_FIRST_INPUT_AT_MS = "pendingFirstInputAtMs"
private const val ATTR_PENDING_LAUNCH_MODE = "pendingLaunchMode"
private const val ATTR_LAUNCH_MODE = "launchMode"
private const val ATTR_LAUNCH_PROFILE_ID = "launchProfileId"
private const val ATTR_LAUNCH_TARGET_ID = "launchTargetId"
private const val ATTR_SURFACE_ID = "surfaceId"
private const val ATTR_NEW_THREAD_REBIND_REQUESTED_AT_MS = "newThreadRebindRequestedAtMs"
private const val ATTR_STARTUP_KIND = "startupKind"
private const val ATTR_STARTUP_PROVIDER = "startupProvider"
private const val ATTR_STARTUP_LAUNCH_MODE = "startupLaunchMode"
private const val ATTR_STARTUP_LAUNCH_PROFILE_ID = "startupLaunchProfileId"
private const val ATTR_STARTUP_LAUNCH_TARGET_ID = "startupLaunchTargetId"
private const val ATTR_STARTUP_SURFACE_ID = "startupSurfaceId"
private const val STARTUP_KIND_NEW_SESSION = "newSession"
