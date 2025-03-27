// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rhizome

import com.intellij.platform.kernel.EntityTypeProvider
import com.intellij.platform.project.ProjectEntity
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import com.jetbrains.rhizomedb.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.awt.Color

private class XDebuggerEntityTypesProvider : EntityTypeProvider {
  override fun entityTypes(): List<EntityType<*>> {
    return listOf(
      XDebugSessionEntity,
    )
  }
}

/**
 * Represents an entity which holds reference to the [XDebugSession].
 * Such an entity allows to send [XDebugSessionId] to a client and afterward find [XDebugSession] by this id.
 *
 * This entity cannot be shared between frontend and backend.
 * It should be used only on the backend side.
 *
 * @see [storeXDebugSessionInDb]
 */
data class XDebugSessionEntity(override val eid: EID) : Entity {
  val sessionId: XDebugSessionId by SessionId
  val session: XDebugSession by Session
  val projectEntity: ProjectEntity by ProjectEntity

  companion object : EntityType<XDebugSessionEntity>(
    XDebugSessionEntity::class.java.name,
    "com.intellij.xdebugger.impl.rhizome",
    ::XDebugSessionEntity
  ) {
    val SessionId: Required<XDebugSessionId> = requiredTransient("sessionId", Indexing.UNIQUE)
    val Session: Required<XDebugSession> = requiredTransient("session", Indexing.UNIQUE)
    val ProjectEntity: Required<ProjectEntity> = requiredRef("project", RefFlags.CASCADE_DELETE_BY)
  }
}

// TODO[IJPL-160146]: Implement implement Color serialization
@Serializable
data class XValueMarkerDto(val text: String, @Transient val color: Color? = null, val tooltipText: String?)