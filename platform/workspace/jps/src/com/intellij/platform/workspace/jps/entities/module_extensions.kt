// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ModuleExtensions")

package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls


/**
 * Describes additional data stored in [Module][com.intellij.openapi.module.Module] instance.
 */
@Internal
interface ModuleCustomImlDataEntity : WorkspaceEntity {
  val rootManagerTagCustomData: @NonNls String?

  /**
   * Specifies custom [module options][com.intellij.openapi.module.Module.getOptionValue].
   */
  val customModuleOptions: Map<@NonNls String, @NonNls String>

  @Parent
  val module: ModuleEntity

  //region generated code
  @Deprecated(message = "Use ModuleCustomImlDataEntityBuilder instead")
  interface Builder : ModuleCustomImlDataEntityBuilder {
    @Deprecated(message = "Use new API instead")
    fun getModule(): ModuleEntity.Builder = module as ModuleEntity.Builder

    @Deprecated(message = "Use new API instead")
    fun setModule(value: ModuleEntity.Builder) {
      module = value
    }
  }

  companion object : EntityType<ModuleCustomImlDataEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      customModuleOptions: Map<String, String>,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = ModuleCustomImlDataEntityType.compatibilityInvoke(customModuleOptions, entitySource, init)
  }
  //endregion

}

//region generated code
@Deprecated(message = "Use new API instead")
@Internal
fun MutableEntityStorage.modifyModuleCustomImlDataEntity(
  entity: ModuleCustomImlDataEntity,
  modification: ModuleCustomImlDataEntity.Builder.() -> Unit,
): ModuleCustomImlDataEntity {
  return modifyEntity(ModuleCustomImlDataEntity.Builder::class.java, entity, modification)
}
//endregion

@get:Internal
val ModuleEntity.customImlData: ModuleCustomImlDataEntity?
  by WorkspaceEntity.extension()

/**
 * Describes [explicit module group][com.intellij.openapi.module.ModuleManager.getModuleGroupPath]. Note that explicit module groups are
 * deprecated, so this entity should be used for compatibility with old code only.
 */
@Internal
interface ModuleGroupPathEntity : WorkspaceEntity {
  @Parent
  val module: ModuleEntity

  val path: List<@NonNls String>

  //region generated code
  @Deprecated(message = "Use ModuleGroupPathEntityBuilder instead")
  interface Builder : ModuleGroupPathEntityBuilder {
    @Deprecated(message = "Use new API instead")
    fun getModule(): ModuleEntity.Builder = module as ModuleEntity.Builder

    @Deprecated(message = "Use new API instead")
    fun setModule(value: ModuleEntity.Builder) {
      module = value
    }
  }

  companion object : EntityType<ModuleGroupPathEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      path: List<String>,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = ModuleGroupPathEntityType.compatibilityInvoke(path, entitySource, init)
  }
  //endregion

}

//region generated code
@Deprecated(message = "Use new API instead")
@Internal
fun MutableEntityStorage.modifyModuleGroupPathEntity(
  entity: ModuleGroupPathEntity,
  modification: ModuleGroupPathEntity.Builder.() -> Unit,
): ModuleGroupPathEntity {
  return modifyEntity(ModuleGroupPathEntity.Builder::class.java, entity, modification)
}
//endregion

@get:Internal
val ModuleEntity.groupPath: ModuleGroupPathEntity?
  by WorkspaceEntity.extension()

/**
 * Describes options for a [Module][com.intellij.openapi.module.Module] imported from some external project system (Maven, Gradle).
 */
@Internal
interface ExternalSystemModuleOptionsEntity : WorkspaceEntity {
  @Parent
  val module: ModuleEntity

  val externalSystem: String?
  val externalSystemModuleVersion: String?
  val linkedProjectPath: String?
  val linkedProjectId: String?
  val rootProjectPath: String?
  val externalSystemModuleGroup: String?
  val externalSystemModuleType: String?

  //region generated code
  @Deprecated(message = "Use ExternalSystemModuleOptionsEntityBuilder instead")
  interface Builder : ExternalSystemModuleOptionsEntityBuilder {
    @Deprecated(message = "Use new API instead")
    fun getModule(): ModuleEntity.Builder = module as ModuleEntity.Builder

    @Deprecated(message = "Use new API instead")
    fun setModule(value: ModuleEntity.Builder) {
      module = value
    }
  }

  companion object : EntityType<ExternalSystemModuleOptionsEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = ExternalSystemModuleOptionsEntityType.compatibilityInvoke(entitySource, init)
  }
  //endregion

}

//region generated code
@Deprecated(message = "Use new API instead")
@Internal
fun MutableEntityStorage.modifyExternalSystemModuleOptionsEntity(
  entity: ExternalSystemModuleOptionsEntity,
  modification: ExternalSystemModuleOptionsEntity.Builder.() -> Unit,
): ExternalSystemModuleOptionsEntity {
  return modifyEntity(ExternalSystemModuleOptionsEntity.Builder::class.java, entity, modification)
}
//endregion

@get:Internal
val ModuleEntity.exModuleOptions: ExternalSystemModuleOptionsEntity?
  by WorkspaceEntity.extension()

/**
 * Provides reference to [production module][com.intellij.openapi.roots.TestModuleProperties.getProductionModule].
 */
@Internal
interface TestModulePropertiesEntity : WorkspaceEntity {
  @Parent
  val module: ModuleEntity
  val productionModuleId: ModuleId

  //region generated code
  @Deprecated(message = "Use TestModulePropertiesEntityBuilder instead")
  interface Builder : TestModulePropertiesEntityBuilder {
    @Deprecated(message = "Use new API instead")
    fun getModule(): ModuleEntity.Builder = module as ModuleEntity.Builder

    @Deprecated(message = "Use new API instead")
    fun setModule(value: ModuleEntity.Builder) {
      module = value
    }
  }

  companion object : EntityType<TestModulePropertiesEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      productionModuleId: ModuleId,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = TestModulePropertiesEntityType.compatibilityInvoke(productionModuleId, entitySource, init)
  }
  //endregion
}

//region generated code
@Deprecated(message = "Use new API instead")
@Internal
fun MutableEntityStorage.modifyTestModulePropertiesEntity(
  entity: TestModulePropertiesEntity,
  modification: TestModulePropertiesEntity.Builder.() -> Unit,
): TestModulePropertiesEntity {
  return modifyEntity(TestModulePropertiesEntity.Builder::class.java, entity, modification)
}
//endregion

@get:Internal
val ModuleEntity.testProperties: TestModulePropertiesEntity?
  by WorkspaceEntity.extension()
