package com.intellij.workspace.model.api

import com.intellij.workspaceModel.codegen.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityWithPersistentId
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.annotations.Open
import org.jetbrains.deft.annotations.Abstract
import org.jetbrains.deft.IntellijWs.IntellijWs
import org.jetbrains.deft.Obj
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.*



interface ArtifactEntity : WorkspaceEntityWithPersistentId {
    override val name: String

    val artifactType: String
    val includeInProjectBuild: Boolean
    val outputUrl: VirtualFileUrl?

    @Child val rootElement: CompositePackagingElementEntity
    val customProperties: List<@Child ArtifactPropertiesEntity>
    @Child val artifactOutputPackagingElement: ArtifactOutputPackagingElementEntity?
    override val persistentId: ArtifactId
        get() = ArtifactId(name)

    //region generated code
    //@formatter:off
    interface Builder: ArtifactEntity, ObjBuilder<ArtifactEntity> {
        override var name: String
        override var entitySource: EntitySource
        override var artifactType: String
        override var includeInProjectBuild: Boolean
        override var outputUrl: VirtualFileUrl?
        override var rootElement: CompositePackagingElementEntity
        override var customProperties: List<ArtifactPropertiesEntity>
        override var artifactOutputPackagingElement: ArtifactOutputPackagingElementEntity?
    }
    
    companion object: ObjType<ArtifactEntity, Builder>(IntellijWs, 23) {
        val nameField: Field<ArtifactEntity, String> = Field(this, 0, "name", TString)
        val entitySource: Field<ArtifactEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val artifactType: Field<ArtifactEntity, String> = Field(this, 0, "artifactType", TString)
        val includeInProjectBuild: Field<ArtifactEntity, Boolean> = Field(this, 0, "includeInProjectBuild", TBoolean)
        val outputUrl: Field<ArtifactEntity, VirtualFileUrl?> = Field(this, 0, "outputUrl", TOptional(TBlob("VirtualFileUrl")))
        val rootElement: Field<ArtifactEntity, CompositePackagingElementEntity> = Field(this, 0, "rootElement", TRef("org.jetbrains.deft.IntellijWs", 26, child = true))
        val customProperties: Field<ArtifactEntity, List<ArtifactPropertiesEntity>> = Field(this, 0, "customProperties", TList(TRef("org.jetbrains.deft.IntellijWs", 24, child = true)))
        val artifactOutputPackagingElement: Field<ArtifactEntity, ArtifactOutputPackagingElementEntity?> = Field(this, 0, "artifactOutputPackagingElement", TOptional(TRef("org.jetbrains.deft.IntellijWs", 30, child = true)))
        val persistentId: Field<ArtifactEntity, ArtifactId> = Field(this, 0, "persistentId", TBlob("ArtifactId"))
    }
    //@formatter:on
    //endregion
}

interface ArtifactPropertiesEntity : WorkspaceEntity {
    val artifact: ArtifactEntity

    val providerType: String
    val propertiesXmlTag: String?

    //region generated code
    //@formatter:off
    interface Builder: ArtifactPropertiesEntity, ObjBuilder<ArtifactPropertiesEntity> {
        override var artifact: ArtifactEntity
        override var entitySource: EntitySource
        override var providerType: String
        override var propertiesXmlTag: String?
    }
    
    companion object: ObjType<ArtifactPropertiesEntity, Builder>(IntellijWs, 24) {
        val artifact: Field<ArtifactPropertiesEntity, ArtifactEntity> = Field(this, 0, "artifact", TRef("org.jetbrains.deft.IntellijWs", 23))
        val entitySource: Field<ArtifactPropertiesEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val providerType: Field<ArtifactPropertiesEntity, String> = Field(this, 0, "providerType", TString)
        val propertiesXmlTag: Field<ArtifactPropertiesEntity, String?> = Field(this, 0, "propertiesXmlTag", TOptional(TString))
    }
    //@formatter:on
    //endregion
}

@Abstract interface PackagingElementEntity : WorkspaceEntity {
    val compositePackagingElement: CompositePackagingElementEntity

    //region generated code
    //@formatter:off
    interface Builder: PackagingElementEntity, ObjBuilder<PackagingElementEntity> {
        override var compositePackagingElement: CompositePackagingElementEntity
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<PackagingElementEntity, Builder>(IntellijWs, 25) {
        val compositePackagingElement: Field<PackagingElementEntity, CompositePackagingElementEntity> = Field(this, 0, "compositePackagingElement", TRef("org.jetbrains.deft.IntellijWs", 26))
        val entitySource: Field<PackagingElementEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
    }
    //@formatter:on
    //endregion
}

@Abstract interface CompositePackagingElementEntity : PackagingElementEntity {
    val artifact: ArtifactEntity

    val children: List<@Child PackagingElementEntity>

    //region generated code
    //@formatter:off
    interface Builder: CompositePackagingElementEntity, ObjBuilder<CompositePackagingElementEntity> {
        override var compositePackagingElement: CompositePackagingElementEntity
        override var artifact: ArtifactEntity
        override var entitySource: EntitySource
        override var children: List<PackagingElementEntity>
    }
    
    companion object: ObjType<CompositePackagingElementEntity, Builder>(IntellijWs, 26, PackagingElementEntity) {
        val artifact: Field<CompositePackagingElementEntity, ArtifactEntity> = Field(this, 0, "artifact", TRef("org.jetbrains.deft.IntellijWs", 23))
        val entitySource: Field<CompositePackagingElementEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val children: Field<CompositePackagingElementEntity, List<PackagingElementEntity>> = Field(this, 0, "children", TList(TRef("org.jetbrains.deft.IntellijWs", 25, child = true)))
    }
    //@formatter:on
    //endregion
}

interface DirectoryPackagingElementEntity: CompositePackagingElementEntity {
    val directoryName: String

    //region generated code
    //@formatter:off
    interface Builder: DirectoryPackagingElementEntity, ObjBuilder<DirectoryPackagingElementEntity> {
        override var compositePackagingElement: CompositePackagingElementEntity
        override var artifact: ArtifactEntity
        override var children: List<PackagingElementEntity>
        override var directoryName: String
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<DirectoryPackagingElementEntity, Builder>(IntellijWs, 27, CompositePackagingElementEntity) {
        val directoryName: Field<DirectoryPackagingElementEntity, String> = Field(this, 0, "directoryName", TString)
        val entitySource: Field<DirectoryPackagingElementEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
    }
    //@formatter:on
    //endregion
}

interface ArchivePackagingElementEntity: CompositePackagingElementEntity {
    val fileName: String

    //region generated code
    //@formatter:off
    interface Builder: ArchivePackagingElementEntity, ObjBuilder<ArchivePackagingElementEntity> {
        override var compositePackagingElement: CompositePackagingElementEntity
        override var artifact: ArtifactEntity
        override var children: List<PackagingElementEntity>
        override var fileName: String
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<ArchivePackagingElementEntity, Builder>(IntellijWs, 28, CompositePackagingElementEntity) {
        val fileName: Field<ArchivePackagingElementEntity, String> = Field(this, 0, "fileName", TString)
        val entitySource: Field<ArchivePackagingElementEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
    }
    //@formatter:on
    //endregion
}

interface ArtifactRootElementEntity: CompositePackagingElementEntity {

    //region generated code
    //@formatter:off
    interface Builder: ArtifactRootElementEntity, ObjBuilder<ArtifactRootElementEntity> {
        override var compositePackagingElement: CompositePackagingElementEntity
        override var artifact: ArtifactEntity
        override var entitySource: EntitySource
        override var children: List<PackagingElementEntity>
    }
    
    companion object: ObjType<ArtifactRootElementEntity, Builder>(IntellijWs, 29, CompositePackagingElementEntity) {
    }
    //@formatter:on
    //endregion
}

interface ArtifactOutputPackagingElementEntity: PackagingElementEntity {
    val artifact: ArtifactEntity?

    //region generated code
    //@formatter:off
    interface Builder: ArtifactOutputPackagingElementEntity, ObjBuilder<ArtifactOutputPackagingElementEntity> {
        override var compositePackagingElement: CompositePackagingElementEntity
        override var artifact: ArtifactEntity?
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<ArtifactOutputPackagingElementEntity, Builder>(IntellijWs, 30, PackagingElementEntity) {
        val artifact: Field<ArtifactOutputPackagingElementEntity, ArtifactEntity?> = Field(this, 0, "artifact", TOptional(TRef("org.jetbrains.deft.IntellijWs", 23)))
        val entitySource: Field<ArtifactOutputPackagingElementEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
    }
    //@formatter:on
    //endregion
}

interface ModuleOutputPackagingElementEntity : PackagingElementEntity {
    val module: ModuleId?

    //region generated code
    //@formatter:off
    interface Builder: ModuleOutputPackagingElementEntity, ObjBuilder<ModuleOutputPackagingElementEntity> {
        override var compositePackagingElement: CompositePackagingElementEntity
        override var module: ModuleId?
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<ModuleOutputPackagingElementEntity, Builder>(IntellijWs, 31, PackagingElementEntity) {
        val moduleField: Field<ModuleOutputPackagingElementEntity, ModuleId?> = Field(this, 0, "module", TOptional(TBlob("ModuleId")))
        val entitySource: Field<ModuleOutputPackagingElementEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
    }
    //@formatter:on
    //endregion
}

interface LibraryFilesPackagingElementEntity : PackagingElementEntity {
    val library: LibraryEntity?

    //region generated code
    //@formatter:off
    interface Builder: LibraryFilesPackagingElementEntity, ObjBuilder<LibraryFilesPackagingElementEntity> {
        override var compositePackagingElement: CompositePackagingElementEntity
        override var library: LibraryEntity?
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<LibraryFilesPackagingElementEntity, Builder>(IntellijWs, 32, PackagingElementEntity) {
        val library: Field<LibraryFilesPackagingElementEntity, LibraryEntity?> = Field(this, 0, "library", TOptional(TRef("org.jetbrains.deft.IntellijWs", 46)))
        val entitySource: Field<LibraryFilesPackagingElementEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
    }
    //@formatter:on
    //endregion
}

interface ModuleSourcePackagingElementEntity : PackagingElementEntity {
    val module: ModuleId?

    //region generated code
    //@formatter:off
    interface Builder: ModuleSourcePackagingElementEntity, ObjBuilder<ModuleSourcePackagingElementEntity> {
        override var compositePackagingElement: CompositePackagingElementEntity
        override var module: ModuleId?
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<ModuleSourcePackagingElementEntity, Builder>(IntellijWs, 33, PackagingElementEntity) {
        val moduleField: Field<ModuleSourcePackagingElementEntity, ModuleId?> = Field(this, 0, "module", TOptional(TBlob("ModuleId")))
        val entitySource: Field<ModuleSourcePackagingElementEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
    }
    //@formatter:on
    //endregion
}

interface ModuleTestOutputPackagingElementEntity : PackagingElementEntity {
    val module: ModuleId?

    //region generated code
    //@formatter:off
    interface Builder: ModuleTestOutputPackagingElementEntity, ObjBuilder<ModuleTestOutputPackagingElementEntity> {
        override var compositePackagingElement: CompositePackagingElementEntity
        override var module: ModuleId?
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<ModuleTestOutputPackagingElementEntity, Builder>(IntellijWs, 34, PackagingElementEntity) {
        val moduleField: Field<ModuleTestOutputPackagingElementEntity, ModuleId?> = Field(this, 0, "module", TOptional(TBlob("ModuleId")))
        val entitySource: Field<ModuleTestOutputPackagingElementEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
    }
    //@formatter:on
    //endregion
}

@Open interface FileOrDirectoryPackagingElementEntity : PackagingElementEntity {
    val filePath: String

    //region generated code
    //@formatter:off
    interface Builder: FileOrDirectoryPackagingElementEntity, ObjBuilder<FileOrDirectoryPackagingElementEntity> {
        override var compositePackagingElement: CompositePackagingElementEntity
        override var filePath: String
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<FileOrDirectoryPackagingElementEntity, Builder>(IntellijWs, 35, PackagingElementEntity) {
        val filePath: Field<FileOrDirectoryPackagingElementEntity, String> = Field(this, 0, "filePath", TString)
        val entitySource: Field<FileOrDirectoryPackagingElementEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
    }
    //@formatter:on
    //endregion
}

interface DirectoryCopyPackagingElementEntity : FileOrDirectoryPackagingElementEntity {

    //region generated code
    //@formatter:off
    interface Builder: DirectoryCopyPackagingElementEntity, ObjBuilder<DirectoryCopyPackagingElementEntity> {
        override var compositePackagingElement: CompositePackagingElementEntity
        override var filePath: String
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<DirectoryCopyPackagingElementEntity, Builder>(IntellijWs, 36, FileOrDirectoryPackagingElementEntity) {
    }
    //@formatter:on
    //endregion
}

interface ExtractedDirectoryPackagingElementEntity: FileOrDirectoryPackagingElementEntity {
    val pathInArchive: String

    //region generated code
    //@formatter:off
    interface Builder: ExtractedDirectoryPackagingElementEntity, ObjBuilder<ExtractedDirectoryPackagingElementEntity> {
        override var compositePackagingElement: CompositePackagingElementEntity
        override var filePath: String
        override var pathInArchive: String
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<ExtractedDirectoryPackagingElementEntity, Builder>(IntellijWs, 37, FileOrDirectoryPackagingElementEntity) {
        val pathInArchive: Field<ExtractedDirectoryPackagingElementEntity, String> = Field(this, 0, "pathInArchive", TString)
        val entitySource: Field<ExtractedDirectoryPackagingElementEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
    }
    //@formatter:on
    //endregion
}

interface FileCopyPackagingElementEntity : FileOrDirectoryPackagingElementEntity {
    val renamedOutputFileName: String?

    //region generated code
    //@formatter:off
    interface Builder: FileCopyPackagingElementEntity, ObjBuilder<FileCopyPackagingElementEntity> {
        override var compositePackagingElement: CompositePackagingElementEntity
        override var filePath: String
        override var renamedOutputFileName: String?
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<FileCopyPackagingElementEntity, Builder>(IntellijWs, 38, FileOrDirectoryPackagingElementEntity) {
        val renamedOutputFileName: Field<FileCopyPackagingElementEntity, String?> = Field(this, 0, "renamedOutputFileName", TOptional(TString))
        val entitySource: Field<FileCopyPackagingElementEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
    }
    //@formatter:on
    //endregion
}

interface CustomPackagingElementEntity : CompositePackagingElementEntity {
    val typeId: String
    val propertiesXmlTag: String

    //region generated code
    //@formatter:off
    interface Builder: CustomPackagingElementEntity, ObjBuilder<CustomPackagingElementEntity> {
        override var compositePackagingElement: CompositePackagingElementEntity
        override var artifact: ArtifactEntity
        override var children: List<PackagingElementEntity>
        override var typeId: String
        override var entitySource: EntitySource
        override var propertiesXmlTag: String
    }
    
    companion object: ObjType<CustomPackagingElementEntity, Builder>(IntellijWs, 39, CompositePackagingElementEntity) {
        val typeId: Field<CustomPackagingElementEntity, String> = Field(this, 0, "typeId", TString)
        val entitySource: Field<CustomPackagingElementEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val propertiesXmlTag: Field<CustomPackagingElementEntity, String> = Field(this, 0, "propertiesXmlTag", TString)
    }
    //@formatter:on
    //endregion
}