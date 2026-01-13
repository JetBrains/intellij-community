// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleExternalProjectEntityModifications")

package org.jetbrains.plugins.gradle.model.projectModel

import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntity
import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityBuilder
import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface GradleExternalProjectEntityBuilder : WorkspaceEntityBuilder<GradleExternalProjectEntity> {
    override var entitySource: EntitySource
    var externalProject: ExternalProjectEntityBuilder
    var externalProjectId: ExternalProjectEntityId
    var gradleVersion: String
}

internal object GradleExternalProjectEntityType : EntityType<GradleExternalProjectEntity, GradleExternalProjectEntityBuilder>() {
    override val entityClass: Class<GradleExternalProjectEntity> get() = GradleExternalProjectEntity::class.java
    operator fun invoke(
        externalProjectId: ExternalProjectEntityId,
        gradleVersion: String,
        entitySource: EntitySource,
        init: (GradleExternalProjectEntityBuilder.() -> Unit)? = null,
    ): GradleExternalProjectEntityBuilder {
        val builder = builder()
        builder.externalProjectId = externalProjectId
        builder.gradleVersion = gradleVersion
        builder.entitySource = entitySource
        init?.invoke(builder)
        return builder
    }
}

fun MutableEntityStorage.modifyGradleExternalProjectEntity(
    entity: GradleExternalProjectEntity,
    modification: GradleExternalProjectEntityBuilder.() -> Unit,
): GradleExternalProjectEntity = modifyEntity(GradleExternalProjectEntityBuilder::class.java, entity, modification)

var ExternalProjectEntityBuilder.gradleInfo: GradleExternalProjectEntityBuilder
        by WorkspaceEntity.extensionBuilder(GradleExternalProjectEntity::class.java)


@JvmOverloads
@JvmName("createGradleExternalProjectEntity")
fun GradleExternalProjectEntity(
    externalProjectId: ExternalProjectEntityId,
    gradleVersion: String,
    entitySource: EntitySource,
    init: (GradleExternalProjectEntityBuilder.() -> Unit)? = null,
): GradleExternalProjectEntityBuilder = GradleExternalProjectEntityType(externalProjectId, gradleVersion, entitySource, init)
