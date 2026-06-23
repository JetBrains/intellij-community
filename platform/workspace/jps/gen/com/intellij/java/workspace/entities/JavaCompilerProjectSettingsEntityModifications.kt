// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JavaCompilerProjectSettingsEntityModifications")

package com.intellij.java.workspace.entities

import com.intellij.java.workspace.entities.impl.JavaCompilerProjectSettingsEntityImpl
import com.intellij.platform.workspace.jps.entities.ProjectSettingsEntityBuilder
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

@GeneratedCodeApiVersion(3)
interface JavaCompilerProjectSettingsEntityBuilder : WorkspaceEntityBuilder<JavaCompilerProjectSettingsEntity> {
  override var entitySource: EntitySource
  var projectSettings: ProjectSettingsEntityBuilder
  var additionalOptions: MutableList<String>
  var preferTargetJdkCompiler: Boolean
  var debuggingInfo: Boolean
  var generateNoWarnings: Boolean
  var deprecation: Boolean
  var maximumHeapSize: Int
}

internal object JavaCompilerProjectSettingsEntityType :
  EntityType<JavaCompilerProjectSettingsEntity, JavaCompilerProjectSettingsEntityBuilder>() {
  override val entityClass: Class<JavaCompilerProjectSettingsEntity> get() = JavaCompilerProjectSettingsEntity::class.java
  override val entityImplBuilderClass: Class<*> get() = JavaCompilerProjectSettingsEntityImpl.Builder::class.java
  operator fun invoke(
    additionalOptions: List<String>,
    entitySource: EntitySource,
    init: (JavaCompilerProjectSettingsEntityBuilder.() -> Unit)? = null,
  ): JavaCompilerProjectSettingsEntityBuilder {
    val builder = builder()
    builder.additionalOptions = additionalOptions.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyJavaCompilerProjectSettingsEntity(
  entity: JavaCompilerProjectSettingsEntity,
  modification: JavaCompilerProjectSettingsEntityBuilder.() -> Unit,
): JavaCompilerProjectSettingsEntity = modifyEntity(JavaCompilerProjectSettingsEntityBuilder::class.java, entity, modification)

var ProjectSettingsEntityBuilder.javaCompilerSettings: JavaCompilerProjectSettingsEntityBuilder?
  by WorkspaceEntity.extensionBuilder(JavaCompilerProjectSettingsEntity::class.java)


@JvmOverloads
@JvmName("createJavaCompilerProjectSettingsEntity")
fun JavaCompilerProjectSettingsEntity(
  additionalOptions: List<String>,
  entitySource: EntitySource,
  init: (JavaCompilerProjectSettingsEntityBuilder.() -> Unit)? = null,
): JavaCompilerProjectSettingsEntityBuilder = JavaCompilerProjectSettingsEntityType(additionalOptions, entitySource, init)
