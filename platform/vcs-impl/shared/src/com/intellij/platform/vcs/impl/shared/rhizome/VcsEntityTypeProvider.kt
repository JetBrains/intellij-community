// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.rhizome

import com.intellij.platform.kernel.EntityTypeProvider
import com.intellij.platform.project.ProjectEntity
import com.jetbrains.rhizomedb.EID
import com.jetbrains.rhizomedb.Entity
import com.jetbrains.rhizomedb.EntityType
import com.jetbrains.rhizomedb.RefFlags
import fleet.kernel.DurableEntityType
import kotlinx.serialization.builtins.serializer
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class VcsEntityTypeProvider : EntityTypeProvider {
  override fun entityTypes(): List<EntityType<*>> = listOf(RepositoryCountEntity)
}

@ApiStatus.Internal
data class RepositoryCountEntity(override val eid: EID) : Entity {
  val project: ProjectEntity by Project
  val count: Int by Count

  @ApiStatus.Internal
  companion object : DurableEntityType<RepositoryCountEntity>(RepositoryCountEntity::class.java.name, "com.intellij", ::RepositoryCountEntity) {
    val Project: Required<ProjectEntity> = requiredRef("project", RefFlags.UNIQUE)
    val Count: Required<Int> = requiredValue("count", Int.serializer())
  }
}