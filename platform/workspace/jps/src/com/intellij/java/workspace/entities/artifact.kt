// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.workspace.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.NonNls
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

data class ArtifactId(val name: @NlsSafe String) : SymbolicEntityId<ArtifactEntity> {
  override val presentableName: String
    get() = name
}

interface ArtifactEntity : WorkspaceEntityWithSymbolicId {
  val name: String

  val artifactType: @NonNls String
  val includeInProjectBuild: Boolean
  val outputUrl: VirtualFileUrl?

  @Child val rootElement: CompositePackagingElementEntity?
  val customProperties: List<@Child ArtifactPropertiesEntity>
  @Child val artifactOutputPackagingElement: ArtifactOutputPackagingElementEntity?
  override val symbolicId: ArtifactId
    get() = ArtifactId(name)

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ArtifactEntity, WorkspaceEntity.Builder<ArtifactEntity> {
    override var entitySource: EntitySource
    override var name: String
    override var artifactType: String
    override var includeInProjectBuild: Boolean
    override var outputUrl: VirtualFileUrl?
    override var rootElement: CompositePackagingElementEntity?
    override var customProperties: List<ArtifactPropertiesEntity>
    override var artifactOutputPackagingElement: ArtifactOutputPackagingElementEntity?
  }

  companion object : EntityType<ArtifactEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(name: String,
                        artifactType: String,
                        includeInProjectBuild: Boolean,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): ArtifactEntity {
      val builder = builder()
      builder.name = name
      builder.artifactType = artifactType
      builder.includeInProjectBuild = includeInProjectBuild
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: ArtifactEntity, modification: ArtifactEntity.Builder.() -> Unit) = modifyEntity(
  ArtifactEntity.Builder::class.java, entity, modification)
//endregion

interface ArtifactPropertiesEntity : WorkspaceEntity {
  val artifact: ArtifactEntity

  val providerType: @NonNls String
  val propertiesXmlTag: @NonNls String?

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ArtifactPropertiesEntity, WorkspaceEntity.Builder<ArtifactPropertiesEntity> {
    override var entitySource: EntitySource
    override var artifact: ArtifactEntity
    override var providerType: String
    override var propertiesXmlTag: String?
  }

  companion object : EntityType<ArtifactPropertiesEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(providerType: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ArtifactPropertiesEntity {
      val builder = builder()
      builder.providerType = providerType
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: ArtifactPropertiesEntity,
                                      modification: ArtifactPropertiesEntity.Builder.() -> Unit) = modifyEntity(
  ArtifactPropertiesEntity.Builder::class.java, entity, modification)
//endregion

@Abstract interface PackagingElementEntity : WorkspaceEntity {
  val parentEntity: CompositePackagingElementEntity?

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder<T : PackagingElementEntity> : PackagingElementEntity, WorkspaceEntity.Builder<T> {
    override var entitySource: EntitySource
    override var parentEntity: CompositePackagingElementEntity?
  }

  companion object : EntityType<PackagingElementEntity, Builder<PackagingElementEntity>>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(entitySource: EntitySource, init: (Builder<PackagingElementEntity>.() -> Unit)? = null): PackagingElementEntity {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

@Abstract interface CompositePackagingElementEntity : PackagingElementEntity {
  val artifact: ArtifactEntity?

  val children: List<@Child PackagingElementEntity>

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder<T : CompositePackagingElementEntity> : CompositePackagingElementEntity, PackagingElementEntity.Builder<T>, WorkspaceEntity.Builder<T> {
    override var entitySource: EntitySource
    override var parentEntity: CompositePackagingElementEntity?
    override var artifact: ArtifactEntity?
    override var children: List<PackagingElementEntity>
  }

  companion object : EntityType<CompositePackagingElementEntity, Builder<CompositePackagingElementEntity>>(PackagingElementEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(entitySource: EntitySource,
                        init: (Builder<CompositePackagingElementEntity>.() -> Unit)? = null): CompositePackagingElementEntity {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

interface DirectoryPackagingElementEntity: CompositePackagingElementEntity {
  val directoryName: @NlsSafe String

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : DirectoryPackagingElementEntity, CompositePackagingElementEntity.Builder<DirectoryPackagingElementEntity>, WorkspaceEntity.Builder<DirectoryPackagingElementEntity> {
    override var entitySource: EntitySource
    override var parentEntity: CompositePackagingElementEntity?
    override var artifact: ArtifactEntity?
    override var children: List<PackagingElementEntity>
    override var directoryName: String
  }

  companion object : EntityType<DirectoryPackagingElementEntity, Builder>(CompositePackagingElementEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(directoryName: String,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): DirectoryPackagingElementEntity {
      val builder = builder()
      builder.directoryName = directoryName
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: DirectoryPackagingElementEntity,
                                      modification: DirectoryPackagingElementEntity.Builder.() -> Unit) = modifyEntity(
  DirectoryPackagingElementEntity.Builder::class.java, entity, modification)
//endregion

interface ArchivePackagingElementEntity: CompositePackagingElementEntity {
  val fileName: @NlsSafe String

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ArchivePackagingElementEntity, CompositePackagingElementEntity.Builder<ArchivePackagingElementEntity>, WorkspaceEntity.Builder<ArchivePackagingElementEntity> {
    override var entitySource: EntitySource
    override var parentEntity: CompositePackagingElementEntity?
    override var artifact: ArtifactEntity?
    override var children: List<PackagingElementEntity>
    override var fileName: String
  }

  companion object : EntityType<ArchivePackagingElementEntity, Builder>(CompositePackagingElementEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(fileName: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ArchivePackagingElementEntity {
      val builder = builder()
      builder.fileName = fileName
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: ArchivePackagingElementEntity,
                                      modification: ArchivePackagingElementEntity.Builder.() -> Unit) = modifyEntity(
  ArchivePackagingElementEntity.Builder::class.java, entity, modification)
//endregion

interface ArtifactRootElementEntity: CompositePackagingElementEntity {
  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ArtifactRootElementEntity, CompositePackagingElementEntity.Builder<ArtifactRootElementEntity>, WorkspaceEntity.Builder<ArtifactRootElementEntity> {
    override var entitySource: EntitySource
    override var parentEntity: CompositePackagingElementEntity?
    override var artifact: ArtifactEntity?
    override var children: List<PackagingElementEntity>
  }

  companion object : EntityType<ArtifactRootElementEntity, Builder>(CompositePackagingElementEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ArtifactRootElementEntity {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: ArtifactRootElementEntity,
                                      modification: ArtifactRootElementEntity.Builder.() -> Unit) = modifyEntity(
  ArtifactRootElementEntity.Builder::class.java, entity, modification)
//endregion

interface ArtifactOutputPackagingElementEntity: PackagingElementEntity {
  val artifact: ArtifactId?

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ArtifactOutputPackagingElementEntity, PackagingElementEntity.Builder<ArtifactOutputPackagingElementEntity>, WorkspaceEntity.Builder<ArtifactOutputPackagingElementEntity> {
    override var entitySource: EntitySource
    override var parentEntity: CompositePackagingElementEntity?
    override var artifact: ArtifactId?
  }

  companion object : EntityType<ArtifactOutputPackagingElementEntity, Builder>(PackagingElementEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ArtifactOutputPackagingElementEntity {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: ArtifactOutputPackagingElementEntity,
                                      modification: ArtifactOutputPackagingElementEntity.Builder.() -> Unit) = modifyEntity(
  ArtifactOutputPackagingElementEntity.Builder::class.java, entity, modification)

var ArtifactOutputPackagingElementEntity.Builder.artifactEntity: ArtifactEntity
  by WorkspaceEntity.extension()
//endregion

val ArtifactOutputPackagingElementEntity.artifactEntity: ArtifactEntity
  by WorkspaceEntity.extension()

interface ModuleOutputPackagingElementEntity : PackagingElementEntity {
  val module: ModuleId?

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ModuleOutputPackagingElementEntity, PackagingElementEntity.Builder<ModuleOutputPackagingElementEntity>, WorkspaceEntity.Builder<ModuleOutputPackagingElementEntity> {
    override var entitySource: EntitySource
    override var parentEntity: CompositePackagingElementEntity?
    override var module: ModuleId?
  }

  companion object : EntityType<ModuleOutputPackagingElementEntity, Builder>(PackagingElementEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ModuleOutputPackagingElementEntity {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: ModuleOutputPackagingElementEntity,
                                      modification: ModuleOutputPackagingElementEntity.Builder.() -> Unit) = modifyEntity(
  ModuleOutputPackagingElementEntity.Builder::class.java, entity, modification)
//endregion

interface LibraryFilesPackagingElementEntity : PackagingElementEntity {
  val library: LibraryId?

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : LibraryFilesPackagingElementEntity, PackagingElementEntity.Builder<LibraryFilesPackagingElementEntity>, WorkspaceEntity.Builder<LibraryFilesPackagingElementEntity> {
    override var entitySource: EntitySource
    override var parentEntity: CompositePackagingElementEntity?
    override var library: LibraryId?
  }

  companion object : EntityType<LibraryFilesPackagingElementEntity, Builder>(PackagingElementEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(entitySource: EntitySource, init: (Builder.() -> Unit)? = null): LibraryFilesPackagingElementEntity {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: LibraryFilesPackagingElementEntity,
                                      modification: LibraryFilesPackagingElementEntity.Builder.() -> Unit) = modifyEntity(
  LibraryFilesPackagingElementEntity.Builder::class.java, entity, modification)
//endregion

interface ModuleSourcePackagingElementEntity : PackagingElementEntity {
  val module: ModuleId?

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ModuleSourcePackagingElementEntity, PackagingElementEntity.Builder<ModuleSourcePackagingElementEntity>, WorkspaceEntity.Builder<ModuleSourcePackagingElementEntity> {
    override var entitySource: EntitySource
    override var parentEntity: CompositePackagingElementEntity?
    override var module: ModuleId?
  }

  companion object : EntityType<ModuleSourcePackagingElementEntity, Builder>(PackagingElementEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ModuleSourcePackagingElementEntity {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: ModuleSourcePackagingElementEntity,
                                      modification: ModuleSourcePackagingElementEntity.Builder.() -> Unit) = modifyEntity(
  ModuleSourcePackagingElementEntity.Builder::class.java, entity, modification)
//endregion

interface ModuleTestOutputPackagingElementEntity : PackagingElementEntity {
  val module: ModuleId?

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ModuleTestOutputPackagingElementEntity, PackagingElementEntity.Builder<ModuleTestOutputPackagingElementEntity>, WorkspaceEntity.Builder<ModuleTestOutputPackagingElementEntity> {
    override var entitySource: EntitySource
    override var parentEntity: CompositePackagingElementEntity?
    override var module: ModuleId?
  }

  companion object : EntityType<ModuleTestOutputPackagingElementEntity, Builder>(PackagingElementEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ModuleTestOutputPackagingElementEntity {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: ModuleTestOutputPackagingElementEntity,
                                      modification: ModuleTestOutputPackagingElementEntity.Builder.() -> Unit) = modifyEntity(
  ModuleTestOutputPackagingElementEntity.Builder::class.java, entity, modification)
//endregion

@Abstract interface FileOrDirectoryPackagingElementEntity : PackagingElementEntity {
  val filePath: VirtualFileUrl

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder<T : FileOrDirectoryPackagingElementEntity> : FileOrDirectoryPackagingElementEntity, PackagingElementEntity.Builder<T>, WorkspaceEntity.Builder<T> {
    override var entitySource: EntitySource
    override var parentEntity: CompositePackagingElementEntity?
    override var filePath: VirtualFileUrl
  }

  companion object : EntityType<FileOrDirectoryPackagingElementEntity, Builder<FileOrDirectoryPackagingElementEntity>>(
    PackagingElementEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(filePath: VirtualFileUrl,
                        entitySource: EntitySource,
                        init: (Builder<FileOrDirectoryPackagingElementEntity>.() -> Unit)? = null): FileOrDirectoryPackagingElementEntity {
      val builder = builder()
      builder.filePath = filePath
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

interface DirectoryCopyPackagingElementEntity : FileOrDirectoryPackagingElementEntity {
  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : DirectoryCopyPackagingElementEntity, FileOrDirectoryPackagingElementEntity.Builder<DirectoryCopyPackagingElementEntity>, WorkspaceEntity.Builder<DirectoryCopyPackagingElementEntity> {
    override var entitySource: EntitySource
    override var parentEntity: CompositePackagingElementEntity?
    override var filePath: VirtualFileUrl
  }

  companion object : EntityType<DirectoryCopyPackagingElementEntity, Builder>(FileOrDirectoryPackagingElementEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(filePath: VirtualFileUrl,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): DirectoryCopyPackagingElementEntity {
      val builder = builder()
      builder.filePath = filePath
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: DirectoryCopyPackagingElementEntity,
                                      modification: DirectoryCopyPackagingElementEntity.Builder.() -> Unit) = modifyEntity(
  DirectoryCopyPackagingElementEntity.Builder::class.java, entity, modification)
//endregion

interface ExtractedDirectoryPackagingElementEntity: FileOrDirectoryPackagingElementEntity {
  val pathInArchive: @NlsSafe String

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ExtractedDirectoryPackagingElementEntity, FileOrDirectoryPackagingElementEntity.Builder<ExtractedDirectoryPackagingElementEntity>, WorkspaceEntity.Builder<ExtractedDirectoryPackagingElementEntity> {
    override var entitySource: EntitySource
    override var parentEntity: CompositePackagingElementEntity?
    override var filePath: VirtualFileUrl
    override var pathInArchive: String
  }

  companion object : EntityType<ExtractedDirectoryPackagingElementEntity, Builder>(FileOrDirectoryPackagingElementEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(filePath: VirtualFileUrl,
                        pathInArchive: String,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): ExtractedDirectoryPackagingElementEntity {
      val builder = builder()
      builder.filePath = filePath
      builder.pathInArchive = pathInArchive
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: ExtractedDirectoryPackagingElementEntity,
                                      modification: ExtractedDirectoryPackagingElementEntity.Builder.() -> Unit) = modifyEntity(
  ExtractedDirectoryPackagingElementEntity.Builder::class.java, entity, modification)
//endregion

interface FileCopyPackagingElementEntity : FileOrDirectoryPackagingElementEntity {
  val renamedOutputFileName: @NlsSafe String?

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : FileCopyPackagingElementEntity, FileOrDirectoryPackagingElementEntity.Builder<FileCopyPackagingElementEntity>, WorkspaceEntity.Builder<FileCopyPackagingElementEntity> {
    override var entitySource: EntitySource
    override var parentEntity: CompositePackagingElementEntity?
    override var filePath: VirtualFileUrl
    override var renamedOutputFileName: String?
  }

  companion object : EntityType<FileCopyPackagingElementEntity, Builder>(FileOrDirectoryPackagingElementEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(filePath: VirtualFileUrl,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): FileCopyPackagingElementEntity {
      val builder = builder()
      builder.filePath = filePath
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: FileCopyPackagingElementEntity,
                                      modification: FileCopyPackagingElementEntity.Builder.() -> Unit) = modifyEntity(
  FileCopyPackagingElementEntity.Builder::class.java, entity, modification)
//endregion

interface CustomPackagingElementEntity : CompositePackagingElementEntity {
  val typeId: @NonNls String
  val propertiesXmlTag: @NonNls String

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : CustomPackagingElementEntity, CompositePackagingElementEntity.Builder<CustomPackagingElementEntity>, WorkspaceEntity.Builder<CustomPackagingElementEntity> {
    override var entitySource: EntitySource
    override var parentEntity: CompositePackagingElementEntity?
    override var artifact: ArtifactEntity?
    override var children: List<PackagingElementEntity>
    override var typeId: String
    override var propertiesXmlTag: String
  }

  companion object : EntityType<CustomPackagingElementEntity, Builder>(CompositePackagingElementEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(typeId: String,
                        propertiesXmlTag: String,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): CustomPackagingElementEntity {
      val builder = builder()
      builder.typeId = typeId
      builder.propertiesXmlTag = propertiesXmlTag
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: CustomPackagingElementEntity,
                                      modification: CustomPackagingElementEntity.Builder.() -> Unit) = modifyEntity(
  CustomPackagingElementEntity.Builder::class.java, entity, modification)
//endregion

/**
 * This entity stores order of artifacts in ipr file. This is needed to ensure that artifact tags are saved in the same order to avoid
 * unnecessary modifications of ipr file.
 */
interface ArtifactsOrderEntity : WorkspaceEntity {
  val orderOfArtifacts: List<@NlsSafe String>

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ArtifactsOrderEntity, WorkspaceEntity.Builder<ArtifactsOrderEntity> {
    override var entitySource: EntitySource
    override var orderOfArtifacts: MutableList<String>
  }

  companion object : EntityType<ArtifactsOrderEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(orderOfArtifacts: List<String>,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): ArtifactsOrderEntity {
      val builder = builder()
      builder.orderOfArtifacts = orderOfArtifacts.toMutableWorkspaceList()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: ArtifactsOrderEntity, modification: ArtifactsOrderEntity.Builder.() -> Unit) = modifyEntity(
  ArtifactsOrderEntity.Builder::class.java, entity, modification)
//endregion
