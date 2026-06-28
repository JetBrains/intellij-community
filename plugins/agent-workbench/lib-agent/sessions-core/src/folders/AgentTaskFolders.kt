// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.sessions.core.folders

import com.intellij.platform.ai.agent.core.normalizeAgentWorkbenchPath
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.util.NlsSafe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import java.util.UUID

@ApiStatus.Internal
enum class AgentTaskFolderStatus {
  IN_PROGRESS,
  DONE,
}

@ApiStatus.Internal
data class AgentTaskFolder(
  @JvmField val path: String,
  @JvmField val id: String,
  @JvmField val name: @NlsSafe String,
  @JvmField val status: AgentTaskFolderStatus = AgentTaskFolderStatus.IN_PROGRESS,
  @JvmField val metadata: Map<String, String> = emptyMap(),
  @JvmField val createdAt: Long,
  @JvmField val updatedAt: Long,
)

@ApiStatus.Internal
data class AgentTaskFolderThreadAssignment(
  @JvmField val path: String,
  val provider: AgentSessionProvider,
  @JvmField val threadId: String,
  @JvmField val folderId: String,
  @JvmField val assignedAt: Long,
)

@ApiStatus.Internal
data class AgentTaskFolderSnapshot(
  @JvmField val foldersByPath: Map<String, List<AgentTaskFolder>> = emptyMap(),
  @JvmField val assignmentsByPath: Map<String, List<AgentTaskFolderThreadAssignment>> = emptyMap(),
) {
  fun folders(path: String, includeDone: Boolean = false): List<AgentTaskFolder> {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    return foldersByPath[normalizedPath].orEmpty()
      .filter { folder -> includeDone || folder.status == AgentTaskFolderStatus.IN_PROGRESS }
  }

  fun folder(path: String, folderId: String): AgentTaskFolder? {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    val normalizedFolderId = folderId.trim()
    return foldersByPath[normalizedPath].orEmpty().firstOrNull { folder -> folder.id == normalizedFolderId }
  }

  fun folderForThread(path: String, provider: AgentSessionProvider, threadId: String): AgentTaskFolder? {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    val normalizedThreadId = threadId.trim()
    val assignment = assignmentsByPath[normalizedPath].orEmpty().firstOrNull { assignment ->
      assignment.provider == provider && assignment.threadId == normalizedThreadId
    } ?: return null
    return folder(normalizedPath, assignment.folderId)
  }

  fun assignments(path: String, folderId: String? = null): List<AgentTaskFolderThreadAssignment> {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    val normalizedFolderId = folderId?.trim()?.takeIf { it.isNotEmpty() }
    return assignmentsByPath[normalizedPath].orEmpty()
      .filter { assignment -> normalizedFolderId == null || assignment.folderId == normalizedFolderId }
      .sortedBy { assignment -> assignment.assignedAt }
  }
}

@ApiStatus.Internal
@Service(Service.Level.APP)
@State(name = "AgentTaskFolderState", storages = [Storage(StoragePathMacros.NON_ROAMABLE_FILE)])
class AgentTaskFolderService : SerializablePersistentStateComponent<AgentTaskFolderService.TaskFolderState> {
  private val nowProvider: () -> Long
  private val mutableStateFlow: MutableStateFlow<TaskFolderState>
  val stateFlow: StateFlow<TaskFolderState>

  @Suppress("unused")
  constructor() : this(System::currentTimeMillis)

  internal constructor(nowProvider: () -> Long) : super(TaskFolderState()) {
    this.nowProvider = nowProvider
    mutableStateFlow = MutableStateFlow(state)
    stateFlow = mutableStateFlow.asStateFlow()
  }

  fun snapshot(includeDone: Boolean = true): AgentTaskFolderSnapshot {
    return state.toSnapshot(includeDone = includeDone)
  }

  fun listFolders(path: String, includeDone: Boolean = false): List<AgentTaskFolder> {
    return snapshot(includeDone = includeDone).folders(path, includeDone = includeDone)
  }

  fun getFolder(path: String, folderId: String): AgentTaskFolder? {
    return snapshot(includeDone = true).folder(path, folderId)
  }

  fun getFolderForThread(path: String, provider: AgentSessionProvider, threadId: String): AgentTaskFolder? {
    return snapshot(includeDone = true).folderForThread(path, provider, threadId)
  }

  fun listFolderThreadAssignments(path: String, folderId: String): List<AgentTaskFolderThreadAssignment> {
    return snapshot(includeDone = true).assignments(path, folderId)
  }

  fun createFolder(path: String, name: String, metadata: Map<String, String> = emptyMap()): AgentTaskFolder? {
    val normalizedName = normalizeFolderName(name) ?: return null
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    val now = nowProvider()
    val folder = FolderState(
      id = UUID.randomUUID().toString(),
      name = normalizedName,
      status = AgentTaskFolderStatus.IN_PROGRESS.name,
      metadata = normalizeMetadata(metadata),
      createdAt = now,
      updatedAt = now,
    )
    updateAndEmit { current ->
      val updatedFolders = current.foldersByPath.toMutableMap()
      updatedFolders[normalizedPath] = updatedFolders[normalizedPath].orEmpty() + folder
      current.copy(foldersByPath = updatedFolders)
    }
    return folder.toFolder(normalizedPath)
  }

  fun renameFolder(path: String, folderId: String, name: String): Boolean {
    val normalizedName = normalizeFolderName(name) ?: return false
    return updateFolder(path = path, folderId = folderId) { folder, now ->
      if (folder.name == normalizedName) folder else folder.copy(name = normalizedName, updatedAt = now)
    }
  }

  fun setFolderStatus(path: String, folderId: String, status: AgentTaskFolderStatus): Boolean {
    return updateFolder(path = path, folderId = folderId) { folder, now ->
      val statusName = status.name
      if (folder.status == statusName) folder else folder.copy(status = statusName, updatedAt = now)
    }
  }

  fun deleteFolder(path: String, folderId: String): Boolean {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    val normalizedFolderId = normalizeFolderId(folderId) ?: return false
    var changed = false
    updateAndEmit { current ->
      val currentFolders = current.foldersByPath[normalizedPath].orEmpty()
      val nextFolders = currentFolders.filterNot { folder -> folder.id == normalizedFolderId }
      if (nextFolders.size != currentFolders.size) {
        changed = true
      }
      val currentAssignments = current.assignmentsByPath[normalizedPath].orEmpty()
      val nextAssignments = currentAssignments.filterNot { assignment -> assignment.folderId == normalizedFolderId }
      if (nextAssignments.size != currentAssignments.size) {
        changed = true
      }
      if (!changed) {
        return@updateAndEmit current
      }
      current.copy(
        foldersByPath = current.foldersByPath.updatedPathEntries(normalizedPath, nextFolders),
        assignmentsByPath = current.assignmentsByPath.updatedPathEntries(normalizedPath, nextAssignments),
      )
    }
    return changed
  }

  fun assignThread(path: String, provider: AgentSessionProvider, threadId: String, folderId: String): Boolean {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    val normalizedThreadId = threadId.trim().takeIf { it.isNotEmpty() } ?: return false
    val normalizedFolderId = normalizeFolderId(folderId) ?: return false
    val currentFolder = getFolder(normalizedPath, normalizedFolderId) ?: return false
    if (currentFolder.status != AgentTaskFolderStatus.IN_PROGRESS) return false

    val now = nowProvider()
    val assignment = AssignmentState(
      providerId = provider.value,
      threadId = normalizedThreadId,
      folderId = normalizedFolderId,
      assignedAt = now,
    )
    var changed = false
    updateAndEmit { current ->
      val currentAssignments = current.assignmentsByPath[normalizedPath].orEmpty()
      val nextAssignments = currentAssignments
        .filterNot { existing -> existing.providerId == provider.value && existing.threadId == normalizedThreadId }
        .let { assignments -> assignments + assignment }
      changed = nextAssignments != currentAssignments
      if (!changed) current else current.copy(assignmentsByPath = current.assignmentsByPath + (normalizedPath to nextAssignments))
    }
    return changed
  }

  fun unassignThread(path: String, provider: AgentSessionProvider, threadId: String): Boolean {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    val normalizedThreadId = threadId.trim().takeIf { it.isNotEmpty() } ?: return false
    var changed = false
    updateAndEmit { current ->
      val currentAssignments = current.assignmentsByPath[normalizedPath].orEmpty()
      val nextAssignments = currentAssignments.filterNot { assignment ->
        assignment.providerId == provider.value && assignment.threadId == normalizedThreadId
      }
      changed = nextAssignments.size != currentAssignments.size
      if (!changed) current else current.copy(assignmentsByPath = current.assignmentsByPath.updatedPathEntries(normalizedPath, nextAssignments))
    }
    return changed
  }

  fun setMetadata(path: String, folderId: String, key: String, value: String): Boolean {
    val normalizedKey = key.trim().takeIf { it.isNotEmpty() } ?: return false
    return updateFolder(path = path, folderId = folderId) { folder, now ->
      if (folder.metadata[normalizedKey] == value) {
        folder
      }
      else {
        folder.copy(metadata = folder.metadata + (normalizedKey to value), updatedAt = now)
      }
    }
  }

  fun deleteMetadata(path: String, folderId: String, key: String): Boolean {
    val normalizedKey = key.trim().takeIf { it.isNotEmpty() } ?: return false
    return updateFolder(path = path, folderId = folderId) { folder, now ->
      if (!folder.metadata.containsKey(normalizedKey)) {
        folder
      }
      else {
        folder.copy(metadata = folder.metadata - normalizedKey, updatedAt = now)
      }
    }
  }

  override fun loadState(state: TaskFolderState) {
    super.loadState(normalizeState(state))
    mutableStateFlow.value = this.state
  }

  private fun updateFolder(path: String, folderId: String, transform: (FolderState, Long) -> FolderState): Boolean {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    val normalizedFolderId = normalizeFolderId(folderId) ?: return false
    var changed = false
    updateAndEmit { current ->
      val currentFolders = current.foldersByPath[normalizedPath].orEmpty()
      val now = nowProvider()
      val nextFolders = currentFolders.map { folder ->
        if (folder.id != normalizedFolderId) return@map folder
        val updated = transform(folder, now)
        if (updated != folder) {
          changed = true
        }
        updated
      }
      if (!changed) current else current.copy(foldersByPath = current.foldersByPath + (normalizedPath to nextFolders))
    }
    return changed
  }

  private fun updateAndEmit(transform: (TaskFolderState) -> TaskFolderState) {
    updateState { current -> normalizeState(transform(current)) }
    mutableStateFlow.value = state
  }

  @Serializable
  data class TaskFolderState(
    @JvmField val foldersByPath: Map<String, List<FolderState>> = emptyMap(),
    @JvmField val assignmentsByPath: Map<String, List<AssignmentState>> = emptyMap(),
  )

  @Serializable
  data class FolderState(
    @JvmField val id: String,
    @JvmField val name: String,
    @JvmField val status: String = AgentTaskFolderStatus.IN_PROGRESS.name,
    @JvmField val metadata: Map<String, String> = emptyMap(),
    @JvmField val createdAt: Long = 0L,
    @JvmField val updatedAt: Long = 0L,
  )

  @Serializable
  data class AssignmentState(
    @JvmField val providerId: String,
    @JvmField val threadId: String,
    @JvmField val folderId: String,
    @JvmField val assignedAt: Long = 0L,
  )
}

private fun AgentTaskFolderService.TaskFolderState.toSnapshot(includeDone: Boolean): AgentTaskFolderSnapshot {
  val foldersByPath = LinkedHashMap<String, List<AgentTaskFolder>>()
  for ((path, folders) in this.foldersByPath) {
    val visibleFolders = folders.mapNotNull { folder ->
      val taskFolder = folder.toFolder(path)
      taskFolder.takeIf { includeDone || it.status == AgentTaskFolderStatus.IN_PROGRESS }
    }
    if (visibleFolders.isNotEmpty()) {
      foldersByPath[path] = visibleFolders
    }
  }

  val visibleFolderIdsByPath = foldersByPath.mapValues { (_, folders) -> folders.mapTo(LinkedHashSet()) { folder -> folder.id } }
  val assignmentsByPath = LinkedHashMap<String, List<AgentTaskFolderThreadAssignment>>()
  for ((path, assignments) in this.assignmentsByPath) {
    val visibleFolderIds = visibleFolderIdsByPath[path].orEmpty()
    val visibleAssignments = assignments.mapNotNull { assignment ->
      assignment.toAssignment(path)?.takeIf { it.folderId in visibleFolderIds }
    }
    if (visibleAssignments.isNotEmpty()) {
      assignmentsByPath[path] = visibleAssignments
    }
  }
  return AgentTaskFolderSnapshot(foldersByPath = foldersByPath, assignmentsByPath = assignmentsByPath)
}

private fun AgentTaskFolderService.FolderState.toFolder(path: String): AgentTaskFolder {
  val parsedStatus = runCatching { AgentTaskFolderStatus.valueOf(status) }.getOrDefault(AgentTaskFolderStatus.IN_PROGRESS)
  return AgentTaskFolder(
    path = normalizeAgentWorkbenchPath(path),
    id = id,
    name = name,
    status = parsedStatus,
    metadata = metadata,
    createdAt = createdAt,
    updatedAt = updatedAt,
  )
}

private fun AgentTaskFolderService.AssignmentState.toAssignment(path: String): AgentTaskFolderThreadAssignment? {
  val provider = AgentSessionProvider.fromOrNull(providerId) ?: return null
  return AgentTaskFolderThreadAssignment(
    path = normalizeAgentWorkbenchPath(path),
    provider = provider,
    threadId = threadId,
    folderId = folderId,
    assignedAt = assignedAt,
  )
}

private fun normalizeState(state: AgentTaskFolderService.TaskFolderState): AgentTaskFolderService.TaskFolderState {
  val normalizedFolders = LinkedHashMap<String, List<AgentTaskFolderService.FolderState>>()
  val validFolderIdsByPath = LinkedHashMap<String, Set<String>>()
  for ((path, folders) in state.foldersByPath) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    val normalizedPathFolders = folders
      .mapNotNull { folder -> normalizeFolderState(folder) }
      .distinctBy { folder -> folder.id }
      .sortedWith(compareBy<AgentTaskFolderService.FolderState> { folder -> folder.createdAt }.thenBy { folder -> folder.name.lowercase() })
    if (normalizedPathFolders.isNotEmpty()) {
      normalizedFolders[normalizedPath] = normalizedPathFolders
      validFolderIdsByPath[normalizedPath] = normalizedPathFolders.mapTo(LinkedHashSet()) { folder -> folder.id }
    }
  }

  val normalizedAssignments = LinkedHashMap<String, List<AgentTaskFolderService.AssignmentState>>()
  for ((path, assignments) in state.assignmentsByPath) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    val folderIds = validFolderIdsByPath[normalizedPath].orEmpty()
    val assignmentByThreadKey = LinkedHashMap<String, AgentTaskFolderService.AssignmentState>()
    assignments
      .mapNotNull { assignment -> normalizeAssignmentState(assignment) }
      .filter { assignment -> assignment.folderId in folderIds }
      .forEach { assignment -> assignmentByThreadKey["${assignment.providerId}:${assignment.threadId}"] = assignment }
    if (assignmentByThreadKey.isNotEmpty()) {
      normalizedAssignments[normalizedPath] = assignmentByThreadKey.values.sortedBy { assignment -> assignment.assignedAt }
    }
  }

  return AgentTaskFolderService.TaskFolderState(
    foldersByPath = normalizedFolders,
    assignmentsByPath = normalizedAssignments,
  )
}

private fun normalizeFolderState(folder: AgentTaskFolderService.FolderState): AgentTaskFolderService.FolderState? {
  val id = normalizeFolderId(folder.id) ?: return null
  val name = normalizeFolderName(folder.name) ?: return null
  val status = runCatching { AgentTaskFolderStatus.valueOf(folder.status) }.getOrDefault(AgentTaskFolderStatus.IN_PROGRESS)
  return folder.copy(
    id = id,
    name = name,
    status = status.name,
    metadata = normalizeMetadata(folder.metadata),
  )
}

private fun normalizeAssignmentState(assignment: AgentTaskFolderService.AssignmentState): AgentTaskFolderService.AssignmentState? {
  val provider = AgentSessionProvider.fromOrNull(assignment.providerId) ?: return null
  val threadId = assignment.threadId.trim().takeIf { it.isNotEmpty() } ?: return null
  val folderId = normalizeFolderId(assignment.folderId) ?: return null
  return assignment.copy(
    providerId = provider.value,
    threadId = threadId,
    folderId = folderId,
  )
}

private fun normalizeFolderName(name: String): String? {
  return name.trim().takeIf { it.isNotEmpty() }
}

private fun normalizeFolderId(folderId: String): String? {
  return folderId.trim().takeIf { it.isNotEmpty() }
}

private fun normalizeMetadata(metadata: Map<String, String>): Map<String, String> {
  if (metadata.isEmpty()) return emptyMap()
  return metadata.entries
    .asSequence()
    .mapNotNull { (key, value) -> key.trim().takeIf { it.isNotEmpty() }?.let { it to value } }
    .toMap(LinkedHashMap())
}

private fun <T> Map<String, List<T>>.updatedPathEntries(path: String, entries: List<T>): Map<String, List<T>> {
  if (entries.isEmpty()) {
    return this - path
  }
  return this + (path to entries)
}
