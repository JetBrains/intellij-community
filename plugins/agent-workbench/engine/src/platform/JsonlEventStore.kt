// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.engine.platform

import com.intellij.agent.workbench.engine.core.EventId
import com.intellij.agent.workbench.engine.core.EventSource
import com.intellij.agent.workbench.engine.core.EventVisibility
import com.intellij.agent.workbench.engine.core.ThreadEventEnvelope
import com.intellij.agent.workbench.engine.core.ThreadEventType
import com.intellij.agent.workbench.engine.core.ThreadId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText

/**
 * File-backed [EventStore] that persists one JSON object per line under
 * `<root>/threads/<thread>/events.jsonl`. Simple and debuggable for the MVP; the interface lets a
 * SQLite/WAL backend replace it later without touching callers (design §18.3).
 */
class JsonlEventStore(
  private val root: Path,
  private val now: () -> Long = System::currentTimeMillis,
) : EventStore {
  private val json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
  }
  private val seqByThread = ConcurrentHashMap<String, AtomicLong>()

  @Synchronized
  override fun append(
    threadId: ThreadId,
    source: EventSource,
    type: ThreadEventType,
    payload: JsonObject,
    visibility: EventVisibility,
    correlationId: String?,
    causationId: String?,
    rawRef: String?,
  ): ThreadEventEnvelope {
    val seq = nextSeq(threadId)
    val envelope = ThreadEventEnvelope(
      id = EventId("${threadId.value}#$seq"),
      threadId = threadId,
      seq = seq,
      timestamp = now(),
      source = source,
      type = type,
      payload = payload,
      correlationId = correlationId,
      causationId = causationId,
      rawRef = rawRef,
      visibility = visibility,
    )
    writeLine(envelope)
    return envelope
  }

  @Synchronized
  override fun persist(envelope: ThreadEventEnvelope) {
    val counter = counterFor(envelope.threadId)
    counter.updateAndGet { current -> maxOf(current, envelope.seq + 1) }
    writeLine(envelope)
  }

  override fun read(threadId: ThreadId): List<ThreadEventEnvelope> {
    val file = eventsFile(threadId)
    if (!file.exists()) return emptyList()
    return file.readText()
      .lineSequence()
      .filter { it.isNotBlank() }
      .map { json.decodeFromString(ThreadEventEnvelope.serializer(), it) }
      .sortedBy { it.seq }
      .toList()
  }

  override fun threadIds(): List<ThreadId> {
    val threadsDir = root.resolve(THREADS_DIR)
    if (!threadsDir.isDirectory()) return emptyList()
    return threadsDir.listDirectoryEntries()
      .filter { it.isDirectory() && it.resolve(EVENTS_FILE).exists() }
      .map { ThreadId(decodeDir(it.fileName.toString())) }
  }

  private fun nextSeq(threadId: ThreadId): Long = counterFor(threadId).getAndIncrement()

  private fun counterFor(threadId: ThreadId): AtomicLong =
    seqByThread.computeIfAbsent(threadId.value) { AtomicLong(read(threadId).maxOfOrNull { it.seq + 1 } ?: 0L) }

  private fun writeLine(envelope: ThreadEventEnvelope) {
    val file = eventsFile(envelope.threadId)
    file.parent.createDirectories()
    val line = json.encodeToString(ThreadEventEnvelope.serializer(), envelope) + "\n"
    Files.writeString(file, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
  }

  private fun eventsFile(threadId: ThreadId): Path =
    root.resolve(THREADS_DIR).resolve(encodeDir(threadId.value)).resolve(EVENTS_FILE)

  companion object {
    private const val THREADS_DIR = "threads"
    private const val EVENTS_FILE = "events.jsonl"

    /** Encodes a thread id into a single safe directory name (reversible). */
    private fun encodeDir(id: String): String = buildString {
      for (ch in id) {
        if (ch.isLetterOrDigit() || ch == '.' || ch == '-') append(ch)
        else append('%').append(ch.code.toString(16).padStart(2, '0').uppercase())
      }
    }

    private fun decodeDir(name: String): String = buildString {
      var i = 0
      while (i < name.length) {
        val ch = name[i]
        if (ch == '%' && i + 2 < name.length) {
          append(name.substring(i + 1, i + 3).toInt(16).toChar())
          i += 3
        }
        else {
          append(ch)
          i += 1
        }
      }
    }
  }
}
