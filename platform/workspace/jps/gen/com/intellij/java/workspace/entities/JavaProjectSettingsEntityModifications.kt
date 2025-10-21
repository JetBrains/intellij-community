// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JavaProjectSettingsEntityModifications")

package com.intellij.java.workspace.entities

import com.intellij.platform.workspace.jps.entities.ProjectSettingsEntityBuilder
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface JavaProjectSettingsEntityBuilder : WorkspaceEntityBuilder<JavaProjectSettingsEntity> {
  override var entitySource: EntitySource
  var projectSettings: ProjectSettingsEntityBuilder
  var compilerOutput: VirtualFileUrl?
  var languageLevelId: String?
  var languageLevelDefault: Boolean?
}

internal object JavaProjectSettingsEntityType : EntityType<JavaProjectSettingsEntity, JavaProjectSettingsEntityBuilder>() {
  override val entityClass: Class<JavaProjectSettingsEntity> get() = JavaProjectSettingsEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (JavaProjectSettingsEntityBuilder.() -> Unit)? = null,
  ): JavaProjectSettingsEntityBuilder {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    entitySource: EntitySource,
    init: (JavaProjectSettingsEntity.Builder.() -> Unit)? = null,
  ): JavaProjectSettingsEntity.Builder {
    val builder = builder() as JavaProjectSettingsEntity.Builder
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyJavaProjectSettingsEntity(
  entity: JavaProjectSettingsEntity,
  modification: JavaProjectSettingsEntityBuilder.() -> Unit,
): JavaProjectSettingsEntity = modifyEntity(JavaProjectSettingsEntityBuilder::class.java, entity, modification)

var ProjectSettingsEntityBuilder.javaProjectSettings: JavaProjectSettingsEntityBuilder?
  by WorkspaceEntity.extensionBuilder(JavaProjectSettingsEntity::class.java)

@JvmOverloads
@JvmName("createJavaProjectSettingsEntity")
fun JavaProjectSettingsEntity(
  entitySource: EntitySource,
  init: (JavaProjectSettingsEntityBuilder.() -> Unit)? = null,
): JavaProjectSettingsEntityBuilder = JavaProjectSettingsEntityType(entitySource, init)
