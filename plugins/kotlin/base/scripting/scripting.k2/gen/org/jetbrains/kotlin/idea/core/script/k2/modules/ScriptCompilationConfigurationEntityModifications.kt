// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ScriptCompilationConfigurationEntityModifications")

package org.jetbrains.kotlin.idea.core.script.k2.modules

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder

@GeneratedCodeApiVersion(3)
interface ScriptCompilationConfigurationEntityBuilder : WorkspaceEntityBuilder<ScriptCompilationConfigurationEntity> {
    override var entitySource: EntitySource
    var data: ByteArray
    var identity: ScriptCompilationConfigurationIdentity
}

internal object ScriptCompilationConfigurationEntityType :
    EntityType<ScriptCompilationConfigurationEntity, ScriptCompilationConfigurationEntityBuilder>() {
    override val entityClass: Class<ScriptCompilationConfigurationEntity> get() = ScriptCompilationConfigurationEntity::class.java
    operator fun invoke(
        data: ByteArray,
        identity: ScriptCompilationConfigurationIdentity,
        entitySource: EntitySource,
        init: (ScriptCompilationConfigurationEntityBuilder.() -> Unit)? = null,
    ): ScriptCompilationConfigurationEntityBuilder {
        val builder = builder()
        builder.data = data
        builder.identity = identity
        builder.entitySource = entitySource
        init?.invoke(builder)
        return builder
    }
}

fun MutableEntityStorage.modifyScriptCompilationConfigurationEntity(
    entity: ScriptCompilationConfigurationEntity,
    modification: ScriptCompilationConfigurationEntityBuilder.() -> Unit,
): ScriptCompilationConfigurationEntity = modifyEntity(ScriptCompilationConfigurationEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createScriptCompilationConfigurationEntity")
fun ScriptCompilationConfigurationEntity(
    data: ByteArray,
    identity: ScriptCompilationConfigurationIdentity,
    entitySource: EntitySource,
    init: (ScriptCompilationConfigurationEntityBuilder.() -> Unit)? = null,
): ScriptCompilationConfigurationEntityBuilder = ScriptCompilationConfigurationEntityType(data, identity, entitySource, init)
