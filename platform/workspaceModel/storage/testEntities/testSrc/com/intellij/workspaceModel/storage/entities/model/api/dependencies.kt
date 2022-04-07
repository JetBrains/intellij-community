package com.intellij.workspaceModel.storage.entities.model.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityWithPersistentId
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import java.io.Serializable
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.Obj
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.*
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import org.jetbrains.deft.IntellijWs.IntellijWs





interface LibraryEntity : WorkspaceEntityWithPersistentId {
    override val name: String
    val tableId: LibraryTableId

    val roots: List<LibraryRoot>
    val excludedRoots: List<VirtualFileUrl>
    @Child val sdk: SdkEntity?
    @Child val libraryProperties: LibraryPropertiesEntity?
    @Child val libraryFilesPackagingElement: LibraryFilesPackagingElementEntity?

    override val persistentId: LibraryId
        get() = LibraryId(name, tableId)
    //region generated code
    //@formatter:off
    interface Builder: LibraryEntity, ModifiableWorkspaceEntity<LibraryEntity>, ObjBuilder<LibraryEntity> {
        override var name: String
        override var entitySource: EntitySource
        override var tableId: LibraryTableId
        override var roots: List<LibraryRoot>
        override var excludedRoots: List<VirtualFileUrl>
        override var sdk: SdkEntity?
        override var libraryProperties: LibraryPropertiesEntity?
        override var libraryFilesPackagingElement: LibraryFilesPackagingElementEntity?
    }
    
    companion object: ObjType<LibraryEntity, Builder>(IntellijWs, 46) {
        val nameField: Field<LibraryEntity, String> = Field(this, 0, "name", TString)
        val entitySource: Field<LibraryEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val tableId: Field<LibraryEntity, LibraryTableId> = Field(this, 0, "tableId", TBlob("LibraryTableId"))
        val roots: Field<LibraryEntity, List<LibraryRoot>> = Field(this, 0, "roots", TList(TBlob("LibraryRoot")))
        val excludedRoots: Field<LibraryEntity, List<VirtualFileUrl>> = Field(this, 0, "excludedRoots", TList(TBlob("VirtualFileUrl")))
        val sdk: Field<LibraryEntity, SdkEntity?> = Field(this, 0, "sdk", TOptional(TRef("org.jetbrains.deft.IntellijWs", 48, child = true)))
        val libraryProperties: Field<LibraryEntity, LibraryPropertiesEntity?> = Field(this, 0, "libraryProperties", TOptional(TRef("org.jetbrains.deft.IntellijWs", 47, child = true)))
        val libraryFilesPackagingElement: Field<LibraryEntity, LibraryFilesPackagingElementEntity?> = Field(this, 0, "libraryFilesPackagingElement", TOptional(TRef("org.jetbrains.deft.IntellijWs", 32, child = true)))
        val persistentId: Field<LibraryEntity, LibraryId> = Field(this, 0, "persistentId", TBlob("LibraryId"))
    }
    //@formatter:on
    //endregion

}

interface LibraryPropertiesEntity : WorkspaceEntity {
    val library: LibraryEntity

    val libraryType: String
    val propertiesXmlTag: String?
    //region generated code
    //@formatter:off
    interface Builder: LibraryPropertiesEntity, ModifiableWorkspaceEntity<LibraryPropertiesEntity>, ObjBuilder<LibraryPropertiesEntity> {
        override var library: LibraryEntity
        override var entitySource: EntitySource
        override var libraryType: String
        override var propertiesXmlTag: String?
    }
    
    companion object: ObjType<LibraryPropertiesEntity, Builder>(IntellijWs, 47) {
        val library: Field<LibraryPropertiesEntity, LibraryEntity> = Field(this, 0, "library", TRef("org.jetbrains.deft.IntellijWs", 46))
        val entitySource: Field<LibraryPropertiesEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val libraryType: Field<LibraryPropertiesEntity, String> = Field(this, 0, "libraryType", TString)
        val propertiesXmlTag: Field<LibraryPropertiesEntity, String?> = Field(this, 0, "propertiesXmlTag", TOptional(TString))
    }
    //@formatter:on
    //endregion

}

interface SdkEntity : WorkspaceEntity {
    val library: LibraryEntity

    val homeUrl: VirtualFileUrl
    //region generated code
    //@formatter:off
    interface Builder: SdkEntity, ModifiableWorkspaceEntity<SdkEntity>, ObjBuilder<SdkEntity> {
        override var library: LibraryEntity
        override var entitySource: EntitySource
        override var homeUrl: VirtualFileUrl
    }
    
    companion object: ObjType<SdkEntity, Builder>(IntellijWs, 48) {
        val library: Field<SdkEntity, LibraryEntity> = Field(this, 0, "library", TRef("org.jetbrains.deft.IntellijWs", 46))
        val entitySource: Field<SdkEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val homeUrl: Field<SdkEntity, VirtualFileUrl> = Field(this, 0, "homeUrl", TBlob("VirtualFileUrl"))
    }
    //@formatter:on
    //endregion

}

sealed class ModuleDependencyItem : Serializable {
    sealed class Exportable : ModuleDependencyItem() {
        abstract val exported: Boolean
        abstract val scope: DependencyScope

        abstract fun withScope(scope: DependencyScope): Exportable
        abstract fun withExported(exported: Boolean): Exportable

        data class ModuleDependency(
            val module: ModuleId,
            override val exported: Boolean,
            override val scope: DependencyScope,
            val productionOnTest: Boolean
        ) : Exportable() {
            override fun withScope(scope: DependencyScope): Exportable = copy(scope = scope)
            override fun withExported(exported: Boolean): Exportable = copy(exported = exported)
        }

        data class LibraryDependency(
            val library: LibraryId,
            override val exported: Boolean,
            override val scope: DependencyScope
        ) : Exportable() {
            override fun withScope(scope: DependencyScope): Exportable = copy(scope = scope)
            override fun withExported(exported: Boolean): Exportable = copy(exported = exported)
        }
    }

    //todo use LibraryProxyId to refer to SDK instead
    data class SdkDependency(val sdkName: String, val sdkType: String) : ModuleDependencyItem()

    object InheritedSdkDependency : ModuleDependencyItem()
    object ModuleSourceDependency : ModuleDependencyItem()
    enum class DependencyScope { COMPILE, TEST, RUNTIME, PROVIDED }
}

sealed class LibraryTableId : Serializable {
    data class ModuleLibraryTableId(val moduleId: ModuleId) : LibraryTableId() {
        override val level: String
            get() = "module"
    }
    object ProjectLibraryTableId : LibraryTableId() {
        override val level: String
            get() = "project"
    }
    data class GlobalLibraryTableId(override val level: String) : LibraryTableId()
    abstract val level: String
}

data class LibraryRootTypeId(val name: String) : Serializable {
    companion object {
        val COMPILED = LibraryRootTypeId("CLASSES")
        val SOURCES = LibraryRootTypeId("SOURCES")
    }
}

data class LibraryRoot(
    val url: VirtualFileUrl,
    val type: LibraryRootTypeId,
    val inclusionOptions: InclusionOptions = InclusionOptions.ROOT_ITSELF
) : Serializable {
    enum class InclusionOptions {
        ROOT_ITSELF, ARCHIVES_UNDER_ROOT, ARCHIVES_UNDER_ROOT_RECURSIVELY
    }
}