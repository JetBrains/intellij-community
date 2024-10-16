// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.shared.rhizome

import com.intellij.platform.kernel.EntityTypeProvider
import com.intellij.platform.project.ProjectEntity
import com.jetbrains.rhizomedb.EID
import com.jetbrains.rhizomedb.Entity
import com.jetbrains.rhizomedb.EntityType
import com.jetbrains.rhizomedb.RefFlags
import fleet.kernel.DurableEntityType
import kotlinx.serialization.builtins.serializer

class VcsEntityTypeProvider : EntityTypeProvider {
  override fun entityTypes(): List<EntityType<*>> = listOf(RepositoryCountEntity)
}

data class RepositoryCountEntity(override val eid: EID) : Entity {
  val project: ProjectEntity by Project
  val count: Int by Count

  companion object : DurableEntityType<RepositoryCountEntity>(RepositoryCountEntity::class.java.name, "com.intellij", ::RepositoryCountEntity) {
    val Project = requiredRef<ProjectEntity>("project", RefFlags.UNIQUE)
    val Count = requiredValue("count", Int.serializer())
  }
}