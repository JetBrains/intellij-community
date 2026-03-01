// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JavaSourceRootPropertiesEntityModifications")

package com.intellij.java.workspace.entities

import com.intellij.platform.workspace.jps.entities.SourceRootEntityBuilder
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder

@GeneratedCodeApiVersion(3)
interface JavaSourceRootPropertiesEntityBuilder : WorkspaceEntityBuilder<JavaSourceRootPropertiesEntity> {
  override var entitySource: EntitySource
  var sourceRoot: SourceRootEntityBuilder
  var generated: Boolean
  var packagePrefix: String
}

internal object JavaSourceRootPropertiesEntityType : EntityType<JavaSourceRootPropertiesEntity, JavaSourceRootPropertiesEntityBuilder>() {
  override val entityClass: Class<JavaSourceRootPropertiesEntity> get() = JavaSourceRootPropertiesEntity::class.java
  operator fun invoke(
    generated: Boolean,
    packagePrefix: String,
    entitySource: EntitySource,
    init: (JavaSourceRootPropertiesEntityBuilder.() -> Unit)? = null,
  ): JavaSourceRootPropertiesEntityBuilder {
    val builder = builder()
    builder.generated = generated
    builder.packagePrefix = packagePrefix
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    generated: Boolean,
    packagePrefix: String,
    entitySource: EntitySource,
    init: (JavaSourceRootPropertiesEntity.Builder.() -> Unit)? = null,
  ): JavaSourceRootPropertiesEntity.Builder {
    val builder = builder() as JavaSourceRootPropertiesEntity.Builder
    builder.generated = generated
    builder.packagePrefix = packagePrefix
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyJavaSourceRootPropertiesEntity(
  entity: JavaSourceRootPropertiesEntity,
  modification: JavaSourceRootPropertiesEntityBuilder.() -> Unit,
): JavaSourceRootPropertiesEntity = modifyEntity(JavaSourceRootPropertiesEntityBuilder::class.java, entity, modification)

var SourceRootEntityBuilder.javaSourceRoots: List<JavaSourceRootPropertiesEntityBuilder>
  by WorkspaceEntity.extensionBuilder(JavaSourceRootPropertiesEntity::class.java)

@JvmOverloads
@JvmName("createJavaSourceRootPropertiesEntity")
fun JavaSourceRootPropertiesEntity(
  generated: Boolean,
  packagePrefix: String,
  entitySource: EntitySource,
  init: (JavaSourceRootPropertiesEntityBuilder.() -> Unit)? = null,
): JavaSourceRootPropertiesEntityBuilder = JavaSourceRootPropertiesEntityType(generated, packagePrefix, entitySource, init)
