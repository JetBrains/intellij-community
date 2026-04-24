// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.core

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-suggestions.spec.md

import com.dynatrace.hash4j.hashing.HashSink
import com.dynatrace.hash4j.hashing.HashValue128
import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

private const val PROMPT_SUGGESTION_IDLE_RETENTION_MS = 30_000L

private class AgentPromptSuggestionSessionServiceLog

private val LOG = logger<AgentPromptSuggestionSessionServiceLog>()

@Service(Service.Level.PROJECT)
class AgentPromptSuggestionSessionService internal constructor(
  private val serviceScope: CoroutineScope,
  private val generatorProvider: () -> AgentPromptSuggestionGenerator?,
  private val idleRetentionMs: Long,
) {
  @Suppress("unused")
  constructor(serviceScope: CoroutineScope) : this(
    serviceScope = serviceScope,
    generatorProvider = AgentPromptSuggestionGenerators::find,
    idleRetentionMs = PROMPT_SUGGESTION_IDLE_RETENTION_MS,
  )

  private val lock = Any()

  private var retainedSession: RetainedPromptSuggestionSession? = null

  fun attach(request: AgentPromptSuggestionRequest): AgentPromptSuggestionSubscription? {
    val requestKey = computePromptSuggestionRequestKey(request)
    synchronized(lock) {
      val existingSession = retainedSession
      if (existingSession?.requestKey == requestKey) {
        // Reuse same-fingerprint sessions while another attachment is still active, or after generation completed within the retention window.
        existingSession.attachments += 1
        existingSession.evictionJob?.cancel()
        existingSession.evictionJob = null
        return PromptSuggestionSubscription(existingSession, ::detach)
      }

      if (request.contextItems.isEmpty()) {
        return null
      }

      val generator = generatorProvider() ?: return null
      val replacedSession = retainedSession
      val newSession = createSession(requestKey, request, generator)
      newSession.attachments = 1
      retainedSession = newSession
      replacedSession?.cancel()
      return PromptSuggestionSubscription(newSession, ::detach)
    }
  }

  private fun createSession(
    requestKey: AgentPromptSuggestionRequestKey,
    request: AgentPromptSuggestionRequest,
    generator: AgentPromptSuggestionGenerator,
  ): RetainedPromptSuggestionSession {
    val candidates = MutableStateFlow<List<AgentPromptSuggestionCandidate>>(emptyList())
    val generationJob = serviceScope.launch {
      try {
        generator.generateSuggestions(request).collect { update ->
          candidates.value = update.candidates
        }
      }
      catch (t: Throwable) {
        if (t is CancellationException) {
          throw t
        }
        LOG.warn("Failed to load prompt suggestions", t)
      }
    }
    return RetainedPromptSuggestionSession(
      requestKey = requestKey,
      candidates = candidates,
      generationJob = generationJob,
    )
  }

  private fun detach(session: RetainedPromptSuggestionSession) {
    var cancelAbandonedSession = false
    synchronized(lock) {
      if (retainedSession !== session) {
        return
      }
      if (session.attachments > 0) {
        session.attachments -= 1
      }
      if (session.attachments != 0 || session.evictionJob != null) {
        return
      }
      if (!session.generationJob.isCompleted) {
        retainedSession = null
        cancelAbandonedSession = true
      }
      else {
        session.evictionJob = serviceScope.launch {
          delay(idleRetentionMs.milliseconds)
          evictIfIdle(session)
        }
      }
    }
    if (cancelAbandonedSession) {
      session.cancel()
    }
  }

  private fun evictIfIdle(session: RetainedPromptSuggestionSession) {
    synchronized(lock) {
      if (retainedSession !== session || session.attachments != 0) {
        return
      }
      retainedSession = null
      session.evictionJob = null
      session.cancel()
    }
  }
}

interface AgentPromptSuggestionSubscription {
  val currentCandidates: List<AgentPromptSuggestionCandidate>
  val updates: Flow<List<AgentPromptSuggestionCandidate>>

  fun close()
}

data class AgentPromptSuggestionRequestKey(
  @JvmField val targetModeId: String,
  @JvmField val projectPath: String?,
  @JvmField val contextFingerprint: HashValue128?,
)

fun computePromptSuggestionRequestKey(request: AgentPromptSuggestionRequest): AgentPromptSuggestionRequestKey {
  return AgentPromptSuggestionRequestKey(
    targetModeId = request.targetModeId,
    projectPath = normalizeSuggestionProjectPath(request.projectPath),
    contextFingerprint = computePromptSuggestionContextFingerprint(request.contextItems),
  )
}

internal fun computePromptSuggestionContextFingerprint(items: List<AgentPromptContextItem>): HashValue128? {
  if (items.isEmpty()) {
    return null
  }

  val hash = Hashing.xxh3_128().hashStream()
  // version
  hash.putInt(0)

  for (item in items) {
    when (resolvePromptSuggestionContextHashMode(item)) {
      PromptSuggestionContextHashMode.FULL -> appendExactContextFingerprintItem(hash, item)
      PromptSuggestionContextHashMode.ROUNDED -> appendRoundedPromptSuggestionContextFingerprintItem(hash, item)
    }
  }
  hash.putInt(items.size)
  return hash.get()
}

internal fun normalizeSuggestionProjectPath(projectPath: String?): String? {
  return projectPath
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?.let(::normalizeNonEmptySuggestionProjectPath)
}

private fun normalizeNonEmptySuggestionProjectPath(projectPath: String): String {
  return projectPath.replace('\\', '/').trimEnd('/')
}

private enum class PromptSuggestionContextHashMode {
  FULL,
  ROUNDED,
}

private fun resolvePromptSuggestionContextHashMode(item: AgentPromptContextItem): PromptSuggestionContextHashMode {
  return if (item.isUnselectedEditorSnippetForPromptSuggestions()) {
    PromptSuggestionContextHashMode.ROUNDED
  }
  else {
    PromptSuggestionContextHashMode.FULL
  }
}

private fun appendRoundedPromptSuggestionContextFingerprintItem(sink: HashSink, item: AgentPromptContextItem) {
  val payload = item.payload.objOrNull()
  sink.putString(item.rendererId)
  appendPromptSuggestionHashField(sink, "itemId", item.itemId)
  appendPromptSuggestionHashField(sink, "parentItemId", item.parentItemId)
  sink.putString(item.source)
  sink.putInt(item.phase?.ordinal ?: -1)
  sink.putBoolean(payload?.bool("selection") == true)
  appendPromptSuggestionHashField(sink, "language", payload?.string("language"))
}

private fun appendExactContextFingerprintItem(sink: HashSink, item: AgentPromptContextItem) {
  sink.putString(item.rendererId)
  appendPromptSuggestionHashField(sink, "title", item.title)
  sink.putString(item.body)
  appendPromptSuggestionHashField(sink, "itemId", item.itemId)
  appendPromptSuggestionHashField(sink, "parentItemId", item.parentItemId)
  sink.putString(item.source)
  sink.putInt(item.phase?.ordinal ?: -1)
  sink.putInt(item.truncation.originalChars)
  sink.putInt(item.truncation.includedChars)
  sink.putInt(item.truncation.reason.ordinal)
  appendPromptSuggestionCanonicalPayload(sink, item.payload)
}

private fun AgentPromptContextItem.isUnselectedEditorSnippetForPromptSuggestions(): Boolean {
  if (rendererId != AgentPromptContextRendererIds.SNIPPET || source != "editor") {
    return false
  }
  val payload = payload.objOrNull() ?: return false
  return payload.bool("selection") == false
}

private fun appendPromptSuggestionHashField(sink: HashSink, name: String, value: String?) {
  sink.putString(name)
  if (value == null) {
    sink.putInt(-1)
  }
  else {
    sink.putString(value)
  }
}

private fun appendPromptSuggestionCanonicalPayload(sink: HashSink, payload: AgentPromptPayloadValue) {
  when (payload) {
    is AgentPromptPayloadValue.Obj -> {
      sink.putByte(0)
      sink.putUnorderedIterable(payload.fields.keys, { key, nestedSink ->
        nestedSink.putString(key)
        appendPromptSuggestionCanonicalPayload(nestedSink, payload.fields.getValue(key))
      }, Hashing.xxh3_64())
    }

    is AgentPromptPayloadValue.Arr -> {
      sink.putByte(1)
      sink.putOrderedIterable(payload.items) { item, nestedSink ->
        appendPromptSuggestionCanonicalPayload(nestedSink, item)
      }
    }

    is AgentPromptPayloadValue.Str -> {
      sink.putByte(2)
      sink.putString(payload.value)
    }

    is AgentPromptPayloadValue.Num -> {
      sink.putByte(3)
      sink.putString(payload.value)
    }

    is AgentPromptPayloadValue.Bool -> {
      sink.putByte(4)
      sink.putBoolean(payload.value)
    }

    AgentPromptPayloadValue.Null -> sink.putByte(5)
  }
}

private class PromptSuggestionSubscription(
  private val session: RetainedPromptSuggestionSession,
  private val onClose: (RetainedPromptSuggestionSession) -> Unit,
) : AgentPromptSuggestionSubscription {
  private val closed = AtomicBoolean(false)

  override val currentCandidates: List<AgentPromptSuggestionCandidate>
    get() = session.candidates.value

  override val updates: Flow<List<AgentPromptSuggestionCandidate>>
    get() = session.candidates

  override fun close() {
    if (closed.compareAndSet(false, true)) {
      onClose(session)
    }
  }
}

private class RetainedPromptSuggestionSession(
  val requestKey: AgentPromptSuggestionRequestKey,
  val candidates: MutableStateFlow<List<AgentPromptSuggestionCandidate>>,
  val generationJob: Job,
) {
  var attachments: Int = 0
  var evictionJob: Job? = null

  fun cancel() {
    evictionJob?.cancel()
    evictionJob = null
    generationJob.cancel()
  }
}
