/*
 * Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.workspaceModel.storage.entities.model.api

import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityWithPersistentId
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.annotations.Open
import org.jetbrains.deft.annotations.Abstract
import org.jetbrains.deft.ObjBuilder
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
  
  companion object: Type<ArtifactEntity, Builder>(23)
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
    
    companion object: Type<ArtifactPropertiesEntity, Builder>(24)
    //@formatter:on
    //endregion

}

@Abstract interface PackagingElementEntity : WorkspaceEntity {
    val compositePackagingElement: CompositePackagingElementEntity
    //region generated code
    //@formatter:off
    interface Builder: PackagingElementEntity, ModifiableWorkspaceEntity<PackagingElementEntity>, ObjBuilder<PackagingElementEntity> {
        override var compositePackagingElement: CompositePackagingElementEntity
        override var entitySource: EntitySource
    }
    
    companion object: Type<PackagingElementEntity, Builder>(25)
    //@formatter:on
    //endregion

}

@Abstract interface CompositePackagingElementEntity : PackagingElementEntity {
    val artifact: ArtifactEntity

    val children: List<@Child PackagingElementEntity>
    //region generated code
    //@formatter:off
    interface Builder: CompositePackagingElementEntity, ModifiableWorkspaceEntity<CompositePackagingElementEntity>, ObjBuilder<CompositePackagingElementEntity> {
        override var compositePackagingElement: CompositePackagingElementEntity
        override var artifact: ArtifactEntity
        override var entitySource: EntitySource
        override var children: List<PackagingElementEntity>
    }
    
    companion object: Type<CompositePackagingElementEntity, Builder>(26, PackagingElementEntity)
    //@formatter:on
    //endregion

}

interface DirectoryPackagingElementEntity: CompositePackagingElementEntity {
    val directoryName: String
    //region generated code
    //@formatter:off
    interface Builder: DirectoryPackagingElementEntity, ModifiableWorkspaceEntity<DirectoryPackagingElementEntity>, ObjBuilder<DirectoryPackagingElementEntity> {
        override var compositePackagingElement: CompositePackagingElementEntity
        override var artifact: ArtifactEntity
        override var children: List<PackagingElementEntity>
        override var directoryName: String
        override var entitySource: EntitySource
    }
    
    companion object: Type<DirectoryPackagingElementEntity, Builder>(27, CompositePackagingElementEntity)
    //@formatter:on
    //endregion

}

interface ArchivePackagingElementEntity: CompositePackagingElementEntity {
    val fileName: String
    //region generated code
    //@formatter:off
    interface Builder: ArchivePackagingElementEntity, ModifiableWorkspaceEntity<ArchivePackagingElementEntity>, ObjBuilder<ArchivePackagingElementEntity> {
        override var compositePackagingElement: CompositePackagingElementEntity
        override var artifact: ArtifactEntity
        override var children: List<PackagingElementEntity>
        override var fileName: String
        override var entitySource: EntitySource
    }
    
    companion object: Type<ArchivePackagingElementEntity, Builder>(28, CompositePackagingElementEntity)
    //@formatter:on
    //endregion

}

interface ArtifactRootElementEntity: CompositePackagingElementEntity {
    //region generated code
    //@formatter:off
    interface Builder: ArtifactRootElementEntity, ModifiableWorkspaceEntity<ArtifactRootElementEntity>, ObjBuilder<ArtifactRootElementEntity> {
        override var compositePackagingElement: CompositePackagingElementEntity
        override var artifact: ArtifactEntity
        override var entitySource: EntitySource
        override var children: List<PackagingElementEntity>
    }
    
    companion object: Type<ArtifactRootElementEntity, Builder>(29, CompositePackagingElementEntity)
    //@formatter:on
    //endregion

}

interface ArtifactOutputPackagingElementEntity: PackagingElementEntity {
    val artifact: ArtifactEntity?
    //region generated code
    //@formatter:off
    interface Builder: ArtifactOutputPackagingElementEntity, ModifiableWorkspaceEntity<ArtifactOutputPackagingElementEntity>, ObjBuilder<ArtifactOutputPackagingElementEntity> {
        override var compositePackagingElement: CompositePackagingElementEntity
        override var artifact: ArtifactEntity?
        override var entitySource: EntitySource
    }
    
    companion object: Type<ArtifactOutputPackagingElementEntity, Builder>(30, PackagingElementEntity)
    //@formatter:on
    //endregion

}

interface ModuleOutputPackagingElementEntity : PackagingElementEntity {
    val module: ModuleId?
    //region generated code
    //@formatter:off
    interface Builder: ModuleOutputPackagingElementEntity, ModifiableWorkspaceEntity<ModuleOutputPackagingElementEntity>, ObjBuilder<ModuleOutputPackagingElementEntity> {
        override var compositePackagingElement: CompositePackagingElementEntity
        override var module: ModuleId?
        override var entitySource: EntitySource
    }
    
    companion object: Type<ModuleOutputPackagingElementEntity, Builder>(31, PackagingElementEntity)
    //@formatter:on
    //endregion

}

interface LibraryFilesPackagingElementEntity : PackagingElementEntity {
    val library: LibraryEntity?

    //region generated code
    //@formatter:off
    interface Builder: LibraryFilesPackagingElementEntity, ModifiableWorkspaceEntity<LibraryFilesPackagingElementEntity>, ObjBuilder<LibraryFilesPackagingElementEntity> {
        override var compositePackagingElement: CompositePackagingElementEntity
        override var library: LibraryEntity?
        override var entitySource: EntitySource
    }
    
    companion object: Type<LibraryFilesPackagingElementEntity, Builder>(32, PackagingElementEntity)
    //@formatter:on
    //endregion

}

interface ModuleSourcePackagingElementEntity : PackagingElementEntity {
    val module: ModuleId?
    //region generated code
    //@formatter:off
    interface Builder: ModuleSourcePackagingElementEntity, ModifiableWorkspaceEntity<ModuleSourcePackagingElementEntity>, ObjBuilder<ModuleSourcePackagingElementEntity> {
        override var compositePackagingElement: CompositePackagingElementEntity
        override var module: ModuleId?
        override var entitySource: EntitySource
    }
    
    companion object: Type<ModuleSourcePackagingElementEntity, Builder>(33, PackagingElementEntity)
    //@formatter:on
    //endregion

}

interface ModuleTestOutputPackagingElementEntity : PackagingElementEntity {
    val module: ModuleId?
    //region generated code
    //@formatter:off
    interface Builder: ModuleTestOutputPackagingElementEntity, ModifiableWorkspaceEntity<ModuleTestOutputPackagingElementEntity>, ObjBuilder<ModuleTestOutputPackagingElementEntity> {
        override var compositePackagingElement: CompositePackagingElementEntity
        override var module: ModuleId?
        override var entitySource: EntitySource
    }
    
    companion object: Type<ModuleTestOutputPackagingElementEntity, Builder>(34, PackagingElementEntity)
    //@formatter:on
    //endregion

}

@Open interface FileOrDirectoryPackagingElementEntity : PackagingElementEntity {
    val filePath: String
    //region generated code
    //@formatter:off
    interface Builder: FileOrDirectoryPackagingElementEntity, ModifiableWorkspaceEntity<FileOrDirectoryPackagingElementEntity>, ObjBuilder<FileOrDirectoryPackagingElementEntity> {
        override var compositePackagingElement: CompositePackagingElementEntity
        override var filePath: String
        override var entitySource: EntitySource
    }
    
    companion object: Type<FileOrDirectoryPackagingElementEntity, Builder>(35, PackagingElementEntity)
    //@formatter:on
    //endregion

}

interface DirectoryCopyPackagingElementEntity : FileOrDirectoryPackagingElementEntity {
    //region generated code
    //@formatter:off
    interface Builder: DirectoryCopyPackagingElementEntity, ModifiableWorkspaceEntity<DirectoryCopyPackagingElementEntity>, ObjBuilder<DirectoryCopyPackagingElementEntity> {
        override var compositePackagingElement: CompositePackagingElementEntity
        override var filePath: String
        override var entitySource: EntitySource
    }
    
    companion object: Type<DirectoryCopyPackagingElementEntity, Builder>(36, FileOrDirectoryPackagingElementEntity)
    //@formatter:on
    //endregion

}

interface ExtractedDirectoryPackagingElementEntity: FileOrDirectoryPackagingElementEntity {
    val pathInArchive: String
    //region generated code
    //@formatter:off
    interface Builder: ExtractedDirectoryPackagingElementEntity, ModifiableWorkspaceEntity<ExtractedDirectoryPackagingElementEntity>, ObjBuilder<ExtractedDirectoryPackagingElementEntity> {
        override var compositePackagingElement: CompositePackagingElementEntity
        override var filePath: String
        override var pathInArchive: String
        override var entitySource: EntitySource
    }
    
    companion object: Type<ExtractedDirectoryPackagingElementEntity, Builder>(37, FileOrDirectoryPackagingElementEntity)
    //@formatter:on
    //endregion

}

interface FileCopyPackagingElementEntity : FileOrDirectoryPackagingElementEntity {
    val renamedOutputFileName: String?
    //region generated code
    //@formatter:off
    interface Builder: FileCopyPackagingElementEntity, ModifiableWorkspaceEntity<FileCopyPackagingElementEntity>, ObjBuilder<FileCopyPackagingElementEntity> {
        override var compositePackagingElement: CompositePackagingElementEntity
        override var filePath: String
        override var renamedOutputFileName: String?
        override var entitySource: EntitySource
    }
    
    companion object: Type<FileCopyPackagingElementEntity, Builder>(38, FileOrDirectoryPackagingElementEntity)
    //@formatter:on
    //endregion

}

interface CustomPackagingElementEntity : CompositePackagingElementEntity {
    val typeId: String
    val propertiesXmlTag: String
    //region generated code
    //@formatter:off
    interface Builder: CustomPackagingElementEntity, ModifiableWorkspaceEntity<CustomPackagingElementEntity>, ObjBuilder<CustomPackagingElementEntity> {
        override var compositePackagingElement: CompositePackagingElementEntity
        override var artifact: ArtifactEntity
        override var children: List<PackagingElementEntity>
        override var typeId: String
        override var entitySource: EntitySource
        override var propertiesXmlTag: String
    }
    
    companion object: Type<CustomPackagingElementEntity, Builder>(39, CompositePackagingElementEntity)
    //@formatter:on
    //endregion

}