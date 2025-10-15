// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k1.ucache

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceSet
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface ModifiableKotlinScriptEntity : ModifiableWorkspaceEntity<KotlinScriptEntity> {
  override var entitySource: EntitySource
  var path: String
  var dependencies: MutableSet<KotlinScriptLibraryId>
}

internal object KotlinScriptEntityType : EntityType<KotlinScriptEntity, ModifiableKotlinScriptEntity>() {
  override val entityClass: Class<KotlinScriptEntity> get() = KotlinScriptEntity::class.java
  operator fun invoke(
    path: String,
    dependencies: Set<KotlinScriptLibraryId>,
    entitySource: EntitySource,
    init: (ModifiableKotlinScriptEntity.() -> Unit)? = null,
  ): ModifiableKotlinScriptEntity {
    val builder = builder()
    builder.path = path
    builder.dependencies = dependencies.toMutableWorkspaceSet()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyKotlinScriptEntity(
  entity: KotlinScriptEntity,
  modification: ModifiableKotlinScriptEntity.() -> Unit,
): KotlinScriptEntity = modifyEntity(ModifiableKotlinScriptEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createKotlinScriptEntity")
fun KotlinScriptEntity(
  path: String,
  dependencies: Set<KotlinScriptLibraryId>,
  entitySource: EntitySource,
  init: (ModifiableKotlinScriptEntity.() -> Unit)? = null,
): ModifiableKotlinScriptEntity = KotlinScriptEntityType(path, dependencies, entitySource, init)
