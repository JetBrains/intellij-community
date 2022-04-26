// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.bridgeEntities.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Abstract
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion




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
    @GeneratedCodeApiVersion(0)
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
    
    companion object: Type<ArtifactEntity, Builder>() {
        operator fun invoke(name: String, entitySource: EntitySource, artifactType: String, includeInProjectBuild: Boolean, init: Builder.() -> Unit): ArtifactEntity {
            val builder = builder(init)
            builder.name = name
            builder.entitySource = entitySource
            builder.artifactType = artifactType
            builder.includeInProjectBuild = includeInProjectBuild
            return builder
        }
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
    @GeneratedCodeApiVersion(0)
    interface Builder: ArtifactPropertiesEntity, ModifiableWorkspaceEntity<ArtifactPropertiesEntity>, ObjBuilder<ArtifactPropertiesEntity> {
        override var artifact: ArtifactEntity
        override var entitySource: EntitySource
        override var providerType: String
        override var propertiesXmlTag: String?
    }
    
    companion object: Type<ArtifactPropertiesEntity, Builder>() {
        operator fun invoke(entitySource: EntitySource, providerType: String, init: Builder.() -> Unit): ArtifactPropertiesEntity {
            val builder = builder(init)
            builder.entitySource = entitySource
            builder.providerType = providerType
            return builder
        }
    }
    //@formatter:on
    //endregion

}

@Abstract interface PackagingElementEntity : WorkspaceEntity {
    val parentEntity: CompositePackagingElementEntity?

    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder<T: PackagingElementEntity>: PackagingElementEntity, ModifiableWorkspaceEntity<T>, ObjBuilder<T> {
        override var parentEntity: CompositePackagingElementEntity?
        override var entitySource: EntitySource
    }
    
    companion object: Type<PackagingElementEntity, Builder<PackagingElementEntity>>() {
        operator fun invoke(entitySource: EntitySource, init: Builder<PackagingElementEntity>.() -> Unit): PackagingElementEntity {
            val builder = builder(init)
            builder.entitySource = entitySource
            return builder
        }
    }
    //@formatter:on
    //endregion

}

@Abstract interface CompositePackagingElementEntity : PackagingElementEntity {
    val artifact: ArtifactEntity?

    val children: List<@Child PackagingElementEntity>


    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder<T: CompositePackagingElementEntity>: CompositePackagingElementEntity, PackagingElementEntity.Builder<T>, ModifiableWorkspaceEntity<T>, ObjBuilder<T> {
        override var parentEntity: CompositePackagingElementEntity?
        override var artifact: ArtifactEntity?
        override var entitySource: EntitySource
        override var children: List<PackagingElementEntity>
    }
    
    companion object: Type<CompositePackagingElementEntity, Builder<CompositePackagingElementEntity>>(PackagingElementEntity) {
        operator fun invoke(entitySource: EntitySource, init: Builder<CompositePackagingElementEntity>.() -> Unit): CompositePackagingElementEntity {
            val builder = builder(init)
            builder.entitySource = entitySource
            return builder
        }
    }
    //@formatter:on
    //endregion

}

interface DirectoryPackagingElementEntity: CompositePackagingElementEntity {
    val directoryName: String


    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder: DirectoryPackagingElementEntity, CompositePackagingElementEntity.Builder<DirectoryPackagingElementEntity>, ModifiableWorkspaceEntity<DirectoryPackagingElementEntity>, ObjBuilder<DirectoryPackagingElementEntity> {
        override var parentEntity: CompositePackagingElementEntity?
        override var artifact: ArtifactEntity?
        override var children: List<PackagingElementEntity>
        override var directoryName: String
        override var entitySource: EntitySource
    }
    
    companion object: Type<DirectoryPackagingElementEntity, Builder>(CompositePackagingElementEntity) {
        operator fun invoke(directoryName: String, entitySource: EntitySource, init: Builder.() -> Unit): DirectoryPackagingElementEntity {
            val builder = builder(init)
            builder.directoryName = directoryName
            builder.entitySource = entitySource
            return builder
        }
    }
    //@formatter:on
    //endregion

}

interface ArchivePackagingElementEntity: CompositePackagingElementEntity {
    val fileName: String


    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder: ArchivePackagingElementEntity, CompositePackagingElementEntity.Builder<ArchivePackagingElementEntity>, ModifiableWorkspaceEntity<ArchivePackagingElementEntity>, ObjBuilder<ArchivePackagingElementEntity> {
        override var parentEntity: CompositePackagingElementEntity?
        override var artifact: ArtifactEntity?
        override var children: List<PackagingElementEntity>
        override var fileName: String
        override var entitySource: EntitySource
    }
    
    companion object: Type<ArchivePackagingElementEntity, Builder>(CompositePackagingElementEntity) {
        operator fun invoke(fileName: String, entitySource: EntitySource, init: Builder.() -> Unit): ArchivePackagingElementEntity {
            val builder = builder(init)
            builder.fileName = fileName
            builder.entitySource = entitySource
            return builder
        }
    }
    //@formatter:on
    //endregion

}

interface ArtifactRootElementEntity: CompositePackagingElementEntity {


    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder: ArtifactRootElementEntity, CompositePackagingElementEntity.Builder<ArtifactRootElementEntity>, ModifiableWorkspaceEntity<ArtifactRootElementEntity>, ObjBuilder<ArtifactRootElementEntity> {
        override var parentEntity: CompositePackagingElementEntity?
        override var artifact: ArtifactEntity?
        override var entitySource: EntitySource
        override var children: List<PackagingElementEntity>
    }
    
    companion object: Type<ArtifactRootElementEntity, Builder>(CompositePackagingElementEntity) {
        operator fun invoke(entitySource: EntitySource, init: Builder.() -> Unit): ArtifactRootElementEntity {
            val builder = builder(init)
            builder.entitySource = entitySource
            return builder
        }
    }
    //@formatter:on
    //endregion

}

interface ArtifactOutputPackagingElementEntity: PackagingElementEntity {
    val artifact: ArtifactId?


    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder: ArtifactOutputPackagingElementEntity, PackagingElementEntity.Builder<ArtifactOutputPackagingElementEntity>, ModifiableWorkspaceEntity<ArtifactOutputPackagingElementEntity>, ObjBuilder<ArtifactOutputPackagingElementEntity> {
        override var parentEntity: CompositePackagingElementEntity?
        override var artifact: ArtifactId?
        override var entitySource: EntitySource
    }
    
    companion object: Type<ArtifactOutputPackagingElementEntity, Builder>(PackagingElementEntity) {
        operator fun invoke(entitySource: EntitySource, init: Builder.() -> Unit): ArtifactOutputPackagingElementEntity {
            val builder = builder(init)
            builder.entitySource = entitySource
            return builder
        }
    }
    //@formatter:on
    //endregion

}

val ArtifactOutputPackagingElementEntity.artifactEntity: ArtifactEntity
  get() = referrersx(ArtifactEntity::artifactOutputPackagingElement).single()

interface ModuleOutputPackagingElementEntity : PackagingElementEntity {
    val module: ModuleId?


    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder: ModuleOutputPackagingElementEntity, PackagingElementEntity.Builder<ModuleOutputPackagingElementEntity>, ModifiableWorkspaceEntity<ModuleOutputPackagingElementEntity>, ObjBuilder<ModuleOutputPackagingElementEntity> {
        override var parentEntity: CompositePackagingElementEntity?
        override var module: ModuleId?
        override var entitySource: EntitySource
    }
    
    companion object: Type<ModuleOutputPackagingElementEntity, Builder>(PackagingElementEntity) {
        operator fun invoke(entitySource: EntitySource, init: Builder.() -> Unit): ModuleOutputPackagingElementEntity {
            val builder = builder(init)
            builder.entitySource = entitySource
            return builder
        }
    }
    //@formatter:on
    //endregion

}

interface LibraryFilesPackagingElementEntity : PackagingElementEntity {
    val library: LibraryId?

    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder: LibraryFilesPackagingElementEntity, PackagingElementEntity.Builder<LibraryFilesPackagingElementEntity>, ModifiableWorkspaceEntity<LibraryFilesPackagingElementEntity>, ObjBuilder<LibraryFilesPackagingElementEntity> {
        override var parentEntity: CompositePackagingElementEntity?
        override var library: LibraryId?
        override var entitySource: EntitySource
    }
    
    companion object: Type<LibraryFilesPackagingElementEntity, Builder>(PackagingElementEntity) {
        operator fun invoke(entitySource: EntitySource, init: Builder.() -> Unit): LibraryFilesPackagingElementEntity {
            val builder = builder(init)
            builder.entitySource = entitySource
            return builder
        }
    }
    //@formatter:on
    //endregion

}

val LibraryFilesPackagingElementEntity.libraryEntity: LibraryEntity
  get() = referrersx(LibraryEntity::libraryFilesPackagingElement).single()

interface ModuleSourcePackagingElementEntity : PackagingElementEntity {
    val module: ModuleId?


    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder: ModuleSourcePackagingElementEntity, PackagingElementEntity.Builder<ModuleSourcePackagingElementEntity>, ModifiableWorkspaceEntity<ModuleSourcePackagingElementEntity>, ObjBuilder<ModuleSourcePackagingElementEntity> {
        override var parentEntity: CompositePackagingElementEntity?
        override var module: ModuleId?
        override var entitySource: EntitySource
    }
    
    companion object: Type<ModuleSourcePackagingElementEntity, Builder>(PackagingElementEntity) {
        operator fun invoke(entitySource: EntitySource, init: Builder.() -> Unit): ModuleSourcePackagingElementEntity {
            val builder = builder(init)
            builder.entitySource = entitySource
            return builder
        }
    }
    //@formatter:on
    //endregion

}

interface ModuleTestOutputPackagingElementEntity : PackagingElementEntity {
    val module: ModuleId?


    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder: ModuleTestOutputPackagingElementEntity, PackagingElementEntity.Builder<ModuleTestOutputPackagingElementEntity>, ModifiableWorkspaceEntity<ModuleTestOutputPackagingElementEntity>, ObjBuilder<ModuleTestOutputPackagingElementEntity> {
        override var parentEntity: CompositePackagingElementEntity?
        override var module: ModuleId?
        override var entitySource: EntitySource
    }
    
    companion object: Type<ModuleTestOutputPackagingElementEntity, Builder>(PackagingElementEntity) {
        operator fun invoke(entitySource: EntitySource, init: Builder.() -> Unit): ModuleTestOutputPackagingElementEntity {
            val builder = builder(init)
            builder.entitySource = entitySource
            return builder
        }
    }
    //@formatter:on
    //endregion

}

@Abstract interface FileOrDirectoryPackagingElementEntity : PackagingElementEntity {
    val filePath: VirtualFileUrl


    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder<T: FileOrDirectoryPackagingElementEntity>: FileOrDirectoryPackagingElementEntity, PackagingElementEntity.Builder<T>, ModifiableWorkspaceEntity<T>, ObjBuilder<T> {
        override var parentEntity: CompositePackagingElementEntity?
        override var filePath: VirtualFileUrl
        override var entitySource: EntitySource
    }
    
    companion object: Type<FileOrDirectoryPackagingElementEntity, Builder<FileOrDirectoryPackagingElementEntity>>(PackagingElementEntity) {
        operator fun invoke(filePath: VirtualFileUrl, entitySource: EntitySource, init: Builder<FileOrDirectoryPackagingElementEntity>.() -> Unit): FileOrDirectoryPackagingElementEntity {
            val builder = builder(init)
            builder.filePath = filePath
            builder.entitySource = entitySource
            return builder
        }
    }
    //@formatter:on
    //endregion

}

interface DirectoryCopyPackagingElementEntity : FileOrDirectoryPackagingElementEntity {


    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder: DirectoryCopyPackagingElementEntity, FileOrDirectoryPackagingElementEntity.Builder<DirectoryCopyPackagingElementEntity>, ModifiableWorkspaceEntity<DirectoryCopyPackagingElementEntity>, ObjBuilder<DirectoryCopyPackagingElementEntity> {
        override var parentEntity: CompositePackagingElementEntity?
        override var filePath: VirtualFileUrl
        override var entitySource: EntitySource
    }
    
    companion object: Type<DirectoryCopyPackagingElementEntity, Builder>(FileOrDirectoryPackagingElementEntity) {
        operator fun invoke(filePath: VirtualFileUrl, entitySource: EntitySource, init: Builder.() -> Unit): DirectoryCopyPackagingElementEntity {
            val builder = builder(init)
            builder.filePath = filePath
            builder.entitySource = entitySource
            return builder
        }
    }
    //@formatter:on
    //endregion

}

interface ExtractedDirectoryPackagingElementEntity: FileOrDirectoryPackagingElementEntity {
    val pathInArchive: String


    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder: ExtractedDirectoryPackagingElementEntity, FileOrDirectoryPackagingElementEntity.Builder<ExtractedDirectoryPackagingElementEntity>, ModifiableWorkspaceEntity<ExtractedDirectoryPackagingElementEntity>, ObjBuilder<ExtractedDirectoryPackagingElementEntity> {
        override var parentEntity: CompositePackagingElementEntity?
        override var filePath: VirtualFileUrl
        override var pathInArchive: String
        override var entitySource: EntitySource
    }
    
    companion object: Type<ExtractedDirectoryPackagingElementEntity, Builder>(FileOrDirectoryPackagingElementEntity) {
        operator fun invoke(filePath: VirtualFileUrl, pathInArchive: String, entitySource: EntitySource, init: Builder.() -> Unit): ExtractedDirectoryPackagingElementEntity {
            val builder = builder(init)
            builder.filePath = filePath
            builder.pathInArchive = pathInArchive
            builder.entitySource = entitySource
            return builder
        }
    }
    //@formatter:on
    //endregion

}

interface FileCopyPackagingElementEntity : FileOrDirectoryPackagingElementEntity {
    val renamedOutputFileName: String?


    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder: FileCopyPackagingElementEntity, FileOrDirectoryPackagingElementEntity.Builder<FileCopyPackagingElementEntity>, ModifiableWorkspaceEntity<FileCopyPackagingElementEntity>, ObjBuilder<FileCopyPackagingElementEntity> {
        override var parentEntity: CompositePackagingElementEntity?
        override var filePath: VirtualFileUrl
        override var renamedOutputFileName: String?
        override var entitySource: EntitySource
    }
    
    companion object: Type<FileCopyPackagingElementEntity, Builder>(FileOrDirectoryPackagingElementEntity) {
        operator fun invoke(filePath: VirtualFileUrl, entitySource: EntitySource, init: Builder.() -> Unit): FileCopyPackagingElementEntity {
            val builder = builder(init)
            builder.filePath = filePath
            builder.entitySource = entitySource
            return builder
        }
    }
    //@formatter:on
    //endregion

}

interface CustomPackagingElementEntity : CompositePackagingElementEntity {
    val typeId: String
    val propertiesXmlTag: String


    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder: CustomPackagingElementEntity, CompositePackagingElementEntity.Builder<CustomPackagingElementEntity>, ModifiableWorkspaceEntity<CustomPackagingElementEntity>, ObjBuilder<CustomPackagingElementEntity> {
        override var parentEntity: CompositePackagingElementEntity?
        override var artifact: ArtifactEntity?
        override var children: List<PackagingElementEntity>
        override var typeId: String
        override var entitySource: EntitySource
        override var propertiesXmlTag: String
    }
    
    companion object: Type<CustomPackagingElementEntity, Builder>(CompositePackagingElementEntity) {
        operator fun invoke(typeId: String, entitySource: EntitySource, propertiesXmlTag: String, init: Builder.() -> Unit): CustomPackagingElementEntity {
            val builder = builder(init)
            builder.typeId = typeId
            builder.entitySource = entitySource
            builder.propertiesXmlTag = propertiesXmlTag
            return builder
        }
    }
    //@formatter:on
    //endregion

}