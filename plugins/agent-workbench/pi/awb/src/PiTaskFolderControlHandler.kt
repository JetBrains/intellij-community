// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.pi.sessions

import com.intellij.agent.workbench.sessions.task.folders.AgentTaskFolder
import com.intellij.agent.workbench.sessions.task.folders.AgentTaskFolderService
import com.intellij.agent.workbench.sessions.task.folders.AgentTaskFolderStatus
import com.intellij.agent.workbench.sessions.task.folders.AgentTaskFolderThreadAssignment
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviders
import kotlinx.coroutines.CancellationException
import org.jetbrains.annotations.ApiStatus
import tools.jackson.core.JsonGenerator

@ApiStatus.Internal
class PiTaskFolderControlHandler : PiControlRequestHandler {
  private val taskFolderServiceProvider: () -> AgentTaskFolderService

  constructor() : this(
    taskFolderServiceProvider = { service<AgentTaskFolderService>() },
  )

  internal constructor(
    taskFolderServiceProvider: () -> AgentTaskFolderService,
  ) {
    this.taskFolderServiceProvider = taskFolderServiceProvider
  }

  override val messageType: String = PI_TASK_FOLDER_CONTROL_MESSAGE_TYPE

  override fun handle(
    context: PiControlSessionContext,
    request: PiControlExtensionRequest,
    requestId: String,
    sendResponse: (String) -> Unit,
  ) {
    val service = taskFolderServiceProvider()
    val arguments = request.arguments ?: PiControlRequestArguments()
    when (request.operation?.trim()) {
      OP_GET_CURRENT -> {
        val folder = service.getFolderForThread(context.projectPath, PI_AGENT_SESSION_PROVIDER, context.sessionId)
        sendResponse(buildTaskFolderResponse(requestId = requestId, folder = folder))
      }
      OP_LIST_FOLDERS -> {
        val folders = service.listFolders(context.projectPath, includeDone = arguments.includeDone == true)
        sendResponse(buildTaskFoldersResponse(requestId = requestId, folders = folders))
      }
      OP_LIST_THREADS -> {
        val folder = resolveTaskFolder(service, context, arguments)
        if (folder == null) {
          sendResponse(buildPiControlErrorResponse(requestId, "Task folder is not available"))
        }
        else {
          sendResponse(buildTaskFolderAssignmentsResponse(requestId, service.listFolderThreadAssignments(folder.id)))
        }
      }
      OP_CREATE_AND_ASSIGN -> {
        createAndAssignCurrentThread(service, context, arguments, requestId, sendResponse)
      }
      OP_ASSIGN_CURRENT_THREAD -> {
        val folder = resolveTaskFolder(service, context, arguments)
        if (folder == null) {
          sendResponse(buildPiControlErrorResponse(requestId, "Task folder is not available"))
        }
        else {
          val changed = service.assignThread(context.projectPath, PI_AGENT_SESSION_PROVIDER, context.sessionId, folder.id)
          sendResponse(buildTaskFolderMutationResponse(requestId = requestId,
                                                       changed = changed,
                                                       folder = refreshedFolder(service, context, folder.id)))
        }
      }
      OP_UNASSIGN_CURRENT_THREAD -> {
        val folder = service.getFolderForThread(context.projectPath, PI_AGENT_SESSION_PROVIDER, context.sessionId)
        val changed = service.unassignThread(context.projectPath, PI_AGENT_SESSION_PROVIDER, context.sessionId)
        sendResponse(buildTaskFolderMutationResponse(requestId = requestId, changed = changed, folder = folder))
      }
      OP_RENAME -> {
        val folder = resolveTaskFolder(service, context, arguments)
        val name = arguments.name?.trim()?.takeIf { it.isNotEmpty() }
        if (folder == null || name == null) {
          sendResponse(buildPiControlErrorResponse(requestId, "Task folder rename request is incomplete"))
        }
        else {
          val changed = service.renameFolder(folder.id, name)
          sendResponse(buildTaskFolderMutationResponse(requestId = requestId,
                                                       changed = changed,
                                                       folder = refreshedFolder(service, context, folder.id)))
        }
      }
      OP_SET_METADATA -> {
        val folder = resolveTaskFolder(service, context, arguments)
        val key = arguments.key?.trim()?.takeIf { it.isNotEmpty() }
        val value = arguments.value
        if (folder == null || key == null || value == null) {
          sendResponse(buildPiControlErrorResponse(requestId, "Task folder metadata request is incomplete"))
        }
        else {
          val changed = service.setMetadata(folder.id, key, value)
          sendResponse(buildTaskFolderMutationResponse(requestId = requestId,
                                                       changed = changed,
                                                       folder = refreshedFolder(service, context, folder.id)))
        }
      }
      OP_DELETE_METADATA -> {
        val folder = resolveTaskFolder(service, context, arguments)
        val key = arguments.key?.trim()?.takeIf { it.isNotEmpty() }
        if (folder == null || key == null) {
          sendResponse(buildPiControlErrorResponse(requestId, "Task folder metadata request is incomplete"))
        }
        else {
          val changed = service.deleteMetadata(folder.id, key)
          sendResponse(buildTaskFolderMutationResponse(requestId = requestId,
                                                       changed = changed,
                                                       folder = refreshedFolder(service, context, folder.id)))
        }
      }
      OP_MARK_DONE -> {
        val folder = resolveTaskFolder(service, context, arguments)
        if (folder == null) {
          sendResponse(buildPiControlErrorResponse(requestId, "Task folder is not available"))
        }
        else {
          markDone(service, context, folder, requestId, sendResponse)
        }
      }
      OP_DELETE -> {
        val folder = resolveTaskFolder(service, context, arguments)
        if (folder == null) {
          sendResponse(buildPiControlErrorResponse(requestId, "Task folder is not available"))
        }
        else {
          val changed = service.deleteFolder(folder.id)
          sendResponse(buildTaskFolderMutationResponse(requestId = requestId, changed = changed, folder = folder))
        }
      }
      else -> sendResponse(buildPiControlErrorResponse(requestId, "Unsupported task folder operation"))
    }
  }
}

private fun createAndAssignCurrentThread(
  service: AgentTaskFolderService,
  context: PiControlSessionContext,
  arguments: PiControlRequestArguments,
  requestId: String,
  sendResponse: (String) -> Unit,
) {
  val existing = service.getFolderForThread(context.projectPath, PI_AGENT_SESSION_PROVIDER, context.sessionId)
  if (existing != null) {
    sendResponse(buildTaskFolderCreatedResponse(requestId = requestId, folder = existing, created = false, assigned = true))
    return
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
  val assigned = service.assignThread(context.projectPath, PI_AGENT_SESSION_PROVIDER, context.sessionId, folder.id)
  sendResponse(buildTaskFolderCreatedResponse(requestId = requestId, folder = folder, created = true, assigned = assigned))
}

private fun markDone(
  service: AgentTaskFolderService,
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
    .map { assignment -> TaskFolderArchiveTarget(assignment.path, assignment.provider, assignment.threadId) }
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
  if (!targets.all { target -> AgentSessionProviders.find(target.provider)?.supportsArchiveThread == true }) {
    sendResponse(buildPiControlErrorResponse(requestId, "Task folder contains threads that cannot be archived"))
    return
  }
  val result = archiveTargets(targets)
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

private fun archiveTargets(targets: List<TaskFolderArchiveTarget>): TaskFolderArchiveResult {
  var archivedCount = 0
  targets.forEach { target ->
    val descriptor = AgentSessionProviders.find(target.provider)
    if (descriptor?.supportsArchiveThread != true) return@forEach
    val archived = try {
      runBlockingCancellable { descriptor.archiveThread(path = target.path, threadId = target.threadId) }
    }
    catch (t: Throwable) {
      if (t is CancellationException) throw t
      false
    }
    if (archived) archivedCount++
  }
  return TaskFolderArchiveResult(requestedCount = targets.size, archivedCount = archivedCount)
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

private fun buildTaskFolderMutationResponse(requestId: String, changed: Boolean, folder: AgentTaskFolder?): String {
  return buildTaskFolderResultResponse(requestId) { generator ->
    generator.writeBooleanProperty("changed", changed)
    generator.writeName("folder")
    writeTaskFolder(generator, folder)
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

private fun writeTaskFolderAssignment(generator: JsonGenerator, assignment: AgentTaskFolderThreadAssignment) {
  generator.writeStartObject()
  generator.writeStringProperty("path", assignment.path)
  generator.writeStringProperty("provider", assignment.provider.value)
  generator.writeStringProperty("threadId", assignment.threadId)
  generator.writeStringProperty("folderId", assignment.folderId)
  generator.writeNumberProperty("assignedAt", assignment.assignedAt)
  generator.writeEndObject()
}

private fun resolveTaskFolder(
  service: AgentTaskFolderService,
  context: PiControlSessionContext,
  arguments: PiControlRequestArguments,
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

private data class TaskFolderArchiveTarget(
  @JvmField val path: String,
  val provider: AgentSessionProvider,
  @JvmField val threadId: String,
)

private data class TaskFolderArchiveResult(
  @JvmField val requestedCount: Int,
  @JvmField val archivedCount: Int,
) {
  val allRequestedArchived: Boolean
    get() = requestedCount > 0 && requestedCount == archivedCount
}

private const val OP_GET_CURRENT: String = "getCurrent"
private const val OP_LIST_FOLDERS: String = "listFolders"
private const val OP_LIST_THREADS: String = "listThreads"
private const val OP_CREATE_AND_ASSIGN: String = "createAndAssign"
private const val OP_ASSIGN_CURRENT_THREAD: String = "assignCurrentThread"
private const val OP_UNASSIGN_CURRENT_THREAD: String = "unassignCurrentThread"
private const val OP_RENAME: String = "rename"
private const val OP_SET_METADATA: String = "setMetadata"
private const val OP_DELETE_METADATA: String = "deleteMetadata"
private const val OP_MARK_DONE: String = "markDone"
private const val OP_DELETE: String = "delete"
private const val PI_TASK_FOLDER_CONTROL_MESSAGE_TYPE: String = "taskFolderRequest"
