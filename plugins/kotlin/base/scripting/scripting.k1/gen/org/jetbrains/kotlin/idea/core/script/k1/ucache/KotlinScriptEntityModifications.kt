// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KotlinScriptEntityModifications")

package org.jetbrains.kotlin.idea.core.script.k1.ucache

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceSet
import org.jetbrains.kotlin.K1Deprecation

@K1Deprecation
@GeneratedCodeApiVersion(3)
interface KotlinScriptEntityBuilder : WorkspaceEntityBuilder<KotlinScriptEntity> {
  override var entitySource: EntitySource
  var path: String
  var dependencies: MutableSet<KotlinScriptLibraryId>
}

internal object KotlinScriptEntityType : EntityType<KotlinScriptEntity, KotlinScriptEntityBuilder>() {
  override val entityClass: Class<KotlinScriptEntity> get() = KotlinScriptEntity::class.java
  operator fun invoke(
    path: String,
    dependencies: Set<KotlinScriptLibraryId>,
    entitySource: EntitySource,
    init: (KotlinScriptEntityBuilder.() -> Unit)? = null,
  ): KotlinScriptEntityBuilder {
    val builder = builder()
    builder.path = path
    builder.dependencies = dependencies.toMutableWorkspaceSet()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

@K1Deprecation
fun MutableEntityStorage.modifyKotlinScriptEntity(
  entity: KotlinScriptEntity,
  modification: KotlinScriptEntityBuilder.() -> Unit,
): KotlinScriptEntity = modifyEntity(KotlinScriptEntityBuilder::class.java, entity, modification)

@K1Deprecation
@JvmOverloads
@JvmName("createKotlinScriptEntity")
fun KotlinScriptEntity(
  path: String,
  dependencies: Set<KotlinScriptLibraryId>,
  entitySource: EntitySource,
  init: (KotlinScriptEntityBuilder.() -> Unit)? = null,
): KotlinScriptEntityBuilder = KotlinScriptEntityType(path, dependencies, entitySource, init)
