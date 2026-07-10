// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.io.Ksuid
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/**
 * Only the [Ksuid] form is accepted, so a value is always safe to use as a file name (it cannot contain path separators or `..`).
 * Construct it via [generate] or [parseOrNull] only.
 */
@ApiStatus.Internal
class ProjectWorkspaceId private constructor(val value: String) {
  override fun toString(): String = value

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ProjectWorkspaceId
    return value == other.value
  }

  override fun hashCode(): Int = value.hashCode()

  companion object {
    /** Generates a new id in the [Ksuid] form. */
    fun generate(): ProjectWorkspaceId = ProjectWorkspaceId(Ksuid.generate())

    /** Accepts only a valid [Ksuid]. Returns `null` otherwise. */
    fun parseOrNull(value: String): ProjectWorkspaceId? = if (Ksuid.isValid(value)) ProjectWorkspaceId(value) else null
  }
}

@ApiStatus.Internal
interface ProjectIdManager {
  var id: ProjectWorkspaceId?
}

@State(name = "ProjectId", storages = [(Storage(StoragePathMacros.WORKSPACE_FILE))], reportStatistic = false)
internal class ProjectIdManagerImpl : SerializablePersistentStateComponent<ProjectIdManagerImpl.State>(State()), ProjectIdManager {
  override var id: ProjectWorkspaceId?
    get() = state.id?.let(ProjectWorkspaceId::parseOrNull)
    set(value) {
      updateState { it }
      state.id = value?.value
    }

  // Drop a tampered/invalid persisted id at the load boundary, so a fresh valid one is generated on demand.
  override fun loadState(state: State) {
    val raw = state.id
    if (raw != null && ProjectWorkspaceId.parseOrNull(raw) == null) {
      thisLogger().warn("Ignoring invalid project id from workspace.xml: $raw")
      state.id = null
    }
    super.loadState(state)
  }

  class State {
    @Attribute
    @JvmField
    var id: String? = null
  }
}

@TestOnly
private class MockProjectIdManager : ProjectIdManager {
  override var id: ProjectWorkspaceId? = null
}
