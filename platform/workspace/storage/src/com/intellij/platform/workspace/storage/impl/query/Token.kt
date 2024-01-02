// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.query

import com.intellij.platform.workspace.storage.EntityStorageSnapshot
import com.intellij.platform.workspace.storage.impl.EntityId
import com.intellij.platform.workspace.storage.impl.ImmutableEntityStorageImpl
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.asString
import com.intellij.platform.workspace.storage.impl.query.Token.WithEntityId
import com.intellij.platform.workspace.storage.impl.query.Token.WithInfo
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi

/**
 * Token is propagated in caches when entity storage changes.
 * The token contains the change itself. It's either entity id [WithEntityId] or data [WithInfo].
 * [operation] defines if this is adding information or removal.
 */
internal sealed interface Token {
  val operation: Operation

  /**
   * Key to use in intermediate calculations
   */
  fun key(): Any?

  class WithInfo(override val operation: Operation, val info: Any?) : Token {
    override fun key() = info
    override fun toString(): String = "Token(operation=$operation, info=${info.toString().take(20)})"
  }
  open class WithEntityId(override val operation: Operation, val entityId: EntityId) : Token {
    override fun key() = entityId
    override fun toString(): String = "Token(operation=$operation, entityId=${entityId.asString()})"
  }
}

public enum class Operation {
  ADDED,
  REMOVED,
}

@OptIn(EntityStorageInstrumentationApi::class)
internal fun Token.getData(snapshot: EntityStorageSnapshot): Any? {
  return when (this) {
    is WithEntityId -> (snapshot as ImmutableEntityStorageImpl).entityDataByIdOrDie(this.entityId).createEntity(snapshot)
    is WithInfo -> this.info
  }
}

internal fun Any?.toToken(operation: Operation): Token {
  return if (this is WorkspaceEntityBase) {
    WithEntityId(operation, this.id)
  }
  else {
    WithInfo(operation, this)
  }
}