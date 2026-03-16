// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions.backend.rollout

// @spec community/plugins/agent-workbench/spec/agent-sessions-codex-rollout-source.spec.md

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.agent.workbench.codex.common.forEachObjectField
import com.intellij.agent.workbench.codex.common.readStringOrNull
import com.intellij.agent.workbench.codex.sessions.backend.CodexBackendThread
import com.intellij.agent.workbench.codex.sessions.backend.CodexSessionActivity
import com.intellij.agent.workbench.codex.sessions.backend.CodexSessionBackend
import com.intellij.agent.workbench.codex.sessions.resolveProjectDirectoryFromPath
import com.intellij.agent.workbench.json.WorkbenchJsonlScanner
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.invariantSeparatorsPathString

private const val ROLLOUT_FILE_PREFIX = "rollout-"
private const val ROLLOUT_FILE_SUFFIX = ".jsonl"
private const val MAX_TITLE_LENGTH = 120
private const val USER_MESSAGE_BEGIN = "## My request for Codex:"
private const val ENVIRONMENT_CONTEXT_OPEN_TAG = "<environment_context>"
private const val TURN_ABORTED_OPEN_TAG = "<turn_aborted>"

private val LOG = logger<CodexRolloutSessionBackend>()

internal class CodexRolloutSessionBackend(
  private val codexHomeProvider: () -> Path = { Path.of(System.getProperty("user.home"), ".codex") },
) : CodexSessionBackend {
  private val jsonFactory = JsonFactory()
  private val cacheLock = Any()
  private val cachedFilesByPath = Object2ObjectOpenHashMap<String, CachedRolloutFile>()
  private val threadsByCwd = Object2ObjectOpenHashMap<String, ObjectArrayList<CodexBackendThread>>()

  override val updates: Flow<Unit> = callbackFlow {
    val watcher = runCatching {
      CodexRolloutSessionsWatcher(codexHomeProvider = codexHomeProvider) {
        trySend(Unit)
      }
    }.onFailure { t ->
      LOG.warn("Failed to initialize Codex rollout watcher", t)
    }.getOrNull()

    if (watcher == null) {
      awaitClose { }
      return@callbackFlow
    }

    awaitClose {
      watcher.close()
    }
  }.conflate()

  override suspend fun listThreads(path: String, @Suppress("UNUSED_PARAMETER") openProject: Project?): List<CodexBackendThread> {
    return withContext(Dispatchers.IO) {
      val workingDirectory = resolveProjectDirectoryFromPath(path)
        ?: return@withContext emptyList()
      val cwdFilter = normalizeRootPath(workingDirectory.invariantSeparatorsPathString)
      collectThreadsByCwd(setOf(cwdFilter))[cwdFilter].orEmpty()
    }
  }

  override suspend fun prefetchThreads(paths: List<String>): Map<String, List<CodexBackendThread>> {
    return withContext(Dispatchers.IO) {
      val pathFilters = resolvePathFilters(paths)
      if (pathFilters.isEmpty()) return@withContext emptyMap()

      val threadsByCwd = collectThreadsByCwd(pathFilters.map { (_, cwdFilter) -> cwdFilter }.toSet())
      pathFilters.associate { (path, cwdFilter) ->
        path to threadsByCwd[cwdFilter].orEmpty()
      }
    }
  }

  private fun collectThreadsByCwd(cwdFilters: Set<String>): Map<String, List<CodexBackendThread>> {
    if (cwdFilters.isEmpty()) return emptyMap()

    val sessionsDir = codexHomeProvider().resolve("sessions")
    if (!Files.isDirectory(sessionsDir)) {
      synchronized(cacheLock) {
        cachedFilesByPath.clear()
        threadsByCwd.clear()
      }
      return emptyMap()
    }

    val scannedFiles = try {
      scanRolloutFiles(sessionsDir)
    }
    catch (_: Throwable) {
      return emptyMap()
    }

    val filesToParse = ObjectArrayList<RolloutFileStat>()
    var removedAny = false
    synchronized(cacheLock) {
      val iterator = cachedFilesByPath.object2ObjectEntrySet().iterator()
      while (iterator.hasNext()) {
        val entry = iterator.next()
        if (!scannedFiles.containsKey(entry.key)) {
          iterator.remove()
          removedAny = true
        }
      }

      for (entry in scannedFiles.object2ObjectEntrySet()) {
        val stat = entry.value
        val cached = cachedFilesByPath[entry.key]
        if (cached == null || cached.lastModifiedMs != stat.lastModifiedMs || cached.sizeBytes != stat.sizeBytes) {
          filesToParse.add(stat)
        }
      }
    }

    if (filesToParse.isNotEmpty()) {
      val parsedUpdates = Object2ObjectOpenHashMap<String, CachedRolloutFile>(filesToParse.size)
      for (stat in filesToParse) {
        parsedUpdates[stat.pathKey] = CachedRolloutFile(
          lastModifiedMs = stat.lastModifiedMs,
          sizeBytes = stat.sizeBytes,
          parsedThread = parseRolloutFile(stat.path),
        )
      }
      synchronized(cacheLock) {
        for (entry in parsedUpdates.object2ObjectEntrySet()) {
          cachedFilesByPath[entry.key] = entry.value
        }
      }
    }

    if (removedAny || filesToParse.isNotEmpty()) {
      synchronized(cacheLock) {
        rebuildThreadsByCwd()
      }
    }

    synchronized(cacheLock) {
      val result = Object2ObjectOpenHashMap<String, List<CodexBackendThread>>(cwdFilters.size)
      for (cwdFilter in cwdFilters) {
        val threads = threadsByCwd[cwdFilter] ?: continue
        result[cwdFilter] = ArrayList(threads)
      }
      return result
    }
  }

  private fun rebuildThreadsByCwd() {
    threadsByCwd.clear()
    for (entry in cachedFilesByPath.object2ObjectEntrySet()) {
      val parsedThread = entry.value.parsedThread ?: continue
      var threads = threadsByCwd[parsedThread.normalizedCwd]
      if (threads == null) {
        threads = ObjectArrayList()
        threadsByCwd[parsedThread.normalizedCwd] = threads
      }
      threads.add(parsedThread.thread)
    }

    for (threads in threadsByCwd.values) {
      threads.sortWith(Comparator { left, right ->
        right.thread.updatedAt.compareTo(left.thread.updatedAt)
      })
    }
  }

  private fun scanRolloutFiles(sessionsDir: Path): Object2ObjectOpenHashMap<String, RolloutFileStat> {
    val scannedFiles = Object2ObjectOpenHashMap<String, RolloutFileStat>()
    Files.walk(sessionsDir).use { stream ->
      val iterator = stream.iterator()
      while (iterator.hasNext()) {
        val candidate = iterator.next()
        if (!Files.isRegularFile(candidate)) continue
        val fileName = candidate.fileName?.toString() ?: continue
        if (!isRolloutFileName(fileName)) continue
        val lastModifiedMs = try {
          Files.getLastModifiedTime(candidate).toMillis()
        }
        catch (_: Throwable) {
          continue
        }
        val sizeBytes = try {
          Files.size(candidate)
        }
        catch (_: Throwable) {
          continue
        }

        val pathKey = candidate.invariantSeparatorsPathString
        scannedFiles[pathKey] = RolloutFileStat(
          pathKey = pathKey,
          path = candidate,
          lastModifiedMs = lastModifiedMs,
          sizeBytes = sizeBytes,
        )
      }
    }

    return scannedFiles
  }

  private fun resolvePathFilters(paths: List<String>): List<Pair<String, String>> {
    return paths.mapNotNull { path ->
      resolveProjectDirectoryFromPath(path)?.let { directory ->
        path to normalizeRootPath(directory.invariantSeparatorsPathString)
      }
    }
  }

  private fun parseRolloutFile(path: Path): ParsedRolloutThread? {
    val state = try {
      WorkbenchJsonlScanner.scanJsonObjects(
        path = path,
        jsonFactory = jsonFactory,
        newState = ::RolloutParseState,
      ) { parser, parseState ->
        val event = parseEvent(parser) ?: return@scanJsonObjects true

        parseState.updatedAt = maxTimestamp(parseState.updatedAt, event.timestampMs)
        parseState.updatedAt = maxTimestamp(parseState.updatedAt, event.sessionTimestampMs)
        parseState.sessionId = parseState.sessionId ?: event.sessionId
        parseState.sessionCwd = parseState.sessionCwd ?: event.sessionCwd
        parseState.gitBranch = parseState.gitBranch ?: event.gitBranch

        val eventTimestamp = event.timestampMs
        when (event.topLevelType) {
          "event_msg" -> {
            when (event.payloadType) {
              "task_started" -> parseState.processing = true
              "task_complete", "turn_aborted" -> parseState.processing = false
              "user_message" -> {
                parseState.latestUserMessageAt = maxTimestamp(parseState.latestUserMessageAt, eventTimestamp)
                parseState.title = parseState.title ?: extractTitle(event.payloadMessage)
                val pendingInputAt = parseState.pendingUserInputAt
                if (pendingInputAt != null && eventTimestamp != null && eventTimestamp >= pendingInputAt) {
                  parseState.pendingUserInputAt = null
                }
              }

              "agent_message" -> {
                parseState.latestAgentMessageAt = maxTimestamp(parseState.latestAgentMessageAt, eventTimestamp)
              }
            }

            if (event.payloadType?.contains("requestUserInput", ignoreCase = true) == true) {
              parseState.pendingUserInputAt = maxTimestamp(parseState.pendingUserInputAt ?: Long.MIN_VALUE, eventTimestamp)
            }

            when (event.itemType) {
              "enteredReviewMode" -> parseState.reviewing = true
              "exitedReviewMode" -> parseState.reviewing = false
            }
          }

          "response_item" -> {
            if (event.payloadType == "message") {
              when (event.payloadRole) {
                "user" -> {
                  parseState.latestUserMessageAt = maxTimestamp(parseState.latestUserMessageAt, eventTimestamp)
                  val pendingInputAt = parseState.pendingUserInputAt
                  if (pendingInputAt != null && eventTimestamp != null && eventTimestamp >= pendingInputAt) {
                    parseState.pendingUserInputAt = null
                  }
                }

                "assistant" -> {
                  parseState.latestAgentMessageAt = maxTimestamp(parseState.latestAgentMessageAt, eventTimestamp)
                }
              }
            }
          }
        }

        true
      }
    }
    catch (_: Throwable) {
      return null
    }

    val normalizedCwd = normalizeRootPath(state.sessionCwd ?: return null)
    val resolvedSessionId = state.sessionId ?: return null
    val hasUnread = state.latestAgentMessageAt > state.latestUserMessageAt
    val hasPendingUserInput = state.pendingUserInputAt != null
    val activity = when {
      hasPendingUserInput || hasUnread -> CodexSessionActivity.UNREAD
      state.reviewing -> CodexSessionActivity.REVIEWING
      state.processing -> CodexSessionActivity.PROCESSING
      else -> CodexSessionActivity.READY
    }

    val fallbackUpdatedAt = runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrDefault(0L)
    val resolvedUpdatedAt = if (state.updatedAt > 0L) state.updatedAt else fallbackUpdatedAt
    val resolvedTitle = state.title ?: "Thread ${resolvedSessionId.take(8)}"

    return ParsedRolloutThread(
      normalizedCwd = normalizedCwd,
      thread = CodexBackendThread(
        thread = CodexThread(
          id = resolvedSessionId,
          title = resolvedTitle,
          updatedAt = resolvedUpdatedAt,
          archived = false,
          gitBranch = state.gitBranch,
        ),
        activity = activity,
      ),
    )
  }

  private fun parseEvent(parser: JsonParser): RolloutEvent? {
    return try {
      if (parser.currentToken != JsonToken.START_OBJECT) return null

      var topLevelType: String? = null
      var timestampMs: Long? = null
      var payloadType: String? = null
      var payloadRole: String? = null
      var payloadMessage: String? = null
      var sessionId: String? = null
      var sessionCwd: String? = null
      var sessionTimestampMs: Long? = null
      var gitBranch: String? = null
      var itemType: String? = null

      forEachObjectField(parser) { fieldName ->
        when (fieldName) {
          "timestamp" -> timestampMs = parseIsoTimestamp(readStringOrNull(parser))
          "type" -> topLevelType = readStringOrNull(parser)
          "payload" -> {
            if (parser.currentToken == JsonToken.START_OBJECT) {
              forEachObjectField(parser) { payloadField ->
                when (payloadField) {
                  "type" -> payloadType = readStringOrNull(parser)
                  "role" -> payloadRole = readStringOrNull(parser)
                  "message" -> payloadMessage = readStringOrNull(parser)
                  "id" -> sessionId = readStringOrNull(parser)
                  "cwd" -> sessionCwd = readStringOrNull(parser)
                  "timestamp" -> sessionTimestampMs = parseIsoTimestamp(readStringOrNull(parser))
                  "git" -> {
                    gitBranch = parseNestedStringField(parser, "branch")
                  }

                  "item" -> {
                    itemType = parseNestedStringField(parser, "type")
                  }

                  else -> parser.skipChildren()
                }
                true
              }
            }
            else {
              parser.skipChildren()
            }
          }

          else -> parser.skipChildren()
        }
        true
      }

      RolloutEvent(
        topLevelType = topLevelType,
        timestampMs = timestampMs,
        payloadType = payloadType,
        payloadRole = payloadRole,
        payloadMessage = payloadMessage,
        sessionId = sessionId,
        sessionCwd = sessionCwd,
        sessionTimestampMs = sessionTimestampMs,
        gitBranch = gitBranch,
        itemType = itemType,
      )
    }
    catch (_: Throwable) {
      null
    }
  }
}

private data class RolloutEvent(
  @JvmField val topLevelType: String?,
  @JvmField val timestampMs: Long?,
  @JvmField val payloadType: String?,
  @JvmField val payloadRole: String?,
  @JvmField val payloadMessage: String?,
  @JvmField val sessionId: String?,
  @JvmField val sessionCwd: String?,
  @JvmField val sessionTimestampMs: Long?,
  @JvmField val gitBranch: String?,
  @JvmField val itemType: String?,
)

private data class ParsedRolloutThread(
  @JvmField val normalizedCwd: String,
  @JvmField val thread: CodexBackendThread,
)

private data class RolloutParseState(
  @JvmField var sessionId: String? = null,
  @JvmField var sessionCwd: String? = null,
  @JvmField var gitBranch: String? = null,
  @JvmField var title: String? = null,
  @JvmField var updatedAt: Long = 0L,
  @JvmField var processing: Boolean = false,
  @JvmField var reviewing: Boolean = false,
  @JvmField var latestUserMessageAt: Long = Long.MIN_VALUE,
  @JvmField var latestAgentMessageAt: Long = Long.MIN_VALUE,
  @JvmField var pendingUserInputAt: Long? = null,
)

private data class RolloutFileStat(
  @JvmField val pathKey: String,
  @JvmField val path: Path,
  @JvmField val lastModifiedMs: Long,
  @JvmField val sizeBytes: Long,
)

private data class CachedRolloutFile(
  @JvmField val lastModifiedMs: Long,
  @JvmField val sizeBytes: Long,
  @JvmField val parsedThread: ParsedRolloutThread?,
)

private class CodexRolloutSessionsWatcher(
  private val codexHomeProvider: () -> Path,
  private val onRolloutChange: () -> Unit,
) : AutoCloseable {
  private val watchService = FileSystems.getDefault().newWatchService()
  private val running = AtomicBoolean(true)
  private val watchKeysByPath = Object2ObjectOpenHashMap<WatchKey, Path>()
  private val watchKeysLock = Any()
  private val sessionsRoot: Path
    get() = codexHomeProvider().resolve("sessions")

  private val thread = Thread(::runWatchLoop, "CodexRolloutSessionBackendWatcher").apply {
    isDaemon = true
    start()
  }

  init {
    registerInitialPaths()
  }

  override fun close() {
    if (!running.compareAndSet(true, false)) return
    watchService.close()
    thread.interrupt()
  }

  private fun registerInitialPaths() {
    val codexHome = codexHomeProvider()
    if (Files.isDirectory(codexHome)) {
      registerDirectory(codexHome)
    }
    val sessions = sessionsRoot
    if (Files.isDirectory(sessions)) {
      registerDirectoryRecursively(sessions)
    }
  }

  private fun runWatchLoop() {
    while (running.get()) {
      val watchKey = try {
        watchService.take()
      }
      catch (_: InterruptedException) {
        continue
      }
      catch (_: ClosedWatchServiceException) {
        break
      }
      catch (_: Throwable) {
        continue
      }

      val watchedPath = synchronized(watchKeysLock) { watchKeysByPath[watchKey] }
      if (watchedPath == null) {
        watchKey.reset()
        continue
      }

      var hasRolloutChange = false
      for (event in watchKey.pollEvents()) {
        val kind = event.kind()
        if (kind == StandardWatchEventKinds.OVERFLOW) {
          hasRolloutChange = true
          continue
        }

        val contextPath = event.context() as? Path ?: continue
        val eventPath = watchedPath.resolve(contextPath)

        if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(eventPath, LinkOption.NOFOLLOW_LINKS)) {
          registerDirectoryRecursively(eventPath)
        }

        if (isRolloutPath(eventPath)) {
          hasRolloutChange = true
        }
      }

      if (!watchKey.reset()) {
        synchronized(watchKeysLock) {
          watchKeysByPath.remove(watchKey)
        }
      }

      if (hasRolloutChange) {
        onRolloutChange()
      }
    }
  }

  private fun isRolloutPath(path: Path): Boolean {
    val fileName = path.fileName?.toString() ?: return false
    return path.startsWith(sessionsRoot) && isRolloutFileName(fileName)
  }

  private fun registerDirectoryRecursively(root: Path) {
    if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return

    try {
      Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
          registerDirectory(dir)
          return FileVisitResult.CONTINUE
        }
      })
    }
    catch (_: Throwable) {
    }
  }

  private fun registerDirectory(path: Path) {
    try {
      val watchKey = path.register(
        watchService,
        StandardWatchEventKinds.ENTRY_CREATE,
        StandardWatchEventKinds.ENTRY_DELETE,
        StandardWatchEventKinds.ENTRY_MODIFY,
      )
      synchronized(watchKeysLock) {
        watchKeysByPath[watchKey] = path
      }
    }
    catch (_: Throwable) {
    }
  }
}

private fun parseIsoTimestamp(value: String?): Long? {
  val text = value?.trim().takeIf { !it.isNullOrEmpty() } ?: return null
  return try {
    Instant.parse(text).toEpochMilli()
  }
  catch (_: DateTimeParseException) {
    null
  }
}

private fun isRolloutFileName(fileName: String): Boolean {
  return fileName.startsWith(ROLLOUT_FILE_PREFIX) && fileName.endsWith(ROLLOUT_FILE_SUFFIX)
}

private fun extractTitle(message: String?): String? {
  val candidate = stripUserMessagePrefix(message ?: return null)
    .lineSequence()
    .map(String::trim)
    .firstOrNull { it.isNotEmpty() }
    ?: return null
  if (isSessionPrefix(candidate)) return null
  return trimTitle(candidate.replace(Regex("\\s+"), " "))
}

private fun stripUserMessagePrefix(text: String): String {
  val markerIndex = text.indexOf(USER_MESSAGE_BEGIN)
  return if (markerIndex >= 0) {
    text.substring(markerIndex + USER_MESSAGE_BEGIN.length).trim()
  }
  else {
    text.trim()
  }
}

private fun isSessionPrefix(text: String): Boolean {
  val normalized = text.trimStart().lowercase()
  return normalized.startsWith(ENVIRONMENT_CONTEXT_OPEN_TAG) || normalized.startsWith(TURN_ABORTED_OPEN_TAG)
}

private fun trimTitle(value: String): String {
  if (value.length <= MAX_TITLE_LENGTH) return value
  return value.take(MAX_TITLE_LENGTH - 3).trimEnd() + "..."
}

private fun normalizeRootPath(value: String): String {
  return value.replace('\\', '/').trimEnd('/')
}

private fun parseNestedStringField(parser: JsonParser, fieldName: String): String? {
  if (parser.currentToken != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return null
  }

  var result: String? = null
  forEachObjectField(parser) { nestedField ->
    if (nestedField == fieldName) {
      result = readStringOrNull(parser)
    }
    else {
      parser.skipChildren()
    }
    true
  }
  return result
}

private fun maxTimestamp(current: Long, candidate: Long?): Long {
  if (candidate == null) return current
  return if (candidate > current) candidate else current
}
