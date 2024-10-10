// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.platform.kernel.EntityTypeProvider
import com.intellij.platform.project.ProjectEntity
import com.intellij.xdebugger.impl.rpc.XDebuggerEvaluatorId
import com.jetbrains.rhizomedb.EID
import com.jetbrains.rhizomedb.Entity
import com.jetbrains.rhizomedb.EntityType
import com.jetbrains.rhizomedb.RefFlags
import fleet.kernel.DurableEntityType
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
  val evaluatorId by EvaluatorId

  @ApiStatus.Internal
  companion object : DurableEntityType<XDebuggerActiveSessionEntity>(
    XDebuggerActiveSessionEntity::class.java.name,
    "com.intellij",
    ::XDebuggerActiveSessionEntity
  ) {
    val Project = requiredRef<ProjectEntity>("project", RefFlags.UNIQUE)
    var EvaluatorId = optionalValue<XDebuggerEvaluatorId>("evaluatorId", XDebuggerEvaluatorId.serializer())
  }
}