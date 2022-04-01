package com.intellij.workspace.model.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityWithPersistentId
import com.intellij.workspaceModel.codegen.storage.url.VirtualFileUrl
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.IntellijWs.IntellijWs
import org.jetbrains.deft.Obj
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.*
import org.jetbrains.deft.IntellijWsTest.IntellijWsTest



interface ModuleEntity : WorkspaceEntityWithPersistentId {
    override val name: String

    val type: String?
    val dependencies: List<ModuleDependencyItem>

    val contentRoots: List<@Child ContentRootEntity>
    @Child val customImlData: ModuleCustomImlDataEntity?
    @Child val groupPath: ModuleGroupPathEntity?
    @Child val javaSettings: JavaModuleSettingsEntity?
    @Child val exModuleOptions: ExternalSystemModuleOptionsEntity?
    val facets: List<@Child FacetEntity>?

    override val persistentId: ModuleId
        get() = ModuleId(name)

    //region generated code
    //@formatter:off
    interface Builder: ModuleEntity, ObjBuilder<ModuleEntity> {
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
    
    companion object: ObjType<ModuleEntity, Builder>(IntellijWs, 41) {
        val nameField: Field<ModuleEntity, String> = Field(this, 0, "name", TString)
        val entitySource: Field<ModuleEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val type: Field<ModuleEntity, String?> = Field(this, 0, "type", TOptional(TString))
        val dependencies: Field<ModuleEntity, List<ModuleDependencyItem>> = Field(this, 0, "dependencies", TList(TBlob("ModuleDependencyItem")))
        val contentRoots: Field<ModuleEntity, List<ContentRootEntity>> = Field(this, 0, "contentRoots", TList(TRef("org.jetbrains.deft.IntellijWs", 17, child = true)))
        val customImlData: Field<ModuleEntity, ModuleCustomImlDataEntity?> = Field(this, 0, "customImlData", TOptional(TRef("org.jetbrains.deft.IntellijWs", 42, child = true)))
        val groupPath: Field<ModuleEntity, ModuleGroupPathEntity?> = Field(this, 0, "groupPath", TOptional(TRef("org.jetbrains.deft.IntellijWs", 43, child = true)))
        val javaSettings: Field<ModuleEntity, JavaModuleSettingsEntity?> = Field(this, 0, "javaSettings", TOptional(TRef("org.jetbrains.deft.IntellijWs", 44, child = true)))
        val exModuleOptions: Field<ModuleEntity, ExternalSystemModuleOptionsEntity?> = Field(this, 0, "exModuleOptions", TOptional(TRef("org.jetbrains.deft.IntellijWs", 45, child = true)))
        val facets: Field<ModuleEntity, List<FacetEntity>> = Field(this, 0, "facets", TList(TRef("org.jetbrains.deft.IntellijWs", 40, child = true)))
        val persistentId: Field<ModuleEntity, ModuleId> = Field(this, 0, "persistentId", TBlob("ModuleId"))
    }
    //@formatter:on
    //endregion
}

interface ModuleCustomImlDataEntity : WorkspaceEntity {
    val module: ModuleEntity

    val rootManagerTagCustomData: String?
    val customModuleOptions: Map<String, String>

    //region generated code
    //@formatter:off
    interface Builder: ModuleCustomImlDataEntity, ObjBuilder<ModuleCustomImlDataEntity> {
        override var module: ModuleEntity
        override var entitySource: EntitySource
        override var rootManagerTagCustomData: String?
        override var customModuleOptions: Map<String, String>
    }
    
    companion object: ObjType<ModuleCustomImlDataEntity, Builder>(IntellijWs, 42) {
        val moduleField: Field<ModuleCustomImlDataEntity, ModuleEntity> = Field(this, 0, "module", TRef("org.jetbrains.deft.IntellijWs", 41))
        val entitySource: Field<ModuleCustomImlDataEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val rootManagerTagCustomData: Field<ModuleCustomImlDataEntity, String?> = Field(this, 0, "rootManagerTagCustomData", TOptional(TString))
        val customModuleOptions: Field<ModuleCustomImlDataEntity, Map<String, String>> = Field(this, 0, "customModuleOptions", TMap(TString, TString))
    }
    //@formatter:on
    //endregion
}

interface ModuleGroupPathEntity : WorkspaceEntity {
    val module: ModuleEntity

    val path: List<String>

    //region generated code
    //@formatter:off
    interface Builder: ModuleGroupPathEntity, ObjBuilder<ModuleGroupPathEntity> {
        override var module: ModuleEntity
        override var entitySource: EntitySource
        override var path: List<String>
    }
    
    companion object: ObjType<ModuleGroupPathEntity, Builder>(IntellijWs, 43) {
        val moduleField: Field<ModuleGroupPathEntity, ModuleEntity> = Field(this, 0, "module", TRef("org.jetbrains.deft.IntellijWs", 41))
        val entitySource: Field<ModuleGroupPathEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val path: Field<ModuleGroupPathEntity, List<String>> = Field(this, 0, "path", TList(TString))
    }
    //@formatter:on
    //endregion
}

interface JavaModuleSettingsEntity: WorkspaceEntity {
    val module: ModuleEntity

    val inheritedCompilerOutput: Boolean
    val excludeOutput: Boolean
    val compilerOutput: VirtualFileUrl?
    val compilerOutputForTests: VirtualFileUrl?
    val languageLevelId: String?

    //region generated code
    //@formatter:off
    interface Builder: JavaModuleSettingsEntity, ObjBuilder<JavaModuleSettingsEntity> {
        override var module: ModuleEntity
        override var entitySource: EntitySource
        override var inheritedCompilerOutput: Boolean
        override var excludeOutput: Boolean
        override var compilerOutput: VirtualFileUrl?
        override var compilerOutputForTests: VirtualFileUrl?
        override var languageLevelId: String?
    }
    
    companion object: ObjType<JavaModuleSettingsEntity, Builder>(IntellijWs, 44) {
        val moduleField: Field<JavaModuleSettingsEntity, ModuleEntity> = Field(this, 0, "module", TRef("org.jetbrains.deft.IntellijWs", 41))
        val entitySource: Field<JavaModuleSettingsEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val inheritedCompilerOutput: Field<JavaModuleSettingsEntity, Boolean> = Field(this, 0, "inheritedCompilerOutput", TBoolean)
        val excludeOutput: Field<JavaModuleSettingsEntity, Boolean> = Field(this, 0, "excludeOutput", TBoolean)
        val compilerOutput: Field<JavaModuleSettingsEntity, VirtualFileUrl?> = Field(this, 0, "compilerOutput", TOptional(TBlob("VirtualFileUrl")))
        val compilerOutputForTests: Field<JavaModuleSettingsEntity, VirtualFileUrl?> = Field(this, 0, "compilerOutputForTests", TOptional(TBlob("VirtualFileUrl")))
        val languageLevelId: Field<JavaModuleSettingsEntity, String?> = Field(this, 0, "languageLevelId", TOptional(TString))
    }
    //@formatter:on
    //endregion
}

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
    interface Builder: ExternalSystemModuleOptionsEntity, ObjBuilder<ExternalSystemModuleOptionsEntity> {
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
    
    companion object: ObjType<ExternalSystemModuleOptionsEntity, Builder>(IntellijWs, 45) {
        val moduleField: Field<ExternalSystemModuleOptionsEntity, ModuleEntity> = Field(this, 0, "module", TRef("org.jetbrains.deft.IntellijWs", 41))
        val entitySource: Field<ExternalSystemModuleOptionsEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val externalSystem: Field<ExternalSystemModuleOptionsEntity, String?> = Field(this, 0, "externalSystem", TOptional(TString))
        val externalSystemModuleVersion: Field<ExternalSystemModuleOptionsEntity, String?> = Field(this, 0, "externalSystemModuleVersion", TOptional(TString))
        val linkedProjectPath: Field<ExternalSystemModuleOptionsEntity, String?> = Field(this, 0, "linkedProjectPath", TOptional(TString))
        val linkedProjectId: Field<ExternalSystemModuleOptionsEntity, String?> = Field(this, 0, "linkedProjectId", TOptional(TString))
        val rootProjectPath: Field<ExternalSystemModuleOptionsEntity, String?> = Field(this, 0, "rootProjectPath", TOptional(TString))
        val externalSystemModuleGroup: Field<ExternalSystemModuleOptionsEntity, String?> = Field(this, 0, "externalSystemModuleGroup", TOptional(TString))
        val externalSystemModuleType: Field<ExternalSystemModuleOptionsEntity, String?> = Field(this, 0, "externalSystemModuleType", TOptional(TString))
    }
    //@formatter:on
    //endregion
}