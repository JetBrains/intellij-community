// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.workspace.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.jps.entities.ModifiableSourceRootEntity
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableJavaSourceRootPropertiesEntity : ModifiableWorkspaceEntity<JavaSourceRootPropertiesEntity> {
  override var entitySource: EntitySource
  var sourceRoot: ModifiableSourceRootEntity
  var generated: Boolean
  var packagePrefix: String
}

internal object JavaSourceRootPropertiesEntityType : EntityType<JavaSourceRootPropertiesEntity, ModifiableJavaSourceRootPropertiesEntity>() {
  override val entityClass: Class<JavaSourceRootPropertiesEntity> get() = JavaSourceRootPropertiesEntity::class.java
  operator fun invoke(
    generated: Boolean,
    packagePrefix: String,
    entitySource: EntitySource,
    init: (ModifiableJavaSourceRootPropertiesEntity.() -> Unit)? = null,
  ): ModifiableJavaSourceRootPropertiesEntity {
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
  modification: ModifiableJavaSourceRootPropertiesEntity.() -> Unit,
): JavaSourceRootPropertiesEntity = modifyEntity(ModifiableJavaSourceRootPropertiesEntity::class.java, entity, modification)

var ModifiableSourceRootEntity.javaSourceRoots: List<ModifiableJavaSourceRootPropertiesEntity>
  by WorkspaceEntity.extensionBuilder(JavaSourceRootPropertiesEntity::class.java)

@JvmOverloads
@JvmName("createJavaSourceRootPropertiesEntity")
fun JavaSourceRootPropertiesEntity(
  generated: Boolean,
  packagePrefix: String,
  entitySource: EntitySource,
  init: (ModifiableJavaSourceRootPropertiesEntity.() -> Unit)? = null,
): ModifiableJavaSourceRootPropertiesEntity = JavaSourceRootPropertiesEntityType(generated, packagePrefix, entitySource, init)
