// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util.entity

import com.intellij.platform.workspace.storage.*
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase

interface GradleTestEntity : WorkspaceEntityWithSymbolicId {

  val phase: GradleSyncPhase

  override val symbolicId: GradleTestEntityId
    get() = GradleTestEntityId(phase)

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<GradleTestEntity> {
    override var entitySource: EntitySource
    var phase: GradleSyncPhase
  }

  companion object : EntityType<GradleTestEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      phase: GradleSyncPhase,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.phase = phase
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyGradleTestEntity(
  entity: GradleTestEntity,
  modification: GradleTestEntity.Builder.() -> Unit,
): GradleTestEntity {
  return modifyEntity(GradleTestEntity.Builder::class.java, entity, modification)
}
//endregion
