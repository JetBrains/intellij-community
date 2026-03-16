// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JavaResourceRootPropertiesEntityModifications")

package com.intellij.java.workspace.entities

import com.intellij.platform.workspace.jps.entities.SourceRootEntityBuilder
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder

@GeneratedCodeApiVersion(3)
interface JavaResourceRootPropertiesEntityBuilder : WorkspaceEntityBuilder<JavaResourceRootPropertiesEntity> {
  override var entitySource: EntitySource
  var sourceRoot: SourceRootEntityBuilder
  var generated: Boolean
  var relativeOutputPath: String
}

internal object JavaResourceRootPropertiesEntityType :
  EntityType<JavaResourceRootPropertiesEntity, JavaResourceRootPropertiesEntityBuilder>() {
  override val entityClass: Class<JavaResourceRootPropertiesEntity> get() = JavaResourceRootPropertiesEntity::class.java
  operator fun invoke(
    generated: Boolean,
    relativeOutputPath: String,
    entitySource: EntitySource,
    init: (JavaResourceRootPropertiesEntityBuilder.() -> Unit)? = null,
  ): JavaResourceRootPropertiesEntityBuilder {
    val builder = builder()
    builder.generated = generated
    builder.relativeOutputPath = relativeOutputPath
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    generated: Boolean,
    relativeOutputPath: String,
    entitySource: EntitySource,
    init: (JavaResourceRootPropertiesEntity.Builder.() -> Unit)? = null,
  ): JavaResourceRootPropertiesEntity.Builder {
    val builder = builder() as JavaResourceRootPropertiesEntity.Builder
    builder.generated = generated
    builder.relativeOutputPath = relativeOutputPath
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyJavaResourceRootPropertiesEntity(
  entity: JavaResourceRootPropertiesEntity,
  modification: JavaResourceRootPropertiesEntityBuilder.() -> Unit,
): JavaResourceRootPropertiesEntity = modifyEntity(JavaResourceRootPropertiesEntityBuilder::class.java, entity, modification)

var SourceRootEntityBuilder.javaResourceRoots: List<JavaResourceRootPropertiesEntityBuilder>
  by WorkspaceEntity.extensionBuilder(JavaResourceRootPropertiesEntity::class.java)

@JvmOverloads
@JvmName("createJavaResourceRootPropertiesEntity")
fun JavaResourceRootPropertiesEntity(
  generated: Boolean,
  relativeOutputPath: String,
  entitySource: EntitySource,
  init: (JavaResourceRootPropertiesEntityBuilder.() -> Unit)? = null,
): JavaResourceRootPropertiesEntityBuilder = JavaResourceRootPropertiesEntityType(generated, relativeOutputPath, entitySource, init)
