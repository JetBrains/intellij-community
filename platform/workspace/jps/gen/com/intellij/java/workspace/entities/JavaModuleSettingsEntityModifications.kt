// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JavaModuleSettingsEntityModifications")

package com.intellij.java.workspace.entities

import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface JavaModuleSettingsEntityBuilder : WorkspaceEntityBuilder<JavaModuleSettingsEntity> {
  override var entitySource: EntitySource
  var module: ModuleEntityBuilder
  var inheritedCompilerOutput: Boolean
  var excludeOutput: Boolean
  var compilerOutput: VirtualFileUrl?
  var compilerOutputForTests: VirtualFileUrl?
  var languageLevelId: String?
  var manifestAttributes: Map<String, String>
}

internal object JavaModuleSettingsEntityType : EntityType<JavaModuleSettingsEntity, JavaModuleSettingsEntityBuilder>() {
  override val entityClass: Class<JavaModuleSettingsEntity> get() = JavaModuleSettingsEntity::class.java
  operator fun invoke(
    inheritedCompilerOutput: Boolean,
    excludeOutput: Boolean,
    entitySource: EntitySource,
    init: (JavaModuleSettingsEntityBuilder.() -> Unit)? = null,
  ): JavaModuleSettingsEntityBuilder {
    val builder = builder()
    builder.inheritedCompilerOutput = inheritedCompilerOutput
    builder.excludeOutput = excludeOutput
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    inheritedCompilerOutput: Boolean,
    excludeOutput: Boolean,
    entitySource: EntitySource,
    init: (JavaModuleSettingsEntity.Builder.() -> Unit)? = null,
  ): JavaModuleSettingsEntity.Builder {
    val builder = builder() as JavaModuleSettingsEntity.Builder
    builder.inheritedCompilerOutput = inheritedCompilerOutput
    builder.excludeOutput = excludeOutput
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyJavaModuleSettingsEntity(
  entity: JavaModuleSettingsEntity,
  modification: JavaModuleSettingsEntityBuilder.() -> Unit,
): JavaModuleSettingsEntity = modifyEntity(JavaModuleSettingsEntityBuilder::class.java, entity, modification)

var ModuleEntityBuilder.javaSettings: JavaModuleSettingsEntityBuilder?
  by WorkspaceEntity.extensionBuilder(JavaModuleSettingsEntity::class.java)

@JvmOverloads
@JvmName("createJavaModuleSettingsEntity")
fun JavaModuleSettingsEntity(
  inheritedCompilerOutput: Boolean,
  excludeOutput: Boolean,
  entitySource: EntitySource,
  init: (JavaModuleSettingsEntityBuilder.() -> Unit)? = null,
): JavaModuleSettingsEntityBuilder = JavaModuleSettingsEntityType(inheritedCompilerOutput, excludeOutput, entitySource, init)
