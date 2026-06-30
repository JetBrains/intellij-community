// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.pi.sessions

import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.service.AgentSessionArchiveRequestResult
import com.intellij.agent.workbench.sessions.service.AgentSessionArchiveService
import com.intellij.agent.workbench.sessions.service.AgentSessionReadService
import com.intellij.agent.workbench.sessions.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.task.folders.AgentTaskFolder
import com.intellij.agent.workbench.sessions.task.folders.AgentTaskFolderService
import com.intellij.agent.workbench.sessions.task.folders.AgentTaskFolderStatus
import com.intellij.agent.workbench.sessions.task.folders.AgentTaskFolderThreadAssignment
import com.intellij.agent.workbench.sessions.util.isAgentSessionNewSessionId
import com.intellij.openapi.components.service
import com.intellij.platform.ai.agent.core.normalizeAgentWorkbenchPath
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.platform.ai.agent.json.createJsonParser
import com.intellij.platform.ai.agent.json.forEachJsonObjectField
import com.intellij.platform.ai.agent.json.readJsonStringOrNull
import org.jetbrains.annotations.ApiStatus
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.json.JsonFactory

@ApiStatus.Internal
class PiTaskFolderControlHandler : PiControlRequestHandler {
  private val taskFolderServiceProvider: () -> AgentTaskFolderService
  private val sessionReadServiceProvider: () -> AgentSessionReadService
  private val archiveServiceProvider: () -> AgentSessionArchiveService

  constructor() : this(
    taskFolderServiceProvider = { service<AgentTaskFolderService>() },
    sessionReadServiceProvider = { service<AgentSessionReadService>() },
    archiveServiceProvider = { service<AgentSessionArchiveService>() },
  )

  internal constructor(
    taskFolderServiceProvider: () -> AgentTaskFolderService,
    sessionReadServiceProvider: () -> AgentSessionReadService,
    archiveServiceProvider: () -> AgentSessionArchiveService,
  ) {
    this.taskFolderServiceProvider = taskFolderServiceProvider
    this.sessionReadServiceProvider = sessionReadServiceProvider
    this.archiveServiceProvider = archiveServiceProvider
  }

  override val messageType: String = PI_TASK_FOLDER_CONTROL_MESSAGE_TYPE

  override fun handle(
    context: PiControlSessionContext,
    request: PiControlExtensionRequest,
    requestId: String,
    sendResponse: (String) -> Unit,
  ) {
    val arguments = parseTaskFolderRequestArguments(request.argumentsJson)
    if (arguments == null) {
      sendResponse(buildPiControlErrorResponse(requestId, "Malformed task folder request arguments"))
      return
    }

    val taskFolderService = taskFolderServiceProvider()
    when (request.operation?.trim()) {
      OP_GET_CURRENT -> {
        val folder = taskFolderService.getFolderForThread(context.projectPath, PI_AGENT_SESSION_PROVIDER, context.sessionId)
        sendResponse(buildTaskFolderResponse(requestId = requestId, folder = folder))
      }
      OP_LIST_FOLDERS -> {
        val folders = taskFolderService.listFolders(context.projectPath, includeDone = arguments.includeDone == true)
        sendResponse(buildTaskFoldersResponse(requestId = requestId, folders = folders))
      }
      OP_LIST_THREADS -> {
        val folder = resolveTaskFolder(taskFolderService, context, arguments)
        if (folder == null) {
          sendResponse(buildPiControlErrorResponse(requestId, "Task folder is not available"))
        }
        else {
          sendResponse(buildTaskFolderAssignmentsResponse(requestId, taskFolderService.listFolderThreadAssignments(folder.id)))
        }
      }
      OP_LIST_PROJECT_THREADS -> {
        val threads = listLoadedProjectThreads(taskFolderService, sessionReadServiceProvider(), context)
        sendResponse(buildProjectThreadsResponse(requestId = requestId, threads = threads))
      }
      OP_CREATE_AND_ASSIGN -> {
        createTaskFolder(taskFolderService, context, arguments, requestId, sendResponse)
      }
      OP_ASSIGN_CURRENT_THREAD -> {
        val folder = resolveTaskFolder(taskFolderService, context, arguments)
        if (folder == null) {
          sendResponse(buildPiControlErrorResponse(requestId, "Task folder is not available"))
        }
        else {
          val changed = taskFolderService.assignThread(context.projectPath, PI_AGENT_SESSION_PROVIDER, context.sessionId, folder.id)
          sendResponse(buildTaskFolderMutationResponse(requestId = requestId,
                                                       changed = changed,
                                                       folder = refreshedFolder(taskFolderService, context, folder.id)))
        }
      }
      OP_UNASSIGN_CURRENT_THREAD -> {
        val folder = taskFolderService.getFolderForThread(context.projectPath, PI_AGENT_SESSION_PROVIDER, context.sessionId)
        val changed = taskFolderService.unassignThread(context.projectPath, PI_AGENT_SESSION_PROVIDER, context.sessionId)
        sendResponse(buildTaskFolderMutationResponse(requestId = requestId, changed = changed, folder = folder))
      }
      OP_MOVE_THREAD -> {
        moveLoadedThreadToFolder(taskFolderService, sessionReadServiceProvider(), context, arguments, requestId, sendResponse)
      }
      OP_REMOVE_THREAD -> {
        removeLoadedThreadFromFolder(taskFolderService, sessionReadServiceProvider(), context, arguments, requestId, sendResponse)
      }
      OP_RENAME -> {
        val folder = resolveTaskFolder(taskFolderService, context, arguments)
        val name = arguments.name?.trim()?.takeIf { it.isNotEmpty() }
        if (folder == null || name == null) {
          sendResponse(buildPiControlErrorResponse(requestId, "Task folder rename request is incomplete"))
        }
        else {
          val changed = taskFolderService.renameFolder(folder.id, name)
          sendResponse(buildTaskFolderMutationResponse(requestId = requestId,
                                                       changed = changed,
                                                       folder = refreshedFolder(taskFolderService, context, folder.id)))
        }
      }
      OP_SET_METADATA -> {
        val folder = resolveTaskFolder(taskFolderService, context, arguments)
        val key = arguments.key?.trim()?.takeIf { it.isNotEmpty() }
        val value = arguments.value
        if (folder == null || key == null || value == null) {
          sendResponse(buildPiControlErrorResponse(requestId, "Task folder metadata request is incomplete"))
        }
        else {
          val changed = taskFolderService.setMetadata(folder.id, key, value)
          sendResponse(buildTaskFolderMutationResponse(requestId = requestId,
                                                       changed = changed,
                                                       folder = refreshedFolder(taskFolderService, context, folder.id)))
        }
      }
      OP_DELETE_METADATA -> {
        val folder = resolveTaskFolder(taskFolderService, context, arguments)
        val key = arguments.key?.trim()?.takeIf { it.isNotEmpty() }
        if (folder == null || key == null) {
          sendResponse(buildPiControlErrorResponse(requestId, "Task folder metadata request is incomplete"))
        }
        else {
          val changed = taskFolderService.deleteMetadata(folder.id, key)
          sendResponse(buildTaskFolderMutationResponse(requestId = requestId,
                                                       changed = changed,
                                                       folder = refreshedFolder(taskFolderService, context, folder.id)))
        }
      }
      OP_MARK_DONE -> {
        val folder = resolveTaskFolder(taskFolderService, context, arguments)
        if (folder == null) {
          sendResponse(buildPiControlErrorResponse(requestId, "Task folder is not available"))
        }
        else {
          markDone(taskFolderService, archiveServiceProvider, context, folder, requestId, sendResponse)
        }
      }
      OP_DELETE -> {
        val folder = resolveTaskFolder(taskFolderService, context, arguments)
        if (folder == null) {
          sendResponse(buildPiControlErrorResponse(requestId, "Task folder is not available"))
        }
        else {
          val changed = taskFolderService.deleteFolder(folder.id)
          sendResponse(buildTaskFolderMutationResponse(requestId = requestId, changed = changed, folder = folder))
        }
      }
      else -> sendResponse(buildPiControlErrorResponse(requestId, "Unsupported task folder operation"))
    }
  }
}

private fun createTaskFolder(
  service: AgentTaskFolderService,
  context: PiControlSessionContext,
  arguments: PiTaskFolderRequestArguments,
  requestId: String,
  sendResponse: (String) -> Unit,
) {
  val assignCurrentThread = arguments.assignCurrentThread != false
  if (assignCurrentThread) {
    val existing = service.getFolderForThread(context.projectPath, PI_AGENT_SESSION_PROVIDER, context.sessionId)
    if (existing != null) {
      sendResponse(buildTaskFolderCreatedResponse(requestId = requestId, folder = existing, created = false, assigned = true))
      return
    }
  }

  val name = arguments.name?.trim()?.takeIf { it.isNotEmpty() }
  if (name == null) {
    sendResponse(buildPiControlErrorResponse(requestId, "Task folder name is required"))
    return
  }
  val folder = service.createFolder(context.projectPath, name, arguments.metadata.orEmpty())
  if (folder == null) {
    sendResponse(buildPiControlErrorResponse(requestId, "Task folder could not be created"))
    return
  }
  val assigned = assignCurrentThread && service.assignThread(context.projectPath, PI_AGENT_SESSION_PROVIDER, context.sessionId, folder.id)
  sendResponse(buildTaskFolderCreatedResponse(requestId = requestId, folder = folder, created = true, assigned = assigned))
}

private fun moveLoadedThreadToFolder(
  service: AgentTaskFolderService,
  readService: AgentSessionReadService,
  context: PiControlSessionContext,
  arguments: PiTaskFolderRequestArguments,
  requestId: String,
  sendResponse: (String) -> Unit,
) {
  val folder = resolveTaskFolder(service, context, arguments)
  if (folder == null) {
    sendResponse(buildPiControlErrorResponse(requestId, "Task folder is not available"))
    return
  }
  val target = resolveLoadedThreadTarget(service, readService, context, arguments)
  if (target.error != null) {
    sendResponse(buildPiControlErrorResponse(requestId, target.error))
    return
  }
  val thread = target.thread ?: run {
    sendResponse(buildPiControlErrorResponse(requestId, "Task folder thread request is incomplete"))
    return
  }
  val changed = service.assignThread(thread.path, thread.provider, thread.thread.id, folder.id)
  val refreshed = refreshedFolder(service, context, folder.id)
  val assignment = service.snapshot(includeDone = true).assignments(thread.path, folder.id).firstOrNull { assignment ->
    assignment.provider == thread.provider && assignment.threadId == thread.thread.id
  }
  sendResponse(buildTaskFolderThreadMutationResponse(requestId = requestId,
                                                     changed = changed,
                                                     folder = refreshed,
                                                     thread = thread.copy(folder = refreshed),
                                                     assignment = assignment))
}

private fun removeLoadedThreadFromFolder(
  service: AgentTaskFolderService,
  readService: AgentSessionReadService,
  context: PiControlSessionContext,
  arguments: PiTaskFolderRequestArguments,
  requestId: String,
  sendResponse: (String) -> Unit,
) {
  val target = resolveLoadedThreadTarget(service, readService, context, arguments)
  if (target.error != null) {
    sendResponse(buildPiControlErrorResponse(requestId, target.error))
    return
  }
  val thread = target.thread ?: run {
    sendResponse(buildPiControlErrorResponse(requestId, "Task folder thread request is incomplete"))
    return
  }
  val previousFolder = service.getFolderForThread(thread.path, thread.provider, thread.thread.id)
  val changed = service.unassignThread(thread.path, thread.provider, thread.thread.id)
  sendResponse(buildTaskFolderThreadMutationResponse(requestId = requestId,
                                                     changed = changed,
                                                     folder = previousFolder,
                                                     thread = thread.copy(folder = null),
                                                     assignment = null))
}

private fun markDone(
  service: AgentTaskFolderService,
  archiveServiceProvider: () -> AgentSessionArchiveService,
  context: PiControlSessionContext,
  folder: AgentTaskFolder,
  requestId: String,
  sendResponse: (String) -> Unit,
) {
  if (folder.status == AgentTaskFolderStatus.DONE) {
    sendResponse(buildTaskFolderDoneResponse(requestId = requestId,
                                             changed = false,
                                             folder = folder,
                                             requestedCount = 0,
                                             archivedCount = 0))
    return
  }
  val targets = service.listFolderThreadAssignments(folder.id)
    .map { assignment ->
      ArchiveThreadTarget.Thread(path = assignment.path,
                                 provider = assignment.provider,
                                 threadId = assignment.threadId)
    }
    .distinct()
  if (targets.isEmpty()) {
    val changed = service.setFolderStatus(folder.id, AgentTaskFolderStatus.DONE)
    sendResponse(
      buildTaskFolderDoneResponse(
        requestId = requestId,
        changed = changed,
        folder = refreshedFolder(service, context, folder.id),
        requestedCount = 0,
        archivedCount = 0,
      )
    )
    return
  }
  val archiveService = archiveServiceProvider()
  if (!targets.all { target -> archiveService.canArchiveProvider(target.provider) }) {
    sendResponse(buildPiControlErrorResponse(requestId, "Task folder contains threads that cannot be archived"))
    return
  }
  archiveService.archiveThreads(
    targets = targets,
    entryPoint = AgentWorkbenchEntryPoint.PROMPT,
    onComplete = { result ->
      handleMarkDoneArchiveResult(service, context, folder, requestId, result, sendResponse)
    },
    onDropped = {
      sendResponse(buildPiControlErrorResponse(requestId, "Task folder archive is already in progress"))
    },
  )
}

private fun handleMarkDoneArchiveResult(
  service: AgentTaskFolderService,
  context: PiControlSessionContext,
  folder: AgentTaskFolder,
  requestId: String,
  result: AgentSessionArchiveRequestResult,
  sendResponse: (String) -> Unit,
) {
  val changed = result.allRequestedArchived && service.setFolderStatus(folder.id, AgentTaskFolderStatus.DONE)
  sendResponse(
    buildTaskFolderDoneResponse(
      requestId = requestId,
      changed = changed,
      folder = refreshedFolder(service, context, folder.id) ?: folder,
      requestedCount = result.requestedCount,
      archivedCount = result.archivedCount,
    )
  )
}

private fun buildTaskFolderCreatedResponse(requestId: String, folder: AgentTaskFolder, created: Boolean, assigned: Boolean): String {
  return buildTaskFolderResultResponse(requestId) { generator ->
    generator.writeName("folder")
    writeTaskFolder(generator, folder)
    generator.writeBooleanProperty("created", created)
    generator.writeBooleanProperty("assigned", assigned)
  }
}

private fun buildTaskFolderResponse(requestId: String, folder: AgentTaskFolder?): String {
  return buildTaskFolderResultResponse(requestId) { generator ->
    generator.writeName("folder")
    writeTaskFolder(generator, folder)
  }
}

private fun buildTaskFoldersResponse(requestId: String, folders: List<AgentTaskFolder>): String {
  return buildTaskFolderResultResponse(requestId) { generator ->
    generator.writeName("folders")
    generator.writeStartArray()
    folders.forEach { folder -> writeTaskFolder(generator, folder) }
    generator.writeEndArray()
  }
}

private fun buildTaskFolderAssignmentsResponse(requestId: String, assignments: List<AgentTaskFolderThreadAssignment>): String {
  return buildTaskFolderResultResponse(requestId) { generator ->
    generator.writeName("threads")
    generator.writeStartArray()
    assignments.forEach { assignment -> writeTaskFolderAssignment(generator, assignment) }
    generator.writeEndArray()
  }
}

private fun buildProjectThreadsResponse(requestId: String, threads: List<LoadedProjectThread>): String {
  return buildTaskFolderResultResponse(requestId) { generator ->
    generator.writeName("threads")
    generator.writeStartArray()
    threads.forEach { thread -> writeProjectThread(generator, thread) }
    generator.writeEndArray()
  }
}

private fun buildTaskFolderMutationResponse(requestId: String, changed: Boolean, folder: AgentTaskFolder?): String {
  return buildTaskFolderResultResponse(requestId) { generator ->
    generator.writeBooleanProperty("changed", changed)
    generator.writeName("folder")
    writeTaskFolder(generator, folder)
  }
}

private fun buildTaskFolderThreadMutationResponse(
  requestId: String,
  changed: Boolean,
  folder: AgentTaskFolder?,
  thread: LoadedProjectThread,
  assignment: AgentTaskFolderThreadAssignment?,
): String {
  return buildTaskFolderResultResponse(requestId) { generator ->
    generator.writeBooleanProperty("changed", changed)
    generator.writeName("folder")
    writeTaskFolder(generator, folder)
    generator.writeName("thread")
    writeProjectThread(generator, thread)
    generator.writeName("assignment")
    writeTaskFolderAssignment(generator, assignment)
  }
}

private fun buildTaskFolderDoneResponse(
  requestId: String,
  changed: Boolean,
  folder: AgentTaskFolder?,
  requestedCount: Int,
  archivedCount: Int,
): String {
  return buildTaskFolderResultResponse(requestId) { generator ->
    generator.writeBooleanProperty("changed", changed)
    generator.writeNumberProperty("requestedCount", requestedCount)
    generator.writeNumberProperty("archivedCount", archivedCount)
    generator.writeName("folder")
    writeTaskFolder(generator, folder)
  }
}

private fun buildTaskFolderResultResponse(requestId: String, writeResult: (JsonGenerator) -> Unit): String {
  return buildPiControlResultResponse(requestId, writeResult)
}

private fun writeTaskFolder(generator: JsonGenerator, folder: AgentTaskFolder?) {
  if (folder == null) {
    generator.writeNull()
    return
  }
  generator.writeStartObject()
  generator.writeStringProperty("path", folder.path)
  generator.writeStringProperty("id", folder.id)
  generator.writeStringProperty("name", folder.name)
  generator.writeStringProperty("status", folder.status.name)
  generator.writeName("metadata")
  generator.writeStartObject()
  folder.metadata.forEach { (key, value) -> generator.writeStringProperty(key, value) }
  generator.writeEndObject()
  generator.writeNumberProperty("createdAt", folder.createdAt)
  generator.writeNumberProperty("updatedAt", folder.updatedAt)
  generator.writeEndObject()
}

private fun writeTaskFolderAssignment(generator: JsonGenerator, assignment: AgentTaskFolderThreadAssignment?) {
  if (assignment == null) {
    generator.writeNull()
    return
  }
  generator.writeStartObject()
  generator.writeStringProperty("path", assignment.path)
  generator.writeStringProperty("provider", assignment.provider.value)
  generator.writeStringProperty("threadId", assignment.threadId)
  generator.writeStringProperty("folderId", assignment.folderId)
  generator.writeNumberProperty("assignedAt", assignment.assignedAt)
  generator.writeEndObject()
}

private fun writeProjectThread(generator: JsonGenerator, thread: LoadedProjectThread) {
  generator.writeStartObject()
  generator.writeStringProperty("path", thread.path)
  generator.writeStringProperty("provider", thread.provider.value)
  generator.writeStringProperty("threadId", thread.thread.id)
  generator.writeStringProperty("title", thread.thread.title)
  generator.writeNumberProperty("updatedAt", thread.thread.updatedAt)
  generator.writeStringProperty("activity", thread.thread.activityReport.rowActivity.name)
  generator.writeBooleanProperty("archived", thread.thread.archived)
  generator.writeName("folderId")
  thread.folder?.let { folder -> generator.writeString(folder.id) } ?: generator.writeNull()
  generator.writeName("folderName")
  thread.folder?.let { folder -> generator.writeString(folder.name) } ?: generator.writeNull()
  generator.writeName("folderStatus")
  thread.folder?.let { folder -> generator.writeString(folder.status.name) } ?: generator.writeNull()
  generator.writeEndObject()
}

private fun resolveTaskFolder(
  service: AgentTaskFolderService,
  context: PiControlSessionContext,
  arguments: PiTaskFolderRequestArguments,
): AgentTaskFolder? {
  val explicitFolderId = arguments.folderId?.trim()?.takeIf { it.isNotEmpty() }
  if (explicitFolderId != null) {
    return service.snapshot(includeDone = true).folder(context.projectPath, explicitFolderId)
  }
  return service.getFolderForThread(context.projectPath, PI_AGENT_SESSION_PROVIDER, context.sessionId)
}

private fun refreshedFolder(service: AgentTaskFolderService, context: PiControlSessionContext, folderId: String): AgentTaskFolder? {
  return service.snapshot(includeDone = true).folder(context.projectPath, folderId)
}

private fun resolveLoadedThreadTarget(
  service: AgentTaskFolderService,
  readService: AgentSessionReadService,
  context: PiControlSessionContext,
  arguments: PiTaskFolderRequestArguments,
): LoadedThreadTargetResolution {
  val path = arguments.path?.let { normalizeAgentWorkbenchPath(it) } ?: context.projectPath
  if (path != context.projectPath) {
    return LoadedThreadTargetResolution(error = "Thread path must match the current project")
  }
  val providerId = arguments.provider?.trim()?.takeIf { it.isNotEmpty() }
                   ?: return LoadedThreadTargetResolution(error = "Thread provider is required")
  val provider = AgentSessionProvider.fromOrNull(providerId)
                 ?: return LoadedThreadTargetResolution(error = "Thread provider is invalid")
  val threadId = arguments.threadId?.trim()?.takeIf { it.isNotEmpty() }
                 ?: return LoadedThreadTargetResolution(error = "Thread id is required")
  val thread = listLoadedProjectThreads(service, readService, context).firstOrNull { candidate ->
    candidate.path == path && candidate.provider == provider && candidate.thread.id == threadId
  } ?: return LoadedThreadTargetResolution(error = "Thread is not loaded for the current project")
  return LoadedThreadTargetResolution(thread = thread)
}

private fun listLoadedProjectThreads(
  service: AgentTaskFolderService,
  readService: AgentSessionReadService,
  context: PiControlSessionContext,
): List<LoadedProjectThread> {
  val path = context.projectPath
  val snapshot = service.snapshot(includeDone = true)
  return resolveThreadsForPath(readService.stateFlow().value, path)
    .asSequence()
    .filter { thread -> !thread.archived && !isAgentSessionNewSessionId(thread.id) }
    .map { thread ->
      LoadedProjectThread(
        path = path,
        provider = thread.provider,
        thread = thread,
        folder = snapshot.folderForThread(path, thread.provider, thread.id),
      )
    }
    .toList()
}

private fun resolveThreadsForPath(state: AgentSessionsState, normalizedPath: String): List<AgentSessionThread> {
  state.projects.firstOrNull { project -> normalizeAgentWorkbenchPath(project.path) == normalizedPath }?.let { project ->
    return project.threads
  }
  state.projects.forEach { project ->
    val worktree =
      project.worktrees.firstOrNull { candidate -> normalizeAgentWorkbenchPath(candidate.path) == normalizedPath } ?: return@forEach
    return worktree.threads
  }
  return emptyList()
}

private fun parseTaskFolderRequestArguments(argumentsJson: String?): PiTaskFolderRequestArguments? {
  val content = argumentsJson?.trim()?.takeIf { it.isNotEmpty() } ?: return PiTaskFolderRequestArguments()
  return try {
    TASK_FOLDER_JSON_FACTORY.createJsonParser(content).use { parser ->
      if (parser.nextToken() != JsonToken.START_OBJECT) return null
      readTaskFolderRequestArguments(parser)
    }
  }
  catch (_: Exception) {
    null
  }
}

private fun readTaskFolderRequestArguments(parser: JsonParser): PiTaskFolderRequestArguments {
  var folderId: String? = null
  var name: String? = null
  var key: String? = null
  var value: String? = null
  var includeDone: Boolean? = null
  var metadata: Map<String, String>? = null
  var assignCurrentThread: Boolean? = null
  var path: String? = null
  var provider: String? = null
  var threadId: String? = null
  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "folderId" -> folderId = readJsonStringOrNull(parser)
      "name" -> name = readJsonStringOrNull(parser)
      "key" -> key = readJsonStringOrNull(parser)
      "value" -> value = readJsonStringOrNull(parser)
      "includeDone" -> includeDone = readJsonBooleanOrNull(parser)
      "metadata" -> metadata = readStringMapOrNull(parser)
      "assignCurrentThread" -> assignCurrentThread = readJsonBooleanOrNull(parser)
      "path" -> path = readJsonStringOrNull(parser)
      "provider" -> provider = readJsonStringOrNull(parser)
      "threadId" -> threadId = readJsonStringOrNull(parser)
      else -> parser.skipChildren()
    }
    true
  }
  return PiTaskFolderRequestArguments(
    folderId = folderId,
    name = name,
    key = key,
    value = value,
    includeDone = includeDone,
    metadata = metadata,
    assignCurrentThread = assignCurrentThread,
    path = path,
    provider = provider,
    threadId = threadId,
  )
}

private fun readStringMapOrNull(parser: JsonParser): Map<String, String>? {
  if (parser.currentToken() != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return null
  }
  val result = LinkedHashMap<String, String>()
  forEachJsonObjectField(parser) { key ->
    readJsonStringOrNull(parser)?.let { value -> result[key] = value }
    true
  }
  return result
}

@Suppress("DuplicatedCode")
private fun readJsonBooleanOrNull(parser: JsonParser): Boolean? {
  return when (parser.currentToken()) {
    JsonToken.VALUE_TRUE -> true
    JsonToken.VALUE_FALSE -> false
    JsonToken.VALUE_NUMBER_INT -> parser.intValue != 0
    JsonToken.VALUE_STRING -> parser.string.equals("true", ignoreCase = true)
    JsonToken.VALUE_NULL -> null
    else -> {
      parser.skipChildren()
      null
    }
  }
}

private data class PiTaskFolderRequestArguments(
  @JvmField val folderId: String? = null,
  @JvmField val name: String? = null,
  @JvmField val key: String? = null,
  @JvmField val value: String? = null,
  @JvmField val includeDone: Boolean? = null,
  @JvmField val metadata: Map<String, String>? = null,
  @JvmField val assignCurrentThread: Boolean? = null,
  @JvmField val path: String? = null,
  @JvmField val provider: String? = null,
  @JvmField val threadId: String? = null,
)

private data class LoadedProjectThread(
  @JvmField val path: String,
  val provider: AgentSessionProvider,
  @JvmField val thread: AgentSessionThread,
  @JvmField val folder: AgentTaskFolder?,
)

private data class LoadedThreadTargetResolution(
  @JvmField val thread: LoadedProjectThread? = null,
  @JvmField val error: String? = null,
)

private const val OP_GET_CURRENT: String = "getCurrent"
private const val OP_LIST_FOLDERS: String = "listFolders"
private const val OP_LIST_THREADS: String = "listThreads"
private const val OP_LIST_PROJECT_THREADS: String = "listProjectThreads"
private const val OP_CREATE_AND_ASSIGN: String = "createAndAssign"
private const val OP_ASSIGN_CURRENT_THREAD: String = "assignCurrentThread"
private const val OP_UNASSIGN_CURRENT_THREAD: String = "unassignCurrentThread"
private const val OP_MOVE_THREAD: String = "moveThreadToFolder"
private const val OP_REMOVE_THREAD: String = "removeThreadFromFolder"
private const val OP_RENAME: String = "rename"
private const val OP_SET_METADATA: String = "setMetadata"
private const val OP_DELETE_METADATA: String = "deleteMetadata"
private const val OP_MARK_DONE: String = "markDone"
private const val OP_DELETE: String = "delete"
private const val PI_TASK_FOLDER_CONTROL_MESSAGE_TYPE: String = "taskFolderRequest"

private val TASK_FOLDER_JSON_FACTORY = JsonFactory()
