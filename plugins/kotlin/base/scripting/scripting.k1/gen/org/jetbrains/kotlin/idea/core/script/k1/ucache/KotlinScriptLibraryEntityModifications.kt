// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KotlinScriptLibraryEntityModifications")

package org.jetbrains.kotlin.idea.core.script.k1.ucache

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceSet
import org.jetbrains.kotlin.K1Deprecation

@K1Deprecation
@GeneratedCodeApiVersion(3)
interface KotlinScriptLibraryEntityBuilder : WorkspaceEntityBuilder<KotlinScriptLibraryEntity> {
  override var entitySource: EntitySource
  var name: String
  var roots: MutableList<KotlinScriptLibraryRoot>
  var indexSourceRoots: Boolean
  var usedInScripts: MutableSet<KotlinScriptId>
}

internal object KotlinScriptLibraryEntityType : EntityType<KotlinScriptLibraryEntity, KotlinScriptLibraryEntityBuilder>() {
  override val entityClass: Class<KotlinScriptLibraryEntity> get() = KotlinScriptLibraryEntity::class.java
  operator fun invoke(
    name: String,
    roots: List<KotlinScriptLibraryRoot>,
    indexSourceRoots: Boolean,
    usedInScripts: Set<KotlinScriptId>,
    entitySource: EntitySource,
    init: (KotlinScriptLibraryEntityBuilder.() -> Unit)? = null,
  ): KotlinScriptLibraryEntityBuilder {
    val builder = builder()
    builder.name = name
    builder.roots = roots.toMutableWorkspaceList()
    builder.indexSourceRoots = indexSourceRoots
    builder.usedInScripts = usedInScripts.toMutableWorkspaceSet()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

@K1Deprecation
fun MutableEntityStorage.modifyKotlinScriptLibraryEntity(
  entity: KotlinScriptLibraryEntity,
  modification: KotlinScriptLibraryEntityBuilder.() -> Unit,
): KotlinScriptLibraryEntity = modifyEntity(KotlinScriptLibraryEntityBuilder::class.java, entity, modification)

@K1Deprecation
@JvmOverloads
@JvmName("createKotlinScriptLibraryEntity")
fun KotlinScriptLibraryEntity(
  name: String,
  roots: List<KotlinScriptLibraryRoot>,
  indexSourceRoots: Boolean,
  usedInScripts: Set<KotlinScriptId>,
  entitySource: EntitySource,
  init: (KotlinScriptLibraryEntityBuilder.() -> Unit)? = null,
): KotlinScriptLibraryEntityBuilder = KotlinScriptLibraryEntityType(name, roots, indexSourceRoots, usedInScripts, entitySource, init)
