// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchCompletionPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageTimeoutPolicy
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.TestOnly
import java.nio.file.Files
import kotlin.time.Duration.Companion.minutes

private const val AGENT_CHAT_TABS_STATE_VERSION = 6
private const val AGENT_CHAT_TABS_STATE_TTL_MILLIS = 30L * 24 * 60 * 60 * 1000
private const val AGENT_CHAT_LEGACY_METADATA_DIR_NAME = "agent-workbench-chat-frame"
private const val AGENT_CHAT_LEGACY_METADATA_TABS_DIR_NAME = "tabs"

private val LOG = logger<AgentChatTabsStateService>()

@Service(Service.Level.APP)
@State(name = "AgentChatTabsState", storages = [Storage(StoragePathMacros.CACHE_FILE)])
internal class AgentChatTabsStateService(scope: CoroutineScope?)
  : SerializablePersistentStateComponent<AgentChatTabsState>(AgentChatTabsState()) {

  @Volatile
  private var versionMismatchForcedForTests: Boolean = false

  init {
    scope?.launch {
      delay(2.minutes)

      runCatching {
        withContext(Dispatchers.IO) {
          deleteLegacyMetadataDirectory()
        }
        pruneStale()
      }.onFailure { t ->
        LOG.debug("Failed to initialize Agent Chat tab state service", t)
      }
    }
  }

  fun load(tabKey: AgentChatTabKey): AgentChatTabSnapshot? {
    if (hasVersionMismatch()) {
      return null
    }
    val entry = state.tabsByKey[tabKey.value] ?: return null
    if (isExpired(entry.updatedAt)) {
      delete(tabKey)
      return null
    }
    return entry.toSnapshot(tabKey)
  }

  fun load(tabKey: String): AgentChatTabSnapshot? {
    return AgentChatTabKey.parse(tabKey)?.let(::load)
  }

  fun upsert(snapshot: AgentChatTabSnapshot) {
    val now = System.currentTimeMillis()
    updateState { current ->
      val updatedTabs = normalizeTabsForWrite(current).toMutableMap()
      updatedTabs.put(snapshot.tabKey.value, snapshot.toPersisted(now))
      current.copy(
        version = AGENT_CHAT_TABS_STATE_VERSION,
        tabsByKey = updatedTabs,
      )
    }
  }

  fun delete(tabKey: AgentChatTabKey): Boolean {
    var deleted = false

    updateState { current ->
      val versionMismatch = hasVersionMismatch(current)
      val baseTabs = normalizeTabsForWrite(current)
      deleted = tabKey.value in baseTabs
      if (!deleted && !versionMismatch) {
        return@updateState current
      }

      val updatedTabs = baseTabs.toMutableMap()
      updatedTabs.remove(tabKey.value)
      current.copy(
        version = AGENT_CHAT_TABS_STATE_VERSION,
        tabsByKey = updatedTabs,
      )
    }
    return deleted
  }

  fun delete(tabKey: String): Boolean {
    return AgentChatTabKey.parse(tabKey)?.let(::delete) ?: false
  }

  fun deleteByThread(projectPath: String, threadIdentity: String, subAgentId: String? = null): Int {
    return deleteByThreadWithKeys(projectPath, threadIdentity, subAgentId).deletedKeys.size
  }

  fun deleteByThreadWithKeys(
    projectPath: String,
    threadIdentity: String,
    subAgentId: String? = null,
  ): AgentChatDeleteByThreadResult {
    val normalizedProjectPath = normalizeAgentWorkbenchPath(projectPath)
    var keysToDelete = emptyList<String>()

    updateState { current ->
      val versionMismatch = hasVersionMismatch(current)
      val baseTabs = normalizeTabsForWrite(current)
      keysToDelete = baseTabs.entries
        .filter { (_, tab) ->
          normalizeAgentWorkbenchPath(tab.projectPath) == normalizedProjectPath &&
          tab.threadIdentity == threadIdentity &&
          (subAgentId == null || tab.subAgentId == subAgentId)
        }
        .map { (key, _) -> key }

      if (keysToDelete.isEmpty() && !versionMismatch) {
        return@updateState current
      }

      val updatedTabs = baseTabs.toMutableMap()
      keysToDelete.forEach(updatedTabs::remove)
      current.copy(
        version = AGENT_CHAT_TABS_STATE_VERSION,
        tabsByKey = updatedTabs,
      )
    }
    return AgentChatDeleteByThreadResult(keysToDelete)
  }

  fun pruneStale() {
    if (hasVersionMismatch()) {
      return
    }
    val now = System.currentTimeMillis()
    val filtered = state.tabsByKey.filterValues { tab -> !isExpired(tab.updatedAt, now) }
    if (filtered.size == state.tabsByKey.size && state.version == AGENT_CHAT_TABS_STATE_VERSION) {
      return
    }

    updateState {
      AgentChatTabsState(
        version = AGENT_CHAT_TABS_STATE_VERSION,
        tabsByKey = filtered,
      )
    }
  }

  fun hasVersionMismatch(): Boolean = hasVersionMismatch(state)

  @TestOnly
  internal fun forceVersionMismatchForTests(value: Boolean) {
    versionMismatchForcedForTests = value
  }

  private fun hasVersionMismatch(current: AgentChatTabsState): Boolean {
    return versionMismatchForcedForTests || current.version != AGENT_CHAT_TABS_STATE_VERSION
  }

  private fun normalizeTabsForWrite(current: AgentChatTabsState): Map<String, PersistedAgentChatTabState> {
    return if (hasVersionMismatch(current)) {
      emptyMap()
    }
    else {
      current.tabsByKey
    }
  }
}

@Serializable
internal data class AgentChatTabsState(
  @JvmField val version: Int = AGENT_CHAT_TABS_STATE_VERSION,
  @JvmField val tabsByKey: Map<String, PersistedAgentChatTabState> = emptyMap(),
)

internal data class AgentChatDeleteByThreadResult(
  @JvmField val deletedKeys: List<String>,
)

@Serializable
internal data class PersistedAgentChatTabState(
  @JvmField val projectHash: String,
  @JvmField val projectPath: String,
  @JvmField val threadIdentity: String,
  @JvmField val subAgentId: String?,
  @JvmField val threadId: String,
  @JvmField val shellCommand: List<String>,
  @JvmField val shellEnvVariables: Map<String, String> = emptyMap(),
  @JvmField val lastKnownTitle: String,
  @JvmField val lastKnownActivity: String = AgentThreadActivity.READY.name,
  @JvmField val pendingCreatedAtMs: Long? = null,
  @JvmField val pendingFirstInputAtMs: Long? = null,
  @JvmField val pendingLaunchMode: String? = null,
  @JvmField val newThreadRebindRequestedAtMs: Long? = null,
  @JvmField val initialMessageDispatchSteps: List<PersistedAgentChatInitialMessageDispatchStep> = emptyList(),
  @JvmField val initialMessageDispatchStepIndex: Int = 0,
  @JvmField val initialComposedMessage: String? = null,
  @JvmField val initialMessageToken: String? = null,
  @JvmField val initialMessageSent: Boolean = false,
  @JvmField val initialMessageTimeoutPolicy: String = AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK.name,
  @JvmField val updatedAt: Long,
)

@Serializable
internal data class PersistedAgentChatInitialMessageDispatchStep(
  @JvmField val text: String,
  @JvmField val timeoutPolicy: String = AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK.name,
  @JvmField val completionPolicy: String = AgentInitialMessageDispatchCompletionPolicy.IMMEDIATE.name,
)

private fun deleteLegacyMetadataDirectory() {
  val tabsDir = PathManager.getConfigDir()
    .resolve(AGENT_CHAT_LEGACY_METADATA_DIR_NAME)
    .resolve(AGENT_CHAT_LEGACY_METADATA_TABS_DIR_NAME)

  if (!Files.isDirectory(tabsDir)) {
    return
  }

  runCatching {
    Files.newDirectoryStream(tabsDir).use { files ->
      for (file in files) {
        Files.deleteIfExists(file)
      }
    }
    Files.deleteIfExists(tabsDir)
  }.onFailure { e ->
    LOG.debug("Failed to remove legacy Agent Chat metadata directory $tabsDir", e)
  }
}

private fun isExpired(updatedAt: Long): Boolean {
  return isExpired(updatedAt, System.currentTimeMillis())
}

private fun isExpired(updatedAt: Long, now: Long): Boolean {
  if (updatedAt <= 0) {
    return true
  }
  return now - updatedAt > AGENT_CHAT_TABS_STATE_TTL_MILLIS
}

private fun PersistedAgentChatTabState.toSnapshot(tabKey: AgentChatTabKey): AgentChatTabSnapshot {
  val resolvedPendingCreatedAtMs = pendingCreatedAtMs
    ?: updatedAt.takeIf { it > 0L && isPersistedPendingThreadIdentity(threadIdentity) }
  val runtimeSteps = if (initialMessageDispatchSteps.isNotEmpty()) {
    initialMessageDispatchSteps.mapNotNull(PersistedAgentChatInitialMessageDispatchStep::toRuntime)
  }
  else {
    initialComposedMessage
      ?.trim()
      ?.takeIf { it.isNotEmpty() }
      ?.let { message ->
        listOf(
          AgentInitialMessageDispatchStep(
            text = message,
            timeoutPolicy = parseInitialMessageTimeoutPolicy(initialMessageTimeoutPolicy),
          )
        )
      }
      .orEmpty()
  }
  val runtimeStepIndex = when {
    runtimeSteps.isEmpty() -> 0
    initialMessageSent -> runtimeSteps.size
    initialMessageDispatchSteps.isEmpty() -> 0
    else -> initialMessageDispatchStepIndex.coerceIn(0, runtimeSteps.size)
  }
  return AgentChatTabSnapshot(
    tabKey = tabKey,
    identity = AgentChatTabIdentity(
      projectHash = projectHash,
      projectPath = projectPath,
      threadIdentity = threadIdentity,
      subAgentId = subAgentId,
    ),
    runtime = AgentChatTabRuntime(
      threadId = threadId,
      threadTitle = lastKnownTitle,
      shellCommand = shellCommand,
      shellEnvVariables = shellEnvVariables,
      threadActivity = parseThreadActivity(lastKnownActivity),
      pendingCreatedAtMs = resolvedPendingCreatedAtMs,
      pendingFirstInputAtMs = pendingFirstInputAtMs,
      pendingLaunchMode = pendingLaunchMode,
      newThreadRebindRequestedAtMs = newThreadRebindRequestedAtMs,
      initialMessageDispatchSteps = runtimeSteps,
      initialMessageDispatchStepIndex = runtimeStepIndex,
      initialMessageToken = initialMessageToken,
      initialMessageSent = initialMessageSent,
    ),
  )
}

private fun AgentChatTabSnapshot.toPersisted(updatedAt: Long): PersistedAgentChatTabState {
  val legacySingleStep = runtime.initialMessageDispatchSteps.singleOrNull()
    ?.takeIf { step ->
      runtime.initialMessageDispatchStepIndex == 0 &&
      step.completionPolicy == AgentInitialMessageDispatchCompletionPolicy.IMMEDIATE
    }
  return PersistedAgentChatTabState(
    projectHash = identity.projectHash,
    projectPath = identity.projectPath,
    threadIdentity = identity.threadIdentity,
    subAgentId = identity.subAgentId,
    threadId = runtime.threadId,
    shellCommand = runtime.shellCommand,
    shellEnvVariables = runtime.shellEnvVariables,
    lastKnownTitle = runtime.threadTitle,
    lastKnownActivity = runtime.threadActivity.name,
    pendingCreatedAtMs = runtime.pendingCreatedAtMs,
    pendingFirstInputAtMs = runtime.pendingFirstInputAtMs,
    pendingLaunchMode = runtime.pendingLaunchMode,
    newThreadRebindRequestedAtMs = runtime.newThreadRebindRequestedAtMs,
    initialMessageDispatchSteps = runtime.initialMessageDispatchSteps.map(AgentInitialMessageDispatchStep::toPersisted),
    initialMessageDispatchStepIndex = runtime.initialMessageDispatchStepIndex,
    initialComposedMessage = legacySingleStep?.text,
    initialMessageToken = runtime.initialMessageToken,
    initialMessageSent = runtime.initialMessageSent,
    initialMessageTimeoutPolicy = legacySingleStep?.timeoutPolicy?.name ?: AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK.name,
    updatedAt = updatedAt,
  )
}

private fun PersistedAgentChatInitialMessageDispatchStep.toRuntime(): AgentInitialMessageDispatchStep? {
  val normalizedText = text.trim()
  if (normalizedText.isEmpty()) {
    return null
  }
  return AgentInitialMessageDispatchStep(
    text = normalizedText,
    timeoutPolicy = parseInitialMessageTimeoutPolicy(timeoutPolicy),
    completionPolicy = parseInitialMessageDispatchCompletionPolicy(completionPolicy),
  )
}

private fun AgentInitialMessageDispatchStep.toPersisted(): PersistedAgentChatInitialMessageDispatchStep {
  return PersistedAgentChatInitialMessageDispatchStep(
    text = text,
    timeoutPolicy = timeoutPolicy.name,
    completionPolicy = completionPolicy.name,
  )
}

private fun isPersistedPendingThreadIdentity(threadIdentity: String): Boolean {
  val separator = threadIdentity.indexOf(':')
  if (separator <= 0 || separator == threadIdentity.lastIndex) {
    return false
  }
  return threadIdentity.substring(separator + 1).startsWith("new-")
}

private fun parseThreadActivity(value: String): AgentThreadActivity {
  return runCatching { AgentThreadActivity.valueOf(value) }
    .getOrDefault(AgentThreadActivity.READY)
}

private fun parseInitialMessageTimeoutPolicy(value: String): AgentInitialMessageTimeoutPolicy {
  return runCatching { AgentInitialMessageTimeoutPolicy.valueOf(value) }
    .getOrDefault(AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK)
}

private fun parseInitialMessageDispatchCompletionPolicy(value: String): AgentInitialMessageDispatchCompletionPolicy {
  return runCatching { AgentInitialMessageDispatchCompletionPolicy.valueOf(value) }
    .getOrDefault(AgentInitialMessageDispatchCompletionPolicy.IMMEDIATE)
}
