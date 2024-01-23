// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.query

import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.impl.EntityId
import com.intellij.platform.workspace.storage.impl.ImmutableEntityStorageImpl
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.asString
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import java.util.*

internal sealed interface Match {
  /**
   * Check if this match still actual. For case of [MatchWithEntityId] it checks if EntityId still presented in the snapshot
   */
  fun isValid(snapshot: ImmutableEntityStorage): Boolean
}

internal class MatchWithData(val data: Any?, val basedOn: Match? = null): Match {
  override fun isValid(snapshot: ImmutableEntityStorage): Boolean = true

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as MatchWithData

    if (data != other.data) return false
    if (basedOn != other.basedOn) return false

    return true
  }

  private val hashCode = Objects.hash(data, basedOn)
  override fun hashCode(): Int = hashCode

  override fun toString(): String {
    if (basedOn != null) {
      return "MatchWithData($data, basedOn = $basedOn)"
    }
    else {
      return "MatchWithData($data)"
    }
  }
}

internal class MatchWithEntityId(val entityId: EntityId, val basedOn: Match? = null): Match {
  override fun isValid(snapshot: ImmutableEntityStorage): Boolean {
    return (snapshot as ImmutableEntityStorageImpl).entityDataById(entityId) != null
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as MatchWithEntityId

    if (entityId != other.entityId) return false
    if (basedOn != other.basedOn) return false

    return true
  }

  private val hashCode = Objects.hash(entityId, basedOn)
  override fun hashCode(): Int = hashCode

  override fun toString(): String {
    if (basedOn != null) {
      return "MatchWithEntityId(${entityId.asString()}, basedOn = $basedOn)"
    } else {
      return "MatchWithEntityId(${entityId.asString()})"
    }
  }
}

public enum class Operation {
  ADDED,
  REMOVED,
}

@OptIn(EntityStorageInstrumentationApi::class)
internal fun Match.getData(snapshot: ImmutableEntityStorage): Any? {
  return when (this) {
    is MatchWithEntityId -> (snapshot as ImmutableEntityStorageImpl).entityDataByIdOrDie(this.entityId).createEntity(snapshot)
    is MatchWithData -> this.data
  }
}

internal fun Any?.toMatch(basedOn: Match?): Match {
  return if (this is WorkspaceEntityBase) {
    MatchWithEntityId(this.id, basedOn)
  }
  else {
    MatchWithData(this, basedOn)
  }
}