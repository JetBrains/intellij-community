// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ModuleExtensions")

package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls


/**
 * Describes additional data stored in [Module][com.intellij.openapi.module.Module] instance.
 */
@Internal
interface ModuleCustomImlDataEntity : WorkspaceEntity {
  val module: ModuleEntity

  val rootManagerTagCustomData: @NonNls String?

  /**
   * Specifies custom [module options][com.intellij.openapi.module.Module.getOptionValue].
   */
  val customModuleOptions: Map<@NonNls String, @NonNls String>

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ModuleCustomImlDataEntity> {
    override var entitySource: EntitySource
    var module: ModuleEntity.Builder
    var rootManagerTagCustomData: String?
    var customModuleOptions: Map<String, String>
  }

  companion object : EntityType<ModuleCustomImlDataEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      customModuleOptions: Map<String, String>,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.customModuleOptions = customModuleOptions
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(
  entity: ModuleCustomImlDataEntity,
  modification: ModuleCustomImlDataEntity.Builder.() -> Unit,
): ModuleCustomImlDataEntity {
  return modifyEntity(ModuleCustomImlDataEntity.Builder::class.java, entity, modification)
}
//endregion

@get:Internal
val ModuleEntity.customImlData: @Child ModuleCustomImlDataEntity?
  by WorkspaceEntity.extension()

/**
 * Describes [explicit module group][com.intellij.openapi.module.ModuleManager.getModuleGroupPath]. Note that explicit module groups are
 * deprecated, so this entity should be used for compatibility with old code only.
 */
@Internal
interface ModuleGroupPathEntity : WorkspaceEntity {
  val module: ModuleEntity

  val path: List<@NonNls String>

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ModuleGroupPathEntity> {
    override var entitySource: EntitySource
    var module: ModuleEntity.Builder
    var path: MutableList<String>
  }

  companion object : EntityType<ModuleGroupPathEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      path: List<String>,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.path = path.toMutableWorkspaceList()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(
  entity: ModuleGroupPathEntity,
  modification: ModuleGroupPathEntity.Builder.() -> Unit,
): ModuleGroupPathEntity {
  return modifyEntity(ModuleGroupPathEntity.Builder::class.java, entity, modification)
}
//endregion

@get:Internal
val ModuleEntity.groupPath: @Child ModuleGroupPathEntity?
  by WorkspaceEntity.extension()

/**
 * Describes options for a [Module][com.intellij.openapi.module.Module] imported from some external project system (Maven, Gradle).
 */
@Internal
interface ExternalSystemModuleOptionsEntity : WorkspaceEntity {
  val module: ModuleEntity

  val externalSystem: String?
  val externalSystemModuleVersion: String?
  val linkedProjectPath: String?
  val linkedProjectId: String?
  val rootProjectPath: String?
  val externalSystemModuleGroup: String?
  val externalSystemModuleType: String?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ExternalSystemModuleOptionsEntity> {
    override var entitySource: EntitySource
    var module: ModuleEntity.Builder
    var externalSystem: String?
    var externalSystemModuleVersion: String?
    var linkedProjectPath: String?
    var linkedProjectId: String?
    var rootProjectPath: String?
    var externalSystemModuleGroup: String?
    var externalSystemModuleType: String?
  }

  companion object : EntityType<ExternalSystemModuleOptionsEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(
  entity: ExternalSystemModuleOptionsEntity,
  modification: ExternalSystemModuleOptionsEntity.Builder.() -> Unit,
): ExternalSystemModuleOptionsEntity {
  return modifyEntity(ExternalSystemModuleOptionsEntity.Builder::class.java, entity, modification)
}
//endregion

@get:Internal
val ModuleEntity.exModuleOptions: @Child ExternalSystemModuleOptionsEntity?
  by WorkspaceEntity.extension()

/**
 * Provides reference to [production module][com.intellij.openapi.roots.TestModuleProperties.getProductionModule].
 */
@Internal
interface TestModulePropertiesEntity : WorkspaceEntity {
  val module: ModuleEntity
  val productionModuleId: ModuleId

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<TestModulePropertiesEntity> {
    override var entitySource: EntitySource
    var module: ModuleEntity.Builder
    var productionModuleId: ModuleId
  }

  companion object : EntityType<TestModulePropertiesEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      productionModuleId: ModuleId,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.productionModuleId = productionModuleId
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyEntity(
  entity: TestModulePropertiesEntity,
  modification: TestModulePropertiesEntity.Builder.() -> Unit,
): TestModulePropertiesEntity {
  return modifyEntity(TestModulePropertiesEntity.Builder::class.java, entity, modification)
}
//endregion

@get:Internal
val ModuleEntity.testProperties: @Child TestModulePropertiesEntity?
  by WorkspaceEntity.extension()
