// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("ScriptDefinitionTemplateEntityModifications")

package org.jetbrains.kotlin.idea.core.script.k2.definitions

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

@GeneratedCodeApiVersion(3)
interface ScriptDefinitionTemplateEntityBuilder : WorkspaceEntityBuilder<ScriptDefinitionTemplateEntity> {
    override var entitySource: EntitySource
    var templateFqns: MutableList<String>
    var classpath: MutableList<String>
}

internal object ScriptDefinitionTemplateEntityType : EntityType<ScriptDefinitionTemplateEntity, ScriptDefinitionTemplateEntityBuilder>() {
    override val entityClass: Class<ScriptDefinitionTemplateEntity> get() = ScriptDefinitionTemplateEntity::class.java
    operator fun invoke(
        templateFqns: List<String>,
        classpath: List<String>,
        entitySource: EntitySource,
        init: (ScriptDefinitionTemplateEntityBuilder.() -> Unit)? = null,
    ): ScriptDefinitionTemplateEntityBuilder {
        val builder = builder()
        builder.templateFqns = templateFqns.toMutableWorkspaceList()
        builder.classpath = classpath.toMutableWorkspaceList()
        builder.entitySource = entitySource
        init?.invoke(builder)
        return builder
    }
}

fun MutableEntityStorage.modifyScriptDefinitionTemplateEntity(
    entity: ScriptDefinitionTemplateEntity,
    modification: ScriptDefinitionTemplateEntityBuilder.() -> Unit,
): ScriptDefinitionTemplateEntity = modifyEntity(ScriptDefinitionTemplateEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createScriptDefinitionTemplateEntity")
fun ScriptDefinitionTemplateEntity(
    templateFqns: List<String>,
    classpath: List<String>,
    entitySource: EntitySource,
    init: (ScriptDefinitionTemplateEntityBuilder.() -> Unit)? = null,
): ScriptDefinitionTemplateEntityBuilder = ScriptDefinitionTemplateEntityType(templateFqns, classpath, entitySource, init)
