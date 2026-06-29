// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.pi.sessions

import com.intellij.openapi.components.service
import com.intellij.platform.ai.agent.sessions.core.folders.AgentTaskFolder
import com.intellij.platform.ai.agent.sessions.core.folders.AgentTaskFolderService
import com.intellij.platform.ai.agent.sessions.core.folders.AgentTaskFolderThreadAssignment
import tools.jackson.core.JsonGenerator

internal class PiTaskFolderControlHandler(
  private val taskFolderServiceProvider: () -> AgentTaskFolderService = { service<AgentTaskFolderService>() },
) {
  fun handle(context: PiControlSessionContext, payload: PiControlPayload, requestId: String): String {
    val service = taskFolderServiceProvider()
    return when (payload.type) {
      PiControlMessageType.GET_CURRENT_TASK_FOLDER -> {
        val folder = service.getFolderForThread(context.projectPath, PI_AGENT_SESSION_PROVIDER, context.sessionId)
        buildTaskFolderResponse(requestId = requestId, folder = folder)
      }
      PiControlMessageType.LIST_TASK_FOLDER_THREADS -> {
        val folderId = resolveTaskFolderId(service, context, payload)
        if (folderId == null) {
          buildPiControlErrorResponse(requestId, "Task folder is not available")
        }
        else {
          buildTaskFolderAssignmentsResponse(requestId, service.listFolderThreadAssignments(folderId))
        }
      }
      PiControlMessageType.CREATE_AND_ASSIGN_TASK_FOLDER -> {
        val name = payload.name?.trim()?.takeIf { it.isNotEmpty() }
        if (name == null) {
          buildPiControlErrorResponse(requestId, "Task folder name is required")
        }
        else {
          val folder = service.createFolder(context.projectPath, name, payload.metadata.orEmpty())
          if (folder == null) {
            buildPiControlErrorResponse(requestId, "Task folder could not be created")
          }
          else {
            val assigned = service.assignThread(context.projectPath, PI_AGENT_SESSION_PROVIDER, context.sessionId, folder.id)
            buildTaskFolderCreatedResponse(requestId = requestId, folder = folder, assigned = assigned)
          }
        }
      }
      PiControlMessageType.GET_TASK_FOLDER_METADATA -> {
        val folder = resolveTaskFolder(service, context, payload)
        if (folder == null) {
          buildPiControlErrorResponse(requestId, "Task folder is not available")
        }
        else {
          buildTaskFolderMetadataResponse(requestId, folder.metadata)
        }
      }
      PiControlMessageType.SET_TASK_FOLDER_METADATA -> {
        val folderId = resolveTaskFolderId(service, context, payload)
        val key = payload.key?.trim()?.takeIf { it.isNotEmpty() }
        val value = payload.value
        if (folderId == null || key == null || value == null) {
          buildPiControlErrorResponse(requestId, "Task folder metadata request is incomplete")
        }
        else {
          val changed = service.setMetadata(folderId, key, value)
          buildPiControlMutationResponse(requestId, changed)
        }
      }
      PiControlMessageType.DELETE_TASK_FOLDER_METADATA -> {
        val folderId = resolveTaskFolderId(service, context, payload)
        val key = payload.key?.trim()?.takeIf { it.isNotEmpty() }
        if (folderId == null || key == null) {
          buildPiControlErrorResponse(requestId, "Task folder metadata request is incomplete")
        }
        else {
          val changed = service.deleteMetadata(folderId, key)
          buildPiControlMutationResponse(requestId, changed)
        }
      }
      else -> buildPiControlErrorResponse(requestId, "Unsupported task folder request")
    }
  }
}

private fun buildTaskFolderCreatedResponse(requestId: String, folder: AgentTaskFolder, assigned: Boolean): String {
  return buildPiControlJsonObject { generator ->
    generator.writeStringProperty("type", PiControlMessageType.RESPONSE.wireName)
    generator.writeStringProperty("requestId", requestId)
    generator.writeBooleanProperty("ok", true)
    generator.writeName("folder")
    writeTaskFolder(generator, folder)
    generator.writeBooleanProperty("assigned", assigned)
  }
}

private fun buildTaskFolderResponse(requestId: String, folder: AgentTaskFolder?): String {
  return buildPiControlJsonObject { generator ->
    generator.writeStringProperty("type", PiControlMessageType.RESPONSE.wireName)
    generator.writeStringProperty("requestId", requestId)
    generator.writeBooleanProperty("ok", true)
    generator.writeName("folder")
    writeTaskFolder(generator, folder)
  }
}

private fun buildTaskFolderAssignmentsResponse(requestId: String, assignments: List<AgentTaskFolderThreadAssignment>): String {
  return buildPiControlJsonObject { generator ->
    generator.writeStringProperty("type", PiControlMessageType.RESPONSE.wireName)
    generator.writeStringProperty("requestId", requestId)
    generator.writeBooleanProperty("ok", true)
    generator.writeName("threads")
    generator.writeStartArray()
    assignments.forEach { assignment -> writeTaskFolderAssignment(generator, assignment) }
    generator.writeEndArray()
  }
}

private fun buildTaskFolderMetadataResponse(requestId: String, metadata: Map<String, String>): String {
  return buildPiControlJsonObject { generator ->
    generator.writeStringProperty("type", PiControlMessageType.RESPONSE.wireName)
    generator.writeStringProperty("requestId", requestId)
    generator.writeBooleanProperty("ok", true)
    generator.writeName("metadata")
    generator.writeStartObject()
    metadata.forEach { (key, value) -> generator.writeStringProperty(key, value) }
    generator.writeEndObject()
  }
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
  payload: PiControlPayload,
): AgentTaskFolder? {
  val folderId = resolveTaskFolderId(service, context, payload) ?: return null
  return service.getFolder(folderId)
}

private fun resolveTaskFolderId(service: AgentTaskFolderService, context: PiControlSessionContext, payload: PiControlPayload): String? {
  return payload.folderId?.trim()?.takeIf { it.isNotEmpty() }
         ?: service.getFolderForThread(context.projectPath, PI_AGENT_SESSION_PROVIDER, context.sessionId)?.id
}
