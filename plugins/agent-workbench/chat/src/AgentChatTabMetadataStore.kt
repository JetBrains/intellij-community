// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.TestOnly
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private const val AGENT_CHAT_METADATA_SCHEMA_VERSION = 2
private const val AGENT_CHAT_METADATA_FILE_SUFFIX = ".awchat.json"
private const val AGENT_CHAT_METADATA_DIR_NAME = "agent-workbench-chat-frame"
private const val AGENT_CHAT_METADATA_TABS_DIR_NAME = "tabs"
private const val AGENT_CHAT_METADATA_TTL_MILLIS = 30L * 24 * 60 * 60 * 1000

private val LOG = logger<AgentChatTabMetadataStore>()

@Service(Service.Level.APP)
internal class AgentChatTabMetadataStore {
  private val jsonFactory = JsonFactory()
  private val lock = Any()
  private val tabsDir: Path by lazy {
    PathManager.getConfigDir()
      .resolve(AGENT_CHAT_METADATA_DIR_NAME)
      .resolve(AGENT_CHAT_METADATA_TABS_DIR_NAME)
  }

  init {
    runCatching { pruneStale() }
      .onFailure { t -> LOG.debug("Failed to prune stale Agent Chat metadata", t) }
  }

  fun loadDescriptor(tabKey: String): AgentChatFileDescriptor? {
    synchronized(lock) {
      val path = metadataPath(tabKey)
      if (!Files.isRegularFile(path)) {
        return null
      }
      val metadata = readMetadata(path) ?: run {
        deleteMetadataFile(path)
        return null
      }
      if (metadata.version != AGENT_CHAT_METADATA_SCHEMA_VERSION || metadata.tabKey != tabKey) {
        deleteMetadataFile(path)
        return null
      }
      if (isExpired(metadata.updatedAt)) {
        deleteMetadataFile(path)
        return null
      }
      return metadata.toDescriptor()
    }
  }

  fun upsert(descriptor: AgentChatFileDescriptor) {
    val metadata = AgentChatTabMetadata(
      version = AGENT_CHAT_METADATA_SCHEMA_VERSION,
      tabKey = descriptor.tabKey,
      projectHash = descriptor.projectHash,
      projectPath = descriptor.projectPath,
      threadIdentity = descriptor.threadIdentity,
      subAgentId = descriptor.subAgentId,
      threadId = descriptor.threadId,
      shellCommand = descriptor.shellCommand,
      title = descriptor.threadTitle,
      updatedAt = System.currentTimeMillis(),
    )

    synchronized(lock) {
      ensureTabsDir()
      val targetPath = metadataPath(metadata.tabKey)
      val tempPath = Files.createTempFile(tabsDir, "${metadata.tabKey}-", ".tmp")
      try {
        Files.newBufferedWriter(tempPath).use { writer ->
          jsonFactory.createGenerator(writer).use { generator ->
            writeMetadata(generator, metadata)
          }
        }
        moveAtomically(tempPath, targetPath)
      }
      catch (t: Throwable) {
        Files.deleteIfExists(tempPath)
        throw t
      }
    }
  }

  fun delete(tabKey: String) {
    synchronized(lock) {
      deleteMetadataFile(metadataPath(tabKey))
    }
  }

  fun deleteByThread(projectPath: String, threadIdentity: String): Int {
    if (!Files.isDirectory(tabsDir)) {
      return 0
    }

    // tabKey is a one-way hash over (projectHash, projectPath, threadIdentity, subAgentId).
    // Archive cleanup only knows (projectPath, threadIdentity), so without a reverse index we
    // must scan metadata files to remove all sub-agent variants of the same thread.
    val normalizedProjectPath = normalizeAgentChatProjectPath(projectPath)
    var deleted = 0
    Files.newDirectoryStream(tabsDir, "*${AGENT_CHAT_METADATA_FILE_SUFFIX}").use { files ->
      for (file in files) {
        if (!Files.isRegularFile(file)) {
          continue
        }

        val metadata = readMetadata(file)
        val identity = if (metadata != null && metadataPath(metadata.tabKey) == file) {
          IdentityPayload(
            projectHash = metadata.projectHash,
            projectPath = metadata.projectPath,
            threadIdentity = metadata.threadIdentity,
            subAgentId = metadata.subAgentId,
          )
        }
        else {
          // Fallback path for malformed/version-mismatched files: keep deletion resilient by
          // parsing only the identity fields needed for archive matching.
          readMetadataIdentity(file)
        } ?: continue

        if (normalizeAgentChatProjectPath(identity.projectPath) == normalizedProjectPath && identity.threadIdentity == threadIdentity) {
          deleteMetadataFile(file)
          deleted++
        }
      }
    }
    return deleted
  }

  fun pruneStale() {
    synchronized(lock) {
      if (!Files.isDirectory(tabsDir)) {
        return
      }

      Files.newDirectoryStream(tabsDir, "*${AGENT_CHAT_METADATA_FILE_SUFFIX}").use { files ->
        for (file in files) {
          if (!Files.isRegularFile(file)) {
            continue
          }
          val metadata = readMetadata(file)
          if (metadata == null ||
              metadata.version != AGENT_CHAT_METADATA_SCHEMA_VERSION ||
              isExpired(metadata.updatedAt) ||
              metadataPath(metadata.tabKey) != file) {
            deleteMetadataFile(file)
          }
        }
      }
    }
  }

  private fun ensureTabsDir() {
    Files.createDirectories(tabsDir)
  }

  private fun metadataPath(tabKey: String): Path = tabsDir.resolve("$tabKey$AGENT_CHAT_METADATA_FILE_SUFFIX")

  private fun deleteMetadataFile(path: Path) {
    runCatching { Files.deleteIfExists(path) }
      .onFailure { t -> LOG.debug("Failed to delete Agent Chat metadata file $path", t) }
  }

  private fun moveAtomically(source: Path, target: Path) {
    try {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
    catch (_: AtomicMoveNotSupportedException) {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
    }
  }

  private fun readMetadata(path: Path): AgentChatTabMetadata? {
    return try {
      Files.newBufferedReader(path).use { reader ->
        jsonFactory.createParser(reader).use { parser ->
          parseMetadata(parser)
        }
      }
    }
    catch (t: Throwable) {
      LOG.debug("Failed to read Agent Chat metadata file $path", t)
      null
    }
  }

  private fun readMetadataIdentity(path: Path): IdentityPayload? {
    return try {
      Files.newBufferedReader(path).use { reader ->
        jsonFactory.createParser(reader).use { parser ->
          parseMetadataIdentity(parser)
        }
      }
    }
    catch (t: Throwable) {
      LOG.debug("Failed to read Agent Chat metadata identity from $path", t)
      null
    }
  }

  private fun parseMetadataIdentity(parser: JsonParser): IdentityPayload? {
    if (parser.nextToken() != JsonToken.START_OBJECT) {
      return null
    }

    var projectPath: String? = null
    var threadIdentity: String? = null

    while (parser.nextToken() != JsonToken.END_OBJECT) {
      val fieldName = parser.currentName() ?: return null
      parser.nextToken()
      when (fieldName) {
        "identity" -> {
          val identity = parseIdentity(parser)
          if (identity != null) {
            if (identity.projectPath.isNotBlank()) {
              projectPath = identity.projectPath
            }
            if (identity.threadIdentity.isNotBlank()) {
              threadIdentity = identity.threadIdentity
            }
          }
        }
        "projectPath" -> {
          readStringOrNull(parser)?.takeIf { it.isNotBlank() }?.let { projectPath = it }
        }
        "threadIdentity" -> {
          readStringOrNull(parser)?.takeIf { it.isNotBlank() }?.let { threadIdentity = it }
        }
        else -> parser.skipChildren()
      }
    }

    val resolvedProjectPath = projectPath ?: return null
    val resolvedThreadIdentity = threadIdentity ?: return null
    return IdentityPayload(
      projectHash = "",
      projectPath = resolvedProjectPath,
      threadIdentity = resolvedThreadIdentity,
      subAgentId = null,
    )
  }

  private fun parseMetadata(parser: JsonParser): AgentChatTabMetadata? {
    if (parser.nextToken() != JsonToken.START_OBJECT) {
      return null
    }

    var version: Int? = null
    var tabKey: String? = null
    var identity: IdentityPayload? = null
    var runtime: RuntimePayload? = null

    while (parser.nextToken() != JsonToken.END_OBJECT) {
      val fieldName = parser.currentName() ?: return null
      parser.nextToken()
      when (fieldName) {
        "version" -> version = readIntOrNull(parser)
        "tabKey" -> tabKey = readStringOrNull(parser)
        "identity" -> identity = parseIdentity(parser)
        "runtime" -> runtime = parseRuntime(parser)
        else -> parser.skipChildren()
      }
    }

    val metadataVersion = version ?: return null
    val metadataTabKey = tabKey?.takeIf { it.isNotBlank() } ?: return null
    val metadataIdentity = identity ?: return null
    val metadataRuntime = runtime ?: return null
    val normalizedThreadId = normalizeThreadId(metadataIdentity.threadIdentity, metadataRuntime.threadId)

    return AgentChatTabMetadata(
      version = metadataVersion,
      tabKey = metadataTabKey,
      projectHash = metadataIdentity.projectHash,
      projectPath = metadataIdentity.projectPath,
      threadIdentity = metadataIdentity.threadIdentity,
      subAgentId = metadataIdentity.subAgentId,
      threadId = normalizedThreadId,
      shellCommand = metadataRuntime.shellCommand,
      title = metadataRuntime.title,
      updatedAt = metadataRuntime.updatedAt,
    )
  }

  private fun parseIdentity(parser: JsonParser): IdentityPayload? {
    if (parser.currentToken != JsonToken.START_OBJECT) {
      parser.skipChildren()
      return null
    }

    var projectHash: String? = null
    var projectPath: String? = null
    var threadIdentity: String? = null
    var subAgentId: String? = null

    while (parser.nextToken() != JsonToken.END_OBJECT) {
      val fieldName = parser.currentName() ?: return null
      parser.nextToken()
      when (fieldName) {
        "projectHash" -> projectHash = readStringOrNull(parser)
        "projectPath" -> projectPath = readStringOrNull(parser)
        "threadIdentity" -> threadIdentity = readStringOrNull(parser)
        "subAgentId" -> subAgentId = readStringOrNull(parser)
        else -> parser.skipChildren()
      }
    }

    return IdentityPayload(
      projectHash = projectHash.orEmpty(),
      projectPath = projectPath.orEmpty(),
      threadIdentity = threadIdentity.orEmpty(),
      subAgentId = subAgentId?.ifBlank { null },
    )
  }

  private fun parseRuntime(parser: JsonParser): RuntimePayload? {
    if (parser.currentToken != JsonToken.START_OBJECT) {
      parser.skipChildren()
      return null
    }

    var threadId: String? = null
    var shellCommand: List<String> = emptyList()
    var title: String? = null
    var updatedAt: Long? = null

    while (parser.nextToken() != JsonToken.END_OBJECT) {
      val fieldName = parser.currentName() ?: return null
      parser.nextToken()
      when (fieldName) {
        "threadId" -> threadId = readStringOrNull(parser)
        "shellCommand" -> shellCommand = readStringArray(parser)
        "title" -> title = readStringOrNull(parser)
        "updatedAt" -> updatedAt = readLongOrNull(parser)
        else -> parser.skipChildren()
      }
    }

    return RuntimePayload(
      threadId = threadId.orEmpty(),
      shellCommand = shellCommand,
      title = title.orEmpty(),
      updatedAt = updatedAt ?: 0L,
    )
  }

  private fun writeMetadata(generator: com.fasterxml.jackson.core.JsonGenerator, metadata: AgentChatTabMetadata) {
    generator.writeStartObject()
    generator.writeNumberField("version", metadata.version)
    generator.writeStringField("tabKey", metadata.tabKey)

    generator.writeObjectFieldStart("identity")
    generator.writeStringField("projectHash", metadata.projectHash)
    generator.writeStringField("projectPath", metadata.projectPath)
    generator.writeStringField("threadIdentity", metadata.threadIdentity)
    if (metadata.subAgentId != null) {
      generator.writeStringField("subAgentId", metadata.subAgentId)
    }
    else {
      generator.writeNullField("subAgentId")
    }
    generator.writeEndObject()

    generator.writeObjectFieldStart("runtime")
    generator.writeStringField("threadId", metadata.threadId)
    generator.writeArrayFieldStart("shellCommand")
    for (commandToken in metadata.shellCommand) {
      generator.writeString(commandToken)
    }
    generator.writeEndArray()
    generator.writeStringField("title", metadata.title)
    generator.writeNumberField("updatedAt", metadata.updatedAt)
    generator.writeEndObject()
    generator.writeEndObject()
  }

  private fun readStringArray(parser: JsonParser): List<String> {
    if (parser.currentToken != JsonToken.START_ARRAY) {
      parser.skipChildren()
      return emptyList()
    }

    val result = ArrayList<String>()
    while (true) {
      val token = parser.nextToken() ?: return result
      if (token == JsonToken.END_ARRAY) {
        return result
      }
      if (token == JsonToken.VALUE_STRING) {
        result.add(parser.text)
      }
      else {
        parser.skipChildren()
      }
    }
  }

  private fun readStringOrNull(parser: JsonParser): String? {
    return if (parser.currentToken == JsonToken.VALUE_STRING) parser.text else null
  }

  private fun readIntOrNull(parser: JsonParser): Int? {
    return when (parser.currentToken) {
      JsonToken.VALUE_NUMBER_INT -> parser.intValue
      JsonToken.VALUE_STRING -> parser.text.toIntOrNull()
      else -> null
    }
  }

  private fun readLongOrNull(parser: JsonParser): Long? {
    return when (parser.currentToken) {
      JsonToken.VALUE_NUMBER_INT -> parser.longValue
      JsonToken.VALUE_STRING -> parser.text.toLongOrNull()
      else -> null
    }
  }

  private fun isExpired(updatedAt: Long): Boolean {
    if (updatedAt <= 0) {
      return true
    }
    return System.currentTimeMillis() - updatedAt > AGENT_CHAT_METADATA_TTL_MILLIS
  }
}

internal object AgentChatTabMetadataStores {
  fun getInstance(): AgentChatTabMetadataStore {
    val application = ApplicationManager.getApplication()
      ?: error("AgentChatTabMetadataStore requires an initialized application")
    return application.getService(AgentChatTabMetadataStore::class.java)
      ?: error("AgentChatTabMetadataStore service is not registered")
  }

  @TestOnly
  fun createStandaloneForTest(): AgentChatTabMetadataStore = AgentChatTabMetadataStore()
}

private data class AgentChatTabMetadata(
  val version: Int,
  val tabKey: String,
  val projectHash: String,
  val projectPath: String,
  val threadIdentity: String,
  val subAgentId: String?,
  val threadId: String,
  val shellCommand: List<String>,
  val title: String,
  val updatedAt: Long,
)

private data class IdentityPayload(
  val projectHash: String,
  val projectPath: String,
  val threadIdentity: String,
  val subAgentId: String?,
)

private data class RuntimePayload(
  val threadId: String,
  val shellCommand: List<String>,
  val title: String,
  val updatedAt: Long,
)

private fun AgentChatTabMetadata.toDescriptor(): AgentChatFileDescriptor {
  return AgentChatFileDescriptor(
    tabKey = tabKey,
    projectHash = projectHash,
    projectPath = projectPath,
    threadIdentity = threadIdentity,
    threadId = threadId,
    threadTitle = title,
    subAgentId = subAgentId,
    shellCommand = shellCommand,
  )
}

private fun normalizeThreadId(threadIdentity: String, threadId: String): String {
  val parsedIdentity = parseLenientThreadIdentity(threadIdentity) ?: return threadId
  val parsedThreadId = parseLenientThreadIdentity(threadId) ?: return threadId
  if (parsedIdentity.threadId != parsedThreadId.threadId) {
    return threadId
  }
  if (!parsedIdentity.providerId.equals(parsedThreadId.providerId, ignoreCase = true)) {
    return threadId
  }
  return parsedIdentity.threadId
}

private fun parseLenientThreadIdentity(value: String): ParsedThreadIdentity? {
  val separator = value.indexOf(':')
  if (separator <= 0 || separator == value.lastIndex) {
    return null
  }

  val providerId = value.substring(0, separator).trim()
  val threadId = value.substring(separator + 1)
  if (providerId.isEmpty() || threadId.isBlank()) {
    return null
  }
  return ParsedThreadIdentity(providerId = providerId, threadId = threadId)
}

private data class ParsedThreadIdentity(
  val providerId: String,
  val threadId: String,
)
