// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleTestEntityModifications")

package org.jetbrains.plugins.gradle.util.entity

import com.intellij.platform.workspace.storage.*
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase

@GeneratedCodeApiVersion(3)
interface GradleTestEntityBuilder : WorkspaceEntityBuilder<GradleTestEntity> {
  override var entitySource: EntitySource
  var phase: GradleSyncPhase
}

internal object GradleTestEntityType : EntityType<GradleTestEntity, GradleTestEntityBuilder>() {
  override val entityClass: Class<GradleTestEntity> get() = GradleTestEntity::class.java
  operator fun invoke(
    phase: GradleSyncPhase,
    entitySource: EntitySource,
    init: (GradleTestEntityBuilder.() -> Unit)? = null,
  ): GradleTestEntityBuilder {
    val builder = builder()
    builder.phase = phase
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyGradleTestEntity(
  entity: GradleTestEntity,
  modification: GradleTestEntityBuilder.() -> Unit,
): GradleTestEntity = modifyEntity(GradleTestEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createGradleTestEntity")
fun GradleTestEntity(
  phase: GradleSyncPhase,
  entitySource: EntitySource,
  init: (GradleTestEntityBuilder.() -> Unit)? = null,
): GradleTestEntityBuilder = GradleTestEntityType(phase, entitySource, init)
