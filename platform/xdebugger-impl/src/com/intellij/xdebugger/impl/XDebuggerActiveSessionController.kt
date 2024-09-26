// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.openapi.project.Project
import com.intellij.platform.kernel.EntityTypeProvider
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.project.ProjectEntity
import com.intellij.platform.project.asEntity
import com.jetbrains.rhizomedb.EID
import com.jetbrains.rhizomedb.Entity
import com.jetbrains.rhizomedb.EntityType
import com.jetbrains.rhizomedb.RefFlags
import com.jetbrains.rhizomedb.entity
import fleet.kernel.DurableEntityType
import fleet.kernel.change
import fleet.kernel.shared
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

private class XDebuggerActiveSessionEntityTypesProvider : EntityTypeProvider {
  override fun entityTypes(): List<EntityType<*>> {
    return listOf(
      XDebuggerActiveSessionEntity,
    )
  }
}

@ApiStatus.Internal
data class XDebuggerActiveSessionEntity(override val eid: EID) : Entity {
  val projectEntity by Project

  @ApiStatus.Internal
  companion object : DurableEntityType<XDebuggerActiveSessionEntity>(
    XDebuggerActiveSessionEntity::class.java.name,
    "com.intellij",
    ::XDebuggerActiveSessionEntity
  ) {
    val Project = requiredRef<ProjectEntity>("project", RefFlags.UNIQUE)
  }
}

internal fun synchronizeActiveSessionWithDb(coroutineScope: CoroutineScope, project: Project, activeSessionState: Flow<XDebugSessionImpl?>) {
  coroutineScope.launch(Dispatchers.IO) {
    activeSessionState.collect { newActiveSession ->
      withKernel {
        change {
          shared {
            val projectEntity = project.asEntity()
            // if there is no active session -> remove entity from DB
            if (newActiveSession == null) {
              entity(XDebuggerActiveSessionEntity.Project, projectEntity)?.delete()
            }
            else {
              // insert entity if active session is added
              XDebuggerActiveSessionEntity.upsert(XDebuggerActiveSessionEntity.Project, projectEntity) {
                it[XDebuggerActiveSessionEntity.Project] = projectEntity
              }
            }
          }
        }
      }
    }
  }
}