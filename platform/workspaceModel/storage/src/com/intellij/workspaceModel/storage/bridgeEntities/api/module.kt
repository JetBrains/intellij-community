// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.bridgeEntities.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity



interface ModuleEntity : WorkspaceEntityWithPersistentId {
    val name: String

    val type: String?
    val dependencies: List<ModuleDependencyItem>

    val contentRoots: List<@Child ContentRootEntity>
    @Child val customImlData: ModuleCustomImlDataEntity?
    @Child val groupPath: ModuleGroupPathEntity?
    @Child val javaSettings: JavaModuleSettingsEntity?
    @Child val exModuleOptions: ExternalSystemModuleOptionsEntity?
    val facets: List<@Child FacetEntity>

    override val persistentId: ModuleId
        get() = ModuleId(name)


    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(1)
    interface Builder: ModuleEntity, ModifiableWorkspaceEntity<ModuleEntity>, ObjBuilder<ModuleEntity> {
        override var name: String
        override var entitySource: EntitySource
        override var type: String?
        override var dependencies: List<ModuleDependencyItem>
        override var contentRoots: List<ContentRootEntity>
        override var customImlData: ModuleCustomImlDataEntity?
        override var groupPath: ModuleGroupPathEntity?
        override var javaSettings: JavaModuleSettingsEntity?
        override var exModuleOptions: ExternalSystemModuleOptionsEntity?
        override var facets: List<FacetEntity>
    }
    
    companion object: Type<ModuleEntity, Builder>() {
        operator fun invoke(name: String, entitySource: EntitySource, dependencies: List<ModuleDependencyItem>, init: (Builder.() -> Unit)? = null): ModuleEntity {
            val builder = builder()
            builder.name = name
            builder.entitySource = entitySource
            builder.dependencies = dependencies
            init?.invoke(builder)
            return builder
        }
    }
    //@formatter:on
    //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: ModuleEntity, modification: ModuleEntity.Builder.() -> Unit) = modifyEntity(ModuleEntity.Builder::class.java, entity, modification)
var ModuleEntity.Builder.facetOrder: @Child FacetsOrderEntity?
    by WorkspaceEntity.extension()

//endregion

interface ModuleCustomImlDataEntity : WorkspaceEntity {
    val module: ModuleEntity

    val rootManagerTagCustomData: String?
    val customModuleOptions: Map<String, String>


    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(1)
    interface Builder: ModuleCustomImlDataEntity, ModifiableWorkspaceEntity<ModuleCustomImlDataEntity>, ObjBuilder<ModuleCustomImlDataEntity> {
        override var module: ModuleEntity
        override var entitySource: EntitySource
        override var rootManagerTagCustomData: String?
        override var customModuleOptions: Map<String, String>
    }
    
    companion object: Type<ModuleCustomImlDataEntity, Builder>() {
        operator fun invoke(entitySource: EntitySource, customModuleOptions: Map<String, String>, init: (Builder.() -> Unit)? = null): ModuleCustomImlDataEntity {
            val builder = builder()
            builder.entitySource = entitySource
            builder.customModuleOptions = customModuleOptions
            init?.invoke(builder)
            return builder
        }
    }
    //@formatter:on
    //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: ModuleCustomImlDataEntity, modification: ModuleCustomImlDataEntity.Builder.() -> Unit) = modifyEntity(ModuleCustomImlDataEntity.Builder::class.java, entity, modification)
//endregion

interface ModuleGroupPathEntity : WorkspaceEntity {
    val module: ModuleEntity

    val path: List<String>


    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(1)
    interface Builder: ModuleGroupPathEntity, ModifiableWorkspaceEntity<ModuleGroupPathEntity>, ObjBuilder<ModuleGroupPathEntity> {
        override var module: ModuleEntity
        override var entitySource: EntitySource
        override var path: List<String>
    }
    
    companion object: Type<ModuleGroupPathEntity, Builder>() {
        operator fun invoke(entitySource: EntitySource, path: List<String>, init: (Builder.() -> Unit)? = null): ModuleGroupPathEntity {
            val builder = builder()
            builder.entitySource = entitySource
            builder.path = path
            init?.invoke(builder)
            return builder
        }
    }
    //@formatter:on
    //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: ModuleGroupPathEntity, modification: ModuleGroupPathEntity.Builder.() -> Unit) = modifyEntity(ModuleGroupPathEntity.Builder::class.java, entity, modification)
//endregion

interface JavaModuleSettingsEntity: WorkspaceEntity {
    val module: ModuleEntity

    val inheritedCompilerOutput: Boolean
    val excludeOutput: Boolean
    val compilerOutput: VirtualFileUrl?
    val compilerOutputForTests: VirtualFileUrl?
    val languageLevelId: String?


    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(1)
    interface Builder: JavaModuleSettingsEntity, ModifiableWorkspaceEntity<JavaModuleSettingsEntity>, ObjBuilder<JavaModuleSettingsEntity> {
        override var module: ModuleEntity
        override var entitySource: EntitySource
        override var inheritedCompilerOutput: Boolean
        override var excludeOutput: Boolean
        override var compilerOutput: VirtualFileUrl?
        override var compilerOutputForTests: VirtualFileUrl?
        override var languageLevelId: String?
    }
    
    companion object: Type<JavaModuleSettingsEntity, Builder>() {
        operator fun invoke(entitySource: EntitySource, inheritedCompilerOutput: Boolean, excludeOutput: Boolean, init: (Builder.() -> Unit)? = null): JavaModuleSettingsEntity {
            val builder = builder()
            builder.entitySource = entitySource
            builder.inheritedCompilerOutput = inheritedCompilerOutput
            builder.excludeOutput = excludeOutput
            init?.invoke(builder)
            return builder
        }
    }
    //@formatter:on
    //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: JavaModuleSettingsEntity, modification: JavaModuleSettingsEntity.Builder.() -> Unit) = modifyEntity(JavaModuleSettingsEntity.Builder::class.java, entity, modification)
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
    //@formatter:off
    @GeneratedCodeApiVersion(1)
    interface Builder: ExternalSystemModuleOptionsEntity, ModifiableWorkspaceEntity<ExternalSystemModuleOptionsEntity>, ObjBuilder<ExternalSystemModuleOptionsEntity> {
        override var module: ModuleEntity
        override var entitySource: EntitySource
        override var externalSystem: String?
        override var externalSystemModuleVersion: String?
        override var linkedProjectPath: String?
        override var linkedProjectId: String?
        override var rootProjectPath: String?
        override var externalSystemModuleGroup: String?
        override var externalSystemModuleType: String?
    }
    
    companion object: Type<ExternalSystemModuleOptionsEntity, Builder>() {
        operator fun invoke(entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ExternalSystemModuleOptionsEntity {
            val builder = builder()
            builder.entitySource = entitySource
            init?.invoke(builder)
            return builder
        }
    }
    //@formatter:on
    //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: ExternalSystemModuleOptionsEntity, modification: ExternalSystemModuleOptionsEntity.Builder.() -> Unit) = modifyEntity(ExternalSystemModuleOptionsEntity.Builder::class.java, entity, modification)
//endregion