// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import org.jetbrains.annotations.NonNls

interface ModuleEntity : WorkspaceEntityWithSymbolicId {
  val name: @NlsSafe String

  val type: @NonNls String?
  val dependencies: List<ModuleDependencyItem>

  val contentRoots: List<@Child ContentRootEntity>
  @Child
  val customImlData: ModuleCustomImlDataEntity?
  @Child
  val groupPath: ModuleGroupPathEntity?
  @Child
  val exModuleOptions: ExternalSystemModuleOptionsEntity?
  @Child
  val testProperties: TestModulePropertiesEntity?
  val facets: List<@Child FacetEntity>

  override val symbolicId: ModuleId
    get() = ModuleId(name)

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ModuleEntity, WorkspaceEntity.Builder<ModuleEntity> {
    override var entitySource: EntitySource
    override var name: String
    override var type: String?
    override var dependencies: MutableList<ModuleDependencyItem>
    override var contentRoots: List<ContentRootEntity>
    override var customImlData: ModuleCustomImlDataEntity?
    override var groupPath: ModuleGroupPathEntity?
    override var exModuleOptions: ExternalSystemModuleOptionsEntity?
    override var testProperties: TestModulePropertiesEntity?
    override var facets: List<FacetEntity>
  }

  companion object : EntityType<ModuleEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(name: String,
                        dependencies: List<ModuleDependencyItem>,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): ModuleEntity {
      val builder = builder()
      builder.name = name
      builder.dependencies = dependencies.toMutableWorkspaceList()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: ModuleEntity, modification: ModuleEntity.Builder.() -> Unit) = modifyEntity(
  ModuleEntity.Builder::class.java, entity, modification)

var ModuleEntity.Builder.facetOrder: @Child FacetsOrderEntity?
  by WorkspaceEntity.extension()
var ModuleEntity.Builder.sourceRoots: List<SourceRootEntity>
  by WorkspaceEntity.extension()
//endregion

interface ModuleCustomImlDataEntity : WorkspaceEntity {
  val module: ModuleEntity

  val rootManagerTagCustomData: @NonNls String?
  val customModuleOptions: Map<@NonNls String, @NonNls String>

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ModuleCustomImlDataEntity, WorkspaceEntity.Builder<ModuleCustomImlDataEntity> {
    override var entitySource: EntitySource
    override var module: ModuleEntity
    override var rootManagerTagCustomData: String?
    override var customModuleOptions: Map<String, String>
  }

  companion object : EntityType<ModuleCustomImlDataEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(customModuleOptions: Map<String, String>,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): ModuleCustomImlDataEntity {
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
fun MutableEntityStorage.modifyEntity(entity: ModuleCustomImlDataEntity,
                                      modification: ModuleCustomImlDataEntity.Builder.() -> Unit) = modifyEntity(
  ModuleCustomImlDataEntity.Builder::class.java, entity, modification)
//endregion

interface ModuleGroupPathEntity : WorkspaceEntity {
  val module: ModuleEntity

  val path: List<@NonNls String>

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ModuleGroupPathEntity, WorkspaceEntity.Builder<ModuleGroupPathEntity> {
    override var entitySource: EntitySource
    override var module: ModuleEntity
    override var path: MutableList<String>
  }

  companion object : EntityType<ModuleGroupPathEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(path: List<String>, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ModuleGroupPathEntity {
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
fun MutableEntityStorage.modifyEntity(entity: ModuleGroupPathEntity, modification: ModuleGroupPathEntity.Builder.() -> Unit) = modifyEntity(
  ModuleGroupPathEntity.Builder::class.java, entity, modification)
//endregion


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
  @GeneratedCodeApiVersion(2)
  interface Builder : ExternalSystemModuleOptionsEntity, WorkspaceEntity.Builder<ExternalSystemModuleOptionsEntity> {
    override var entitySource: EntitySource
    override var module: ModuleEntity
    override var externalSystem: String?
    override var externalSystemModuleVersion: String?
    override var linkedProjectPath: String?
    override var linkedProjectId: String?
    override var rootProjectPath: String?
    override var externalSystemModuleGroup: String?
    override var externalSystemModuleType: String?
  }

  companion object : EntityType<ExternalSystemModuleOptionsEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ExternalSystemModuleOptionsEntity {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: ExternalSystemModuleOptionsEntity,
                                      modification: ExternalSystemModuleOptionsEntity.Builder.() -> Unit) = modifyEntity(
  ExternalSystemModuleOptionsEntity.Builder::class.java, entity, modification)
//endregion

interface TestModulePropertiesEntity : WorkspaceEntity {
  val module: ModuleEntity
  val productionModuleId: ModuleId

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : TestModulePropertiesEntity, WorkspaceEntity.Builder<TestModulePropertiesEntity> {
    override var entitySource: EntitySource
    override var module: ModuleEntity
    override var productionModuleId: ModuleId
  }

  companion object : EntityType<TestModulePropertiesEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(productionModuleId: ModuleId,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): TestModulePropertiesEntity {
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
fun MutableEntityStorage.modifyEntity(entity: TestModulePropertiesEntity,
                                      modification: TestModulePropertiesEntity.Builder.() -> Unit) = modifyEntity(
  TestModulePropertiesEntity.Builder::class.java, entity, modification)
//endregion
