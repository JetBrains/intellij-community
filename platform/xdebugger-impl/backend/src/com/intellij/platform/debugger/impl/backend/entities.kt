// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.platform.kernel.EntityTypeProvider
import com.intellij.platform.project.ProjectEntity
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHint
import com.jetbrains.rhizomedb.EID
import com.jetbrains.rhizomedb.Entity
import com.jetbrains.rhizomedb.EntityType
import org.jetbrains.annotations.ApiStatus

private class BackendXDebuggerEntityTypesProvider : EntityTypeProvider {
  override fun entityTypes(): List<EntityType<*>> {
    return listOf(
      LocalValueHintEntity,
    )
  }
}

@ApiStatus.Internal
internal data class LocalValueHintEntity(override val eid: EID) : Entity {
  val projectEntity by Project
  val hint by Hint

  companion object : EntityType<LocalValueHintEntity>(
    LocalValueHintEntity::class.java.name,
    "com.intellij",
    ::LocalValueHintEntity
  ) {
    val Project = requiredRef<ProjectEntity>("project")
    val Hint = requiredTransient<AbstractValueHint>("hint")
  }
}