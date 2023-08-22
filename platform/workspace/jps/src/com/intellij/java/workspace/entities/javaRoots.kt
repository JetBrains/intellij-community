// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.workspace.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.jps.entities.SourceRootEntity

interface JavaSourceRootPropertiesEntity : WorkspaceEntity {
  val sourceRoot: SourceRootEntity

  val generated: Boolean
  val packagePrefix: @NlsSafe String

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : JavaSourceRootPropertiesEntity, WorkspaceEntity.Builder<JavaSourceRootPropertiesEntity> {
    override var entitySource: EntitySource
    override var sourceRoot: SourceRootEntity
    override var generated: Boolean
    override var packagePrefix: String
  }

  companion object : EntityType<JavaSourceRootPropertiesEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(generated: Boolean,
                        packagePrefix: String,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): JavaSourceRootPropertiesEntity {
      val builder = builder()
      builder.generated = generated
      builder.packagePrefix = packagePrefix
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: JavaSourceRootPropertiesEntity,
                                      modification: JavaSourceRootPropertiesEntity.Builder.() -> Unit) = modifyEntity(
  JavaSourceRootPropertiesEntity.Builder::class.java, entity, modification)

var SourceRootEntity.Builder.javaSourceRoots: @Child List<JavaSourceRootPropertiesEntity>
  by WorkspaceEntity.extension()
//endregion

val SourceRootEntity.javaSourceRoots: List<@Child JavaSourceRootPropertiesEntity>
  by WorkspaceEntity.extension()

interface JavaResourceRootPropertiesEntity: WorkspaceEntity {
  val sourceRoot: SourceRootEntity

  val generated: Boolean
  val relativeOutputPath: @NlsSafe String

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : JavaResourceRootPropertiesEntity, WorkspaceEntity.Builder<JavaResourceRootPropertiesEntity> {
    override var entitySource: EntitySource
    override var sourceRoot: SourceRootEntity
    override var generated: Boolean
    override var relativeOutputPath: String
  }

  companion object : EntityType<JavaResourceRootPropertiesEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(generated: Boolean,
                        relativeOutputPath: String,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): JavaResourceRootPropertiesEntity {
      val builder = builder()
      builder.generated = generated
      builder.relativeOutputPath = relativeOutputPath
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: JavaResourceRootPropertiesEntity,
                                      modification: JavaResourceRootPropertiesEntity.Builder.() -> Unit) = modifyEntity(
  JavaResourceRootPropertiesEntity.Builder::class.java, entity, modification)

var SourceRootEntity.Builder.javaResourceRoots: @Child List<JavaResourceRootPropertiesEntity>
  by WorkspaceEntity.extension()
//endregion

val SourceRootEntity.javaResourceRoots: List<@Child JavaResourceRootPropertiesEntity>
  by WorkspaceEntity.extension()

fun SourceRootEntity.asJavaSourceRoot(): JavaSourceRootPropertiesEntity? = javaSourceRoots.firstOrNull()
fun SourceRootEntity.asJavaResourceRoot(): JavaResourceRootPropertiesEntity? = javaResourceRoots.firstOrNull()
