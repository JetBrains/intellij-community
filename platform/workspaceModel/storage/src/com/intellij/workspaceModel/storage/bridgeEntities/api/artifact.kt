// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.bridgeEntities.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.annotations.Abstract
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.annotations.Open
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import org.jetbrains.deft.Type



interface ArtifactEntity : WorkspaceEntityWithPersistentId {
    val name: String

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
    interface Builder: ArtifactEntity, ModifiableWorkspaceEntity<ArtifactEntity>, ObjBuilder<ArtifactEntity> {
        override var name: String
        override var entitySource: EntitySource
        override var artifactType: String
        override var includeInProjectBuild: Boolean
        override var outputUrl: VirtualFileUrl?
        override var rootElement: CompositePackagingElementEntity
        override var customProperties: List<ArtifactPropertiesEntity>
        override var artifactOutputPackagingElement: ArtifactOutputPackagingElementEntity?
    }
    
    companion object: Type<ArtifactEntity, Builder>(26)
    //@formatter:on
    //endregion

}

interface ArtifactPropertiesEntity : WorkspaceEntity {
    val artifact: ArtifactEntity

    val providerType: String
    val propertiesXmlTag: String?


    //region generated code
    //@formatter:off
    interface Builder: ArtifactPropertiesEntity, ModifiableWorkspaceEntity<ArtifactPropertiesEntity>, ObjBuilder<ArtifactPropertiesEntity> {
        override var artifact: ArtifactEntity
        override var entitySource: EntitySource
        override var providerType: String
        override var propertiesXmlTag: String?
    }
    
    companion object: Type<ArtifactPropertiesEntity, Builder>(27)
    //@formatter:on
    //endregion

}

@Abstract interface PackagingElementEntity : WorkspaceEntity {
    val parentEntity: CompositePackagingElementEntity?

    //region generated code
    //@formatter:off
    interface Builder: PackagingElementEntity, ModifiableWorkspaceEntity<PackagingElementEntity>, ObjBuilder<PackagingElementEntity> {
        override var parentEntity: CompositePackagingElementEntity?
        override var entitySource: EntitySource
    }
    
    companion object: Type<PackagingElementEntity, Builder>(28)
    //@formatter:on
    //endregion

}

@Abstract interface CompositePackagingElementEntity : PackagingElementEntity {
    val artifact: ArtifactEntity?

    val children: List<@Child PackagingElementEntity>


    //region generated code
    //@formatter:off
    interface Builder: CompositePackagingElementEntity, ModifiableWorkspaceEntity<CompositePackagingElementEntity>, ObjBuilder<CompositePackagingElementEntity> {
        override var parentEntity: CompositePackagingElementEntity?
        override var artifact: ArtifactEntity?
        override var entitySource: EntitySource
        override var children: List<PackagingElementEntity>
    }
    
    companion object: Type<CompositePackagingElementEntity, Builder>(29, PackagingElementEntity)
    //@formatter:on
    //endregion

}

interface DirectoryPackagingElementEntity: CompositePackagingElementEntity {
    val directoryName: String


    //region generated code
    //@formatter:off
    interface Builder: DirectoryPackagingElementEntity, ModifiableWorkspaceEntity<DirectoryPackagingElementEntity>, ObjBuilder<DirectoryPackagingElementEntity> {
        override var parentEntity: CompositePackagingElementEntity?
        override var artifact: ArtifactEntity?
        override var children: List<PackagingElementEntity>
        override var directoryName: String
        override var entitySource: EntitySource
    }
    
    companion object: Type<DirectoryPackagingElementEntity, Builder>(30, CompositePackagingElementEntity)
    //@formatter:on
    //endregion

}

interface ArchivePackagingElementEntity: CompositePackagingElementEntity {
    val fileName: String


    //region generated code
    //@formatter:off
    interface Builder: ArchivePackagingElementEntity, ModifiableWorkspaceEntity<ArchivePackagingElementEntity>, ObjBuilder<ArchivePackagingElementEntity> {
        override var parentEntity: CompositePackagingElementEntity?
        override var artifact: ArtifactEntity?
        override var children: List<PackagingElementEntity>
        override var fileName: String
        override var entitySource: EntitySource
    }
    
    companion object: Type<ArchivePackagingElementEntity, Builder>(31, CompositePackagingElementEntity)
    //@formatter:on
    //endregion

}

interface ArtifactRootElementEntity: CompositePackagingElementEntity {


    //region generated code
    //@formatter:off
    interface Builder: ArtifactRootElementEntity, ModifiableWorkspaceEntity<ArtifactRootElementEntity>, ObjBuilder<ArtifactRootElementEntity> {
        override var parentEntity: CompositePackagingElementEntity?
        override var artifact: ArtifactEntity?
        override var entitySource: EntitySource
        override var children: List<PackagingElementEntity>
    }
    
    companion object: Type<ArtifactRootElementEntity, Builder>(32, CompositePackagingElementEntity)
    //@formatter:on
    //endregion

}

interface ArtifactOutputPackagingElementEntity: PackagingElementEntity {
    val artifact: ArtifactId?


    //region generated code
    //@formatter:off
    interface Builder: ArtifactOutputPackagingElementEntity, ModifiableWorkspaceEntity<ArtifactOutputPackagingElementEntity>, ObjBuilder<ArtifactOutputPackagingElementEntity> {
        override var parentEntity: CompositePackagingElementEntity?
        override var artifact: ArtifactId?
        override var entitySource: EntitySource
    }
    
    companion object: Type<ArtifactOutputPackagingElementEntity, Builder>(33, PackagingElementEntity)
    //@formatter:on
    //endregion

}

val ArtifactOutputPackagingElementEntity.artifactEntity: ArtifactEntity
  get() = referrersx(ArtifactEntity::artifactOutputPackagingElement).single()

interface ModuleOutputPackagingElementEntity : PackagingElementEntity {
    val module: ModuleId?


    //region generated code
    //@formatter:off
    interface Builder: ModuleOutputPackagingElementEntity, ModifiableWorkspaceEntity<ModuleOutputPackagingElementEntity>, ObjBuilder<ModuleOutputPackagingElementEntity> {
        override var parentEntity: CompositePackagingElementEntity?
        override var module: ModuleId?
        override var entitySource: EntitySource
    }
    
    companion object: Type<ModuleOutputPackagingElementEntity, Builder>(34, PackagingElementEntity)
    //@formatter:on
    //endregion

}

interface LibraryFilesPackagingElementEntity : PackagingElementEntity {
    val library: LibraryId?

    //region generated code
    //@formatter:off
    interface Builder: LibraryFilesPackagingElementEntity, ModifiableWorkspaceEntity<LibraryFilesPackagingElementEntity>, ObjBuilder<LibraryFilesPackagingElementEntity> {
        override var parentEntity: CompositePackagingElementEntity?
        override var library: LibraryId?
        override var entitySource: EntitySource
    }
    
    companion object: Type<LibraryFilesPackagingElementEntity, Builder>(35, PackagingElementEntity)
    //@formatter:on
    //endregion

}

val LibraryFilesPackagingElementEntity.libraryEntity: LibraryEntity
  get() = referrersx(LibraryEntity::libraryFilesPackagingElement).single()

interface ModuleSourcePackagingElementEntity : PackagingElementEntity {
    val module: ModuleId?


    //region generated code
    //@formatter:off
    interface Builder: ModuleSourcePackagingElementEntity, ModifiableWorkspaceEntity<ModuleSourcePackagingElementEntity>, ObjBuilder<ModuleSourcePackagingElementEntity> {
        override var parentEntity: CompositePackagingElementEntity?
        override var module: ModuleId?
        override var entitySource: EntitySource
    }
    
    companion object: Type<ModuleSourcePackagingElementEntity, Builder>(36, PackagingElementEntity)
    //@formatter:on
    //endregion

}

interface ModuleTestOutputPackagingElementEntity : PackagingElementEntity {
    val module: ModuleId?


    //region generated code
    //@formatter:off
    interface Builder: ModuleTestOutputPackagingElementEntity, ModifiableWorkspaceEntity<ModuleTestOutputPackagingElementEntity>, ObjBuilder<ModuleTestOutputPackagingElementEntity> {
        override var parentEntity: CompositePackagingElementEntity?
        override var module: ModuleId?
        override var entitySource: EntitySource
    }
    
    companion object: Type<ModuleTestOutputPackagingElementEntity, Builder>(37, PackagingElementEntity)
    //@formatter:on
    //endregion

}

@Open interface FileOrDirectoryPackagingElementEntity : PackagingElementEntity {
    val filePath: VirtualFileUrl


    //region generated code
    //@formatter:off
    interface Builder: FileOrDirectoryPackagingElementEntity, ModifiableWorkspaceEntity<FileOrDirectoryPackagingElementEntity>, ObjBuilder<FileOrDirectoryPackagingElementEntity> {
        override var parentEntity: CompositePackagingElementEntity?
        override var filePath: VirtualFileUrl
        override var entitySource: EntitySource
    }
    
    companion object: Type<FileOrDirectoryPackagingElementEntity, Builder>(38, PackagingElementEntity)
    //@formatter:on
    //endregion

}

interface DirectoryCopyPackagingElementEntity : FileOrDirectoryPackagingElementEntity {


    //region generated code
    //@formatter:off
    interface Builder: DirectoryCopyPackagingElementEntity, ModifiableWorkspaceEntity<DirectoryCopyPackagingElementEntity>, ObjBuilder<DirectoryCopyPackagingElementEntity> {
        override var parentEntity: CompositePackagingElementEntity?
        override var filePath: VirtualFileUrl
        override var entitySource: EntitySource
    }
    
    companion object: Type<DirectoryCopyPackagingElementEntity, Builder>(39, FileOrDirectoryPackagingElementEntity)
    //@formatter:on
    //endregion

}

interface ExtractedDirectoryPackagingElementEntity: FileOrDirectoryPackagingElementEntity {
    val pathInArchive: String


    //region generated code
    //@formatter:off
    interface Builder: ExtractedDirectoryPackagingElementEntity, ModifiableWorkspaceEntity<ExtractedDirectoryPackagingElementEntity>, ObjBuilder<ExtractedDirectoryPackagingElementEntity> {
        override var parentEntity: CompositePackagingElementEntity?
        override var filePath: VirtualFileUrl
        override var pathInArchive: String
        override var entitySource: EntitySource
    }
    
    companion object: Type<ExtractedDirectoryPackagingElementEntity, Builder>(40, FileOrDirectoryPackagingElementEntity)
    //@formatter:on
    //endregion

}

interface FileCopyPackagingElementEntity : FileOrDirectoryPackagingElementEntity {
    val renamedOutputFileName: String?


    //region generated code
    //@formatter:off
    interface Builder: FileCopyPackagingElementEntity, ModifiableWorkspaceEntity<FileCopyPackagingElementEntity>, ObjBuilder<FileCopyPackagingElementEntity> {
        override var parentEntity: CompositePackagingElementEntity?
        override var filePath: VirtualFileUrl
        override var renamedOutputFileName: String?
        override var entitySource: EntitySource
    }
    
    companion object: Type<FileCopyPackagingElementEntity, Builder>(41, FileOrDirectoryPackagingElementEntity)
    //@formatter:on
    //endregion

}

interface CustomPackagingElementEntity : CompositePackagingElementEntity {
    val typeId: String
    val propertiesXmlTag: String


    //region generated code
    //@formatter:off
    interface Builder: CustomPackagingElementEntity, ModifiableWorkspaceEntity<CustomPackagingElementEntity>, ObjBuilder<CustomPackagingElementEntity> {
        override var parentEntity: CompositePackagingElementEntity?
        override var artifact: ArtifactEntity?
        override var children: List<PackagingElementEntity>
        override var typeId: String
        override var entitySource: EntitySource
        override var propertiesXmlTag: String
    }
    
    companion object: Type<CustomPackagingElementEntity, Builder>(42, CompositePackagingElementEntity)
    //@formatter:on
    //endregion

}