// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.bridgeEntities

import com.intellij.openapi.util.NlsSafe
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import org.jetbrains.annotations.NonNls
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

interface ModuleEntity : WorkspaceEntityWithSymbolicId {
    val name: @NlsSafe String

    val type: @NonNls String?
    val dependencies: List<ModuleDependencyItem>

    val contentRoots: List<@Child ContentRootEntity>
    @Child val customImlData: ModuleCustomImlDataEntity?
    @Child val groupPath: ModuleGroupPathEntity?
    @Child val javaSettings: JavaModuleSettingsEntity?
    @Child val exModuleOptions: ExternalSystemModuleOptionsEntity?
    @Child val testProperties: TestModulePropertiesEntity?
    val facets: List<@Child FacetEntity>

    override val symbolicId: ModuleId
        get() = ModuleId(name)

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : ModuleEntity, WorkspaceEntity.Builder<ModuleEntity>, ObjBuilder<ModuleEntity> {
    override var entitySource: EntitySource
    override var name: String
    override var type: String?
    override var dependencies: MutableList<ModuleDependencyItem>
    override var contentRoots: List<ContentRootEntity>
    override var customImlData: ModuleCustomImlDataEntity?
    override var groupPath: ModuleGroupPathEntity?
    override var javaSettings: JavaModuleSettingsEntity?
    override var exModuleOptions: ExternalSystemModuleOptionsEntity?
    override var testProperties: TestModulePropertiesEntity?
    override var facets: List<FacetEntity>
  }

  companion object : Type<ModuleEntity, Builder>() {
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
  @GeneratedCodeApiVersion(1)
  interface Builder : ModuleCustomImlDataEntity, WorkspaceEntity.Builder<ModuleCustomImlDataEntity>, ObjBuilder<ModuleCustomImlDataEntity> {
    override var entitySource: EntitySource
    override var module: ModuleEntity
    override var rootManagerTagCustomData: String?
    override var customModuleOptions: Map<String, String>
  }

  companion object : Type<ModuleCustomImlDataEntity, Builder>() {
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
  @GeneratedCodeApiVersion(1)
  interface Builder : ModuleGroupPathEntity, WorkspaceEntity.Builder<ModuleGroupPathEntity>, ObjBuilder<ModuleGroupPathEntity> {
    override var entitySource: EntitySource
    override var module: ModuleEntity
    override var path: MutableList<String>
  }

  companion object : Type<ModuleGroupPathEntity, Builder>() {
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

interface ExternalSystemModuleOptionsEntity: WorkspaceEntity {
    val module: ModuleEntity

    val externalSystem: String?
    val externalSystemModuleVersion: String?
    val linkedProjectPath: String?
    val linkedProjectId: String?
    val rootProjectPath: String?
    val externalSystemModuleGroup: String?
    val externalSystemModuleType: String?

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : ExternalSystemModuleOptionsEntity, WorkspaceEntity.Builder<ExternalSystemModuleOptionsEntity>, ObjBuilder<ExternalSystemModuleOptionsEntity> {
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

  companion object : Type<ExternalSystemModuleOptionsEntity, Builder>() {
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

interface TestModulePropertiesEntity: WorkspaceEntity {
  val module: ModuleEntity
  val productionModuleId: ModuleId

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : TestModulePropertiesEntity, WorkspaceEntity.Builder<TestModulePropertiesEntity>, ObjBuilder<TestModulePropertiesEntity> {
    override var entitySource: EntitySource
    override var module: ModuleEntity
    override var productionModuleId: ModuleId
  }

  companion object : Type<TestModulePropertiesEntity, Builder>() {
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
