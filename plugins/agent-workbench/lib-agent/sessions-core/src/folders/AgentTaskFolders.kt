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
import com.intellij.util.io.Ksuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

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
    val normalizedPath = normalizeFolderPath(path) ?: return emptyList()
    return foldersByPath[normalizedPath].orEmpty()
      .filter { folder -> includeDone || folder.status == AgentTaskFolderStatus.IN_PROGRESS }
  }

  fun folder(folderId: String): AgentTaskFolder? {
    val normalizedFolderId = normalizeFolderId(folderId) ?: return null
    return foldersByPath.values.asSequence()
      .flatMap { folders -> folders.asSequence() }
      .firstOrNull { folder -> folder.id == normalizedFolderId }
  }

  fun folder(path: String, folderId: String): AgentTaskFolder? {
    val normalizedPath = normalizeFolderPath(path) ?: return null
    val normalizedFolderId = normalizeFolderId(folderId) ?: return null
    return foldersByPath[normalizedPath].orEmpty().firstOrNull { folder -> folder.id == normalizedFolderId }
  }

  fun folderForThread(path: String, provider: AgentSessionProvider, threadId: String): AgentTaskFolder? {
    val normalizedPath = normalizeFolderPath(path) ?: return null
    val normalizedThreadId = threadId.trim().takeIf { it.isNotEmpty() } ?: return null
    val assignment = assignmentsByPath[normalizedPath].orEmpty().firstOrNull { assignment ->
      assignment.provider == provider && assignment.threadId == normalizedThreadId
    } ?: return null
    return folder(normalizedPath, assignment.folderId)
  }

  fun assignments(path: String, folderId: String? = null): List<AgentTaskFolderThreadAssignment> {
    val normalizedPath = normalizeFolderPath(path) ?: return emptyList()
    val normalizedFolderId = folderId?.let { normalizeFolderId(it) ?: return emptyList() }
    return assignmentsByPath[normalizedPath].orEmpty()
      .filter { assignment -> normalizedFolderId == null || assignment.folderId == normalizedFolderId }
      .sortedBy { assignment -> assignment.assignedAt }
  }

  fun assignmentsForFolder(folderId: String): List<AgentTaskFolderThreadAssignment> {
    val normalizedFolderId = normalizeFolderId(folderId) ?: return emptyList()
    return assignmentsByPath.values.asSequence()
      .flatMap { assignments -> assignments.asSequence() }
      .filter { assignment -> assignment.folderId == normalizedFolderId }
      .sortedBy { assignment -> assignment.assignedAt }
      .toList()
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

  fun getFolder(folderId: String): AgentTaskFolder? {
    val normalizedFolderId = normalizeFolderId(folderId) ?: return null
    val folder = state.foldersById[normalizedFolderId] ?: return null
    val path = folder.paths.firstOrNull() ?: return null
    return folder.toFolder(path = path, id = normalizedFolderId)
  }

  fun getFolderForThread(path: String, provider: AgentSessionProvider, threadId: String): AgentTaskFolder? {
    return snapshot(includeDone = true).folderForThread(path, provider, threadId)
  }

  fun listFolderThreadAssignments(folderId: String): List<AgentTaskFolderThreadAssignment> {
    return snapshot(includeDone = true).assignmentsForFolder(folderId)
  }

  fun createFolder(path: String, name: String, metadata: Map<String, String> = emptyMap()): AgentTaskFolder? {
    val normalizedName = normalizeFolderName(name) ?: return null
    val normalizedPath = normalizeFolderPath(path) ?: return null
    val now = nowProvider()
    val folderId = newFolderId()
    val folder = FolderState(
      name = normalizedName,
      status = AgentTaskFolderStatus.IN_PROGRESS.name,
      metadata = normalizeMetadata(metadata),
      paths = listOf(normalizedPath),
      createdAt = now,
      updatedAt = now,
    )
    updateAndEmit { current ->
      current.copy(foldersById = current.foldersById + (folderId to folder))
    }
    return folder.toFolder(path = normalizedPath, id = folderId)
  }

  fun renameFolder(folderId: String, name: String): Boolean {
    val normalizedName = normalizeFolderName(name) ?: return false
    return updateFolder(folderId = folderId) { folder, now ->
      if (folder.name == normalizedName) folder else folder.copy(name = normalizedName, updatedAt = now)
    }
  }

  fun setFolderStatus(folderId: String, status: AgentTaskFolderStatus): Boolean {
    return updateFolder(folderId = folderId) { folder, now ->
      val statusName = status.name
      if (folder.status == statusName) folder else folder.copy(status = statusName, updatedAt = now)
    }
  }

  fun deleteFolder(folderId: String): Boolean {
    val normalizedFolderId = normalizeFolderId(folderId) ?: return false
    var changed = false
    updateAndEmit { current ->
      val nextFolders = current.foldersById - normalizedFolderId
      if (nextFolders.size != current.foldersById.size) {
        changed = true
      }
      val nextAssignments = current.assignments.filterNot { assignment -> assignment.folderId == normalizedFolderId }
      if (nextAssignments.size != current.assignments.size) {
        changed = true
      }
      if (!changed) {
        return@updateAndEmit current
      }
      current.copy(foldersById = nextFolders, assignments = nextAssignments)
    }
    return changed
  }

  fun assignThread(path: String, provider: AgentSessionProvider, threadId: String, folderId: String): Boolean {
    val normalizedPath = normalizeFolderPath(path) ?: return false
    val normalizedThreadId = threadId.trim().takeIf { it.isNotEmpty() } ?: return false
    val normalizedFolderId = normalizeFolderId(folderId) ?: return false
    val currentFolder = state.foldersById[normalizedFolderId] ?: return false
    if (currentFolder.status != AgentTaskFolderStatus.IN_PROGRESS.name) return false
    if (normalizedPath !in currentFolder.paths) return false

    val now = nowProvider()
    val assignment = AssignmentState(
      path = normalizedPath,
      providerId = provider.value,
      threadId = normalizedThreadId,
      folderId = normalizedFolderId,
      assignedAt = now,
    )
    var changed = false
    updateAndEmit { current ->
      val nextAssignments = current.assignments
        .filterNot { existing -> existing.path == normalizedPath && existing.providerId == provider.value && existing.threadId == normalizedThreadId }
        .let { assignments -> assignments + assignment }
      changed = nextAssignments != current.assignments
      if (!changed) current else current.copy(assignments = nextAssignments)
    }
    return changed
  }

  fun unassignThread(path: String, provider: AgentSessionProvider, threadId: String): Boolean {
    val normalizedPath = normalizeFolderPath(path) ?: return false
    val normalizedThreadId = threadId.trim().takeIf { it.isNotEmpty() } ?: return false
    var changed = false
    updateAndEmit { current ->
      val nextAssignments = current.assignments.filterNot { assignment ->
        assignment.path == normalizedPath && assignment.providerId == provider.value && assignment.threadId == normalizedThreadId
      }
      changed = nextAssignments.size != current.assignments.size
      if (!changed) current else current.copy(assignments = nextAssignments)
    }
    return changed
  }

  fun setMetadata(folderId: String, key: String, value: String): Boolean {
    val normalizedKey = key.trim().takeIf { it.isNotEmpty() } ?: return false
    return updateFolder(folderId = folderId) { folder, now ->
      if (folder.metadata[normalizedKey] == value) {
        folder
      }
      else {
        folder.copy(metadata = folder.metadata + (normalizedKey to value), updatedAt = now)
      }
    }
  }

  fun deleteMetadata(folderId: String, key: String): Boolean {
    val normalizedKey = key.trim().takeIf { it.isNotEmpty() } ?: return false
    return updateFolder(folderId = folderId) { folder, now ->
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

  private fun updateFolder(folderId: String, transform: (FolderState, Long) -> FolderState): Boolean {
    val normalizedFolderId = normalizeFolderId(folderId) ?: return false
    var changed = false
    updateAndEmit { current ->
      val currentFolder = current.foldersById[normalizedFolderId] ?: return@updateAndEmit current
      val updated = transform(currentFolder, nowProvider())
      if (updated != currentFolder) {
        changed = true
      }
      if (!changed) current else current.copy(foldersById = current.foldersById + (normalizedFolderId to updated))
    }
    return changed
  }

  private fun updateAndEmit(transform: (TaskFolderState) -> TaskFolderState) {
    updateState { current -> normalizeState(transform(current)) }
    mutableStateFlow.value = state
  }

  @Serializable
  data class TaskFolderState(
    @JvmField val foldersById: Map<String, FolderState> = emptyMap(),
    @JvmField val assignments: List<AssignmentState> = emptyList(),
  )

  @Serializable
  data class FolderState(
    @JvmField val name: String,
    @JvmField val status: String = AgentTaskFolderStatus.IN_PROGRESS.name,
    @JvmField val metadata: Map<String, String> = emptyMap(),
    @JvmField val paths: List<String> = emptyList(),
    @JvmField val createdAt: Long = 0L,
    @JvmField val updatedAt: Long = 0L,
  )

  @Serializable
  data class AssignmentState(
    @JvmField val path: String,
    @JvmField val providerId: String,
    @JvmField val threadId: String,
    @JvmField val folderId: String,
    @JvmField val assignedAt: Long = 0L,
  )
}

private fun AgentTaskFolderService.TaskFolderState.toSnapshot(includeDone: Boolean): AgentTaskFolderSnapshot {
  val foldersByPath = LinkedHashMap<String, MutableList<AgentTaskFolder>>()
  for ((folderId, folder) in this.foldersById) {
    val parsedStatus = parseFolderStatus(folder.status)
    if (!includeDone && parsedStatus != AgentTaskFolderStatus.IN_PROGRESS) {
      continue
    }
    folder.paths.forEach { path ->
      foldersByPath.getOrPut(path) { ArrayList() } += folder.toFolder(path = path, id = folderId)
    }
  }

  val visibleFolderIdsByPath = foldersByPath.mapValues { (_, folders) -> folders.mapTo(LinkedHashSet()) { folder -> folder.id } }
  val assignmentsByPath = LinkedHashMap<String, MutableList<AgentTaskFolderThreadAssignment>>()
  for (assignment in this.assignments) {
    val visibleFolderIds = visibleFolderIdsByPath[assignment.path].orEmpty()
    val visibleAssignment = assignment.toAssignment()?.takeIf { it.folderId in visibleFolderIds } ?: continue
    assignmentsByPath.getOrPut(visibleAssignment.path) { ArrayList() } += visibleAssignment
  }

  return AgentTaskFolderSnapshot(
    foldersByPath = foldersByPath.mapValues { (_, folders) -> folders.toList() },
    assignmentsByPath = assignmentsByPath.mapValues { (_, assignments) -> assignments.sortedBy { assignment -> assignment.assignedAt } },
  )
}

private fun AgentTaskFolderService.FolderState.toFolder(path: String, id: String): AgentTaskFolder {
  return AgentTaskFolder(
    path = path,
    id = id,
    name = name,
    status = parseFolderStatus(status),
    metadata = metadata,
    createdAt = createdAt,
    updatedAt = updatedAt,
  )
}

private fun AgentTaskFolderService.AssignmentState.toAssignment(): AgentTaskFolderThreadAssignment? {
  val provider = AgentSessionProvider.fromOrNull(providerId) ?: return null
  return AgentTaskFolderThreadAssignment(
    path = path,
    provider = provider,
    threadId = threadId,
    folderId = folderId,
    assignedAt = assignedAt,
  )
}

private fun normalizeState(state: AgentTaskFolderService.TaskFolderState): AgentTaskFolderService.TaskFolderState {
  val normalizedFolders = LinkedHashMap<String, AgentTaskFolderService.FolderState>()
  state.foldersById.entries
    .asSequence()
    .mapNotNull { (folderId, folder) ->
      val normalizedFolderId = normalizeFolderId(folderId) ?: return@mapNotNull null
      val normalizedFolder = normalizeFolderState(folder) ?: return@mapNotNull null
      normalizedFolderId to normalizedFolder
    }
    .sortedWith(compareBy<Pair<String, AgentTaskFolderService.FolderState>> { (_, folder) -> folder.createdAt }
                  .thenBy { (_, folder) -> folder.name.lowercase() }
                  .thenBy { (folderId, _) -> folderId })
    .forEach { (folderId, folder) -> normalizedFolders.putIfAbsent(folderId, folder) }

  val assignmentByThreadKey = LinkedHashMap<AssignmentThreadKey, AgentTaskFolderService.AssignmentState>()
  state.assignments
    .asSequence()
    .mapNotNull { assignment -> normalizeAssignmentState(assignment) }
    .filter { assignment ->
      val folder = normalizedFolders[assignment.folderId]
      folder != null && assignment.path in folder.paths
    }
    .forEach { assignment ->
      assignmentByThreadKey[AssignmentThreadKey(assignment.path, assignment.providerId, assignment.threadId)] = assignment
    }

  return AgentTaskFolderService.TaskFolderState(
    foldersById = normalizedFolders,
    assignments = assignmentByThreadKey.values.sortedBy { assignment -> assignment.assignedAt },
  )
}

private fun normalizeFolderState(folder: AgentTaskFolderService.FolderState): AgentTaskFolderService.FolderState? {
  val name = normalizeFolderName(folder.name) ?: return null
  val paths = folder.paths
    .mapNotNull(::normalizeFolderPath)
    .distinct()
  if (paths.isEmpty()) return null
  return folder.copy(
    name = name,
    status = parseFolderStatus(folder.status).name,
    metadata = normalizeMetadata(folder.metadata),
    paths = paths,
  )
}

private fun normalizeAssignmentState(assignment: AgentTaskFolderService.AssignmentState): AgentTaskFolderService.AssignmentState? {
  val path = normalizeFolderPath(assignment.path) ?: return null
  val provider = AgentSessionProvider.fromOrNull(assignment.providerId) ?: return null
  val threadId = assignment.threadId.trim().takeIf { it.isNotEmpty() } ?: return null
  val folderId = normalizeFolderId(assignment.folderId) ?: return null
  return assignment.copy(
    path = path,
    providerId = provider.value,
    threadId = threadId,
    folderId = folderId,
  )
}

private fun parseFolderStatus(status: String): AgentTaskFolderStatus {
  return runCatching { AgentTaskFolderStatus.valueOf(status) }.getOrDefault(AgentTaskFolderStatus.IN_PROGRESS)
}

private fun normalizeFolderPath(path: String): String? {
  val trimmedPath = path.trim().takeIf { it.isNotEmpty() } ?: return null
  return normalizeAgentWorkbenchPath(trimmedPath)
}

private fun normalizeFolderName(name: String): String? {
  return name.trim().takeIf { it.isNotEmpty() }
}

private fun normalizeFolderId(folderId: String): String? {
  val id = folderId.trim().takeIf { it.isNotEmpty() } ?: return null
  return id.takeIf(Ksuid::isValid)
}

private fun normalizeMetadata(metadata: Map<String, String>): Map<String, String> {
  if (metadata.isEmpty()) return emptyMap()
  return metadata.entries
    .asSequence()
    .mapNotNull { (key, value) -> key.trim().takeIf { it.isNotEmpty() }?.let { it to value } }
    .toMap(LinkedHashMap())
}

private fun newFolderId(): String = Ksuid.generate()

private data class AssignmentThreadKey(
  @JvmField val path: String,
  @JvmField val providerId: String,
  @JvmField val threadId: String,
)
