// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspaceModel.storage.bridgeEntities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspaceModel.storage.*
import com.intellij.platform.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.annotations.NonNls

interface JavaSourceRootPropertiesEntity : WorkspaceEntity {
  val sourceRoot: SourceRootEntity

  val generated: Boolean
  val packagePrefix: @NlsSafe String

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : JavaSourceRootPropertiesEntity, WorkspaceEntity.Builder<JavaSourceRootPropertiesEntity>, ObjBuilder<JavaSourceRootPropertiesEntity> {
    override var entitySource: EntitySource
    override var sourceRoot: SourceRootEntity
    override var generated: Boolean
    override var packagePrefix: String
  }

  companion object : Type<JavaSourceRootPropertiesEntity, Builder>() {
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
//endregion

interface JavaResourceRootPropertiesEntity: WorkspaceEntity {
  val sourceRoot: SourceRootEntity

  val generated: Boolean
  val relativeOutputPath: @NlsSafe String

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : JavaResourceRootPropertiesEntity, WorkspaceEntity.Builder<JavaResourceRootPropertiesEntity>, ObjBuilder<JavaResourceRootPropertiesEntity> {
    override var entitySource: EntitySource
    override var sourceRoot: SourceRootEntity
    override var generated: Boolean
    override var relativeOutputPath: String
  }

  companion object : Type<JavaResourceRootPropertiesEntity, Builder>() {
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
//endregion

interface JavaModuleSettingsEntity: WorkspaceEntity {
  val module: ModuleEntity

  val inheritedCompilerOutput: Boolean
  val excludeOutput: Boolean
  val compilerOutput: VirtualFileUrl?
  val compilerOutputForTests: VirtualFileUrl?
  val languageLevelId: @NonNls String?

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : JavaModuleSettingsEntity, WorkspaceEntity.Builder<JavaModuleSettingsEntity>, ObjBuilder<JavaModuleSettingsEntity> {
    override var entitySource: EntitySource
    override var module: ModuleEntity
    override var inheritedCompilerOutput: Boolean
    override var excludeOutput: Boolean
    override var compilerOutput: VirtualFileUrl?
    override var compilerOutputForTests: VirtualFileUrl?
    override var languageLevelId: String?
  }

  companion object : Type<JavaModuleSettingsEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(inheritedCompilerOutput: Boolean,
                        excludeOutput: Boolean,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): JavaModuleSettingsEntity {
      val builder = builder()
      builder.inheritedCompilerOutput = inheritedCompilerOutput
      builder.excludeOutput = excludeOutput
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: JavaModuleSettingsEntity,
                                      modification: JavaModuleSettingsEntity.Builder.() -> Unit) = modifyEntity(
  JavaModuleSettingsEntity.Builder::class.java, entity, modification)
//endregion
