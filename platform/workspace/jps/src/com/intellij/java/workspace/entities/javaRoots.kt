// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.workspace.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.jps.entities.SourceRootEntityBuilder
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent

interface JavaSourceRootPropertiesEntity : WorkspaceEntity {
  @Parent
  val sourceRoot: SourceRootEntity

  val generated: Boolean
  val packagePrefix: @NlsSafe String

  //region generated code
  @Deprecated(message = "Use JavaSourceRootPropertiesEntityBuilder instead")
  interface Builder : JavaSourceRootPropertiesEntityBuilder {
    @Deprecated(message = "Use new API instead")
    fun getSourceRoot(): SourceRootEntity.Builder = sourceRoot as SourceRootEntity.Builder

    @Deprecated(message = "Use new API instead")
    fun setSourceRoot(value: SourceRootEntity.Builder) {
      sourceRoot = value
    }
  }

  companion object : EntityType<JavaSourceRootPropertiesEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      generated: Boolean,
      packagePrefix: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = JavaSourceRootPropertiesEntityType.compatibilityInvoke(generated, packagePrefix, entitySource, init)
  }
  //endregion

}

//region generated code
@Deprecated(message = "Use new API instead")
fun MutableEntityStorage.modifyJavaSourceRootPropertiesEntity(
  entity: JavaSourceRootPropertiesEntity,
  modification: JavaSourceRootPropertiesEntity.Builder.() -> Unit,
): JavaSourceRootPropertiesEntity {
  return modifyEntity(JavaSourceRootPropertiesEntity.Builder::class.java, entity, modification)
}

@Deprecated(message = "Use new API instead")
var SourceRootEntity.Builder.javaSourceRoots: List<JavaSourceRootPropertiesEntity.Builder>
  get() = (this as SourceRootEntityBuilder).javaSourceRoots as List<JavaSourceRootPropertiesEntity.Builder>
  set(value) {
    (this as SourceRootEntityBuilder).javaSourceRoots = value
  }
//endregion

val SourceRootEntity.javaSourceRoots: List<JavaSourceRootPropertiesEntity>
  by WorkspaceEntity.extension()

interface JavaResourceRootPropertiesEntity: WorkspaceEntity {
  @Parent
  val sourceRoot: SourceRootEntity

  val generated: Boolean
  val relativeOutputPath: @NlsSafe String

  //region generated code
  @Deprecated(message = "Use JavaResourceRootPropertiesEntityBuilder instead")
  interface Builder : JavaResourceRootPropertiesEntityBuilder {
    @Deprecated(message = "Use new API instead")
    fun getSourceRoot(): SourceRootEntity.Builder = sourceRoot as SourceRootEntity.Builder

    @Deprecated(message = "Use new API instead")
    fun setSourceRoot(value: SourceRootEntity.Builder) {
      sourceRoot = value
    }
  }

  companion object : EntityType<JavaResourceRootPropertiesEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      generated: Boolean,
      relativeOutputPath: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = JavaResourceRootPropertiesEntityType.compatibilityInvoke(generated, relativeOutputPath, entitySource, init)
  }
  //endregion

}

//region generated code
@Deprecated(message = "Use new API instead")
fun MutableEntityStorage.modifyJavaResourceRootPropertiesEntity(
  entity: JavaResourceRootPropertiesEntity,
  modification: JavaResourceRootPropertiesEntity.Builder.() -> Unit,
): JavaResourceRootPropertiesEntity {
  return modifyEntity(JavaResourceRootPropertiesEntity.Builder::class.java, entity, modification)
}

@Deprecated(message = "Use new API instead")
var SourceRootEntity.Builder.javaResourceRoots: List<JavaResourceRootPropertiesEntity.Builder>
  get() = (this as SourceRootEntityBuilder).javaResourceRoots as List<JavaResourceRootPropertiesEntity.Builder>
  set(value) {
    (this as SourceRootEntityBuilder).javaResourceRoots = value
  }
//endregion

val SourceRootEntity.javaResourceRoots: List<JavaResourceRootPropertiesEntity>
  by WorkspaceEntity.extension()

fun SourceRootEntity.asJavaSourceRoot(): JavaSourceRootPropertiesEntity? = javaSourceRoots.firstOrNull()
fun SourceRootEntity.Builder.asJavaSourceRoot(): JavaSourceRootPropertiesEntity.Builder? = javaSourceRoots.firstOrNull()
fun SourceRootEntity.asJavaResourceRoot(): JavaResourceRootPropertiesEntity? = javaResourceRoots.firstOrNull()
fun SourceRootEntityBuilder.asJavaSourceRoot(): JavaSourceRootPropertiesEntityBuilder? = javaSourceRoots.firstOrNull()
fun SourceRootEntityBuilder.asJavaResourceRoot(): JavaResourceRootPropertiesEntityBuilder? = javaResourceRoots.firstOrNull()
