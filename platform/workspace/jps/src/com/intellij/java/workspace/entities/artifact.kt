// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.workspace.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.NonNls

data class ArtifactId(val name: @NlsSafe String) : SymbolicEntityId<ArtifactEntity> {
  override val presentableName: String
    get() = name
}

/**
 * See [com.intellij.packaging.artifacts.LegacyBridgeJpsArtifactEntitySourceFactory]
 */
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
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ArtifactEntity> {
    override var entitySource: EntitySource
    var name: String
    var artifactType: String
    var includeInProjectBuild: Boolean
    var outputUrl: VirtualFileUrl?
    var rootElement: CompositePackagingElementEntity.Builder<out CompositePackagingElementEntity>?
    var customProperties: List<ArtifactPropertiesEntity.Builder>
    var artifactOutputPackagingElement: ArtifactOutputPackagingElementEntity.Builder?
  }

  companion object : EntityType<ArtifactEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      name: String,
      artifactType: String,
      includeInProjectBuild: Boolean,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
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
fun MutableEntityStorage.modifyArtifactEntity(
  entity: ArtifactEntity,
  modification: ArtifactEntity.Builder.() -> Unit,
): ArtifactEntity {
  return modifyEntity(ArtifactEntity.Builder::class.java, entity, modification)
}
//endregion

interface ArtifactPropertiesEntity : WorkspaceEntity {
  val artifact: ArtifactEntity

  val providerType: @NonNls String
  val propertiesXmlTag: @NonNls String?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ArtifactPropertiesEntity> {
    override var entitySource: EntitySource
    var artifact: ArtifactEntity.Builder
    var providerType: String
    var propertiesXmlTag: String?
  }

  companion object : EntityType<ArtifactPropertiesEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      providerType: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
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
fun MutableEntityStorage.modifyArtifactPropertiesEntity(
  entity: ArtifactPropertiesEntity,
  modification: ArtifactPropertiesEntity.Builder.() -> Unit,
): ArtifactPropertiesEntity {
  return modifyEntity(ArtifactPropertiesEntity.Builder::class.java, entity, modification)
}
//endregion

@Abstract interface PackagingElementEntity : WorkspaceEntity {
  val parentEntity: CompositePackagingElementEntity?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder<T : PackagingElementEntity> : WorkspaceEntity.Builder<T> {
    override var entitySource: EntitySource
    var parentEntity: CompositePackagingElementEntity.Builder<out CompositePackagingElementEntity>?
  }

  companion object : EntityType<PackagingElementEntity, Builder<PackagingElementEntity>>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      entitySource: EntitySource,
      init: (Builder<PackagingElementEntity>.() -> Unit)? = null,
    ): Builder<PackagingElementEntity> {
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
  @GeneratedCodeApiVersion(3)
  interface Builder<T : CompositePackagingElementEntity> : WorkspaceEntity.Builder<T>, PackagingElementEntity.Builder<T> {
    override var entitySource: EntitySource
    override var parentEntity: CompositePackagingElementEntity.Builder<out CompositePackagingElementEntity>?
    var artifact: ArtifactEntity.Builder?
    var children: List<PackagingElementEntity.Builder<out PackagingElementEntity>>
  }

  companion object : EntityType<CompositePackagingElementEntity, Builder<CompositePackagingElementEntity>>(PackagingElementEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      entitySource: EntitySource,
      init: (Builder<CompositePackagingElementEntity>.() -> Unit)? = null,
    ): Builder<CompositePackagingElementEntity> {
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
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<DirectoryPackagingElementEntity>,
                      CompositePackagingElementEntity.Builder<DirectoryPackagingElementEntity> {
    override var entitySource: EntitySource
    override var parentEntity: CompositePackagingElementEntity.Builder<out CompositePackagingElementEntity>?
    override var artifact: ArtifactEntity.Builder?
    override var children: List<PackagingElementEntity.Builder<out PackagingElementEntity>>
    var directoryName: String
  }

  companion object : EntityType<DirectoryPackagingElementEntity, Builder>(CompositePackagingElementEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      directoryName: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
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
fun MutableEntityStorage.modifyDirectoryPackagingElementEntity(
  entity: DirectoryPackagingElementEntity,
  modification: DirectoryPackagingElementEntity.Builder.() -> Unit,
): DirectoryPackagingElementEntity {
  return modifyEntity(DirectoryPackagingElementEntity.Builder::class.java, entity, modification)
}
//endregion

interface ArchivePackagingElementEntity: CompositePackagingElementEntity {
  val fileName: @NlsSafe String

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ArchivePackagingElementEntity>,
                      CompositePackagingElementEntity.Builder<ArchivePackagingElementEntity> {
    override var entitySource: EntitySource
    override var parentEntity: CompositePackagingElementEntity.Builder<out CompositePackagingElementEntity>?
    override var artifact: ArtifactEntity.Builder?
    override var children: List<PackagingElementEntity.Builder<out PackagingElementEntity>>
    var fileName: String
  }

  companion object : EntityType<ArchivePackagingElementEntity, Builder>(CompositePackagingElementEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      fileName: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
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
fun MutableEntityStorage.modifyArchivePackagingElementEntity(
  entity: ArchivePackagingElementEntity,
  modification: ArchivePackagingElementEntity.Builder.() -> Unit,
): ArchivePackagingElementEntity {
  return modifyEntity(ArchivePackagingElementEntity.Builder::class.java, entity, modification)
}
//endregion

interface ArtifactRootElementEntity: CompositePackagingElementEntity {
  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ArtifactRootElementEntity>,
                      CompositePackagingElementEntity.Builder<ArtifactRootElementEntity> {
    override var entitySource: EntitySource
    override var parentEntity: CompositePackagingElementEntity.Builder<out CompositePackagingElementEntity>?
    override var artifact: ArtifactEntity.Builder?
    override var children: List<PackagingElementEntity.Builder<out PackagingElementEntity>>
  }

  companion object : EntityType<ArtifactRootElementEntity, Builder>(CompositePackagingElementEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyArtifactRootElementEntity(
  entity: ArtifactRootElementEntity,
  modification: ArtifactRootElementEntity.Builder.() -> Unit,
): ArtifactRootElementEntity {
  return modifyEntity(ArtifactRootElementEntity.Builder::class.java, entity, modification)
}
//endregion

interface ArtifactOutputPackagingElementEntity: PackagingElementEntity {
  val artifact: ArtifactId?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ArtifactOutputPackagingElementEntity>,
                      PackagingElementEntity.Builder<ArtifactOutputPackagingElementEntity> {
    override var entitySource: EntitySource
    override var parentEntity: CompositePackagingElementEntity.Builder<out CompositePackagingElementEntity>?
    var artifact: ArtifactId?
  }

  companion object : EntityType<ArtifactOutputPackagingElementEntity, Builder>(PackagingElementEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyArtifactOutputPackagingElementEntity(
  entity: ArtifactOutputPackagingElementEntity,
  modification: ArtifactOutputPackagingElementEntity.Builder.() -> Unit,
): ArtifactOutputPackagingElementEntity {
  return modifyEntity(ArtifactOutputPackagingElementEntity.Builder::class.java, entity, modification)
}

var ArtifactOutputPackagingElementEntity.Builder.artifactEntity: ArtifactEntity.Builder?
  by WorkspaceEntity.extensionBuilder(ArtifactEntity::class.java)
//endregion

val ArtifactOutputPackagingElementEntity.artifactEntity: ArtifactEntity?
  by WorkspaceEntity.extension()

interface ModuleOutputPackagingElementEntity : PackagingElementEntity {
  val module: ModuleId?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ModuleOutputPackagingElementEntity>,
                      PackagingElementEntity.Builder<ModuleOutputPackagingElementEntity> {
    override var entitySource: EntitySource
    override var parentEntity: CompositePackagingElementEntity.Builder<out CompositePackagingElementEntity>?
    var module: ModuleId?
  }

  companion object : EntityType<ModuleOutputPackagingElementEntity, Builder>(PackagingElementEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyModuleOutputPackagingElementEntity(
  entity: ModuleOutputPackagingElementEntity,
  modification: ModuleOutputPackagingElementEntity.Builder.() -> Unit,
): ModuleOutputPackagingElementEntity {
  return modifyEntity(ModuleOutputPackagingElementEntity.Builder::class.java, entity, modification)
}
//endregion

interface LibraryFilesPackagingElementEntity : PackagingElementEntity {
  val library: LibraryId?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<LibraryFilesPackagingElementEntity>,
                      PackagingElementEntity.Builder<LibraryFilesPackagingElementEntity> {
    override var entitySource: EntitySource
    override var parentEntity: CompositePackagingElementEntity.Builder<out CompositePackagingElementEntity>?
    var library: LibraryId?
  }

  companion object : EntityType<LibraryFilesPackagingElementEntity, Builder>(PackagingElementEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyLibraryFilesPackagingElementEntity(
  entity: LibraryFilesPackagingElementEntity,
  modification: LibraryFilesPackagingElementEntity.Builder.() -> Unit,
): LibraryFilesPackagingElementEntity {
  return modifyEntity(LibraryFilesPackagingElementEntity.Builder::class.java, entity, modification)
}
//endregion

interface ModuleSourcePackagingElementEntity : PackagingElementEntity {
  val module: ModuleId?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ModuleSourcePackagingElementEntity>,
                      PackagingElementEntity.Builder<ModuleSourcePackagingElementEntity> {
    override var entitySource: EntitySource
    override var parentEntity: CompositePackagingElementEntity.Builder<out CompositePackagingElementEntity>?
    var module: ModuleId?
  }

  companion object : EntityType<ModuleSourcePackagingElementEntity, Builder>(PackagingElementEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyModuleSourcePackagingElementEntity(
  entity: ModuleSourcePackagingElementEntity,
  modification: ModuleSourcePackagingElementEntity.Builder.() -> Unit,
): ModuleSourcePackagingElementEntity {
  return modifyEntity(ModuleSourcePackagingElementEntity.Builder::class.java, entity, modification)
}
//endregion

interface ModuleTestOutputPackagingElementEntity : PackagingElementEntity {
  val module: ModuleId?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ModuleTestOutputPackagingElementEntity>,
                      PackagingElementEntity.Builder<ModuleTestOutputPackagingElementEntity> {
    override var entitySource: EntitySource
    override var parentEntity: CompositePackagingElementEntity.Builder<out CompositePackagingElementEntity>?
    var module: ModuleId?
  }

  companion object : EntityType<ModuleTestOutputPackagingElementEntity, Builder>(PackagingElementEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyModuleTestOutputPackagingElementEntity(
  entity: ModuleTestOutputPackagingElementEntity,
  modification: ModuleTestOutputPackagingElementEntity.Builder.() -> Unit,
): ModuleTestOutputPackagingElementEntity {
  return modifyEntity(ModuleTestOutputPackagingElementEntity.Builder::class.java, entity, modification)
}
//endregion

@Abstract interface FileOrDirectoryPackagingElementEntity : PackagingElementEntity {
  val filePath: VirtualFileUrl

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder<T : FileOrDirectoryPackagingElementEntity> : WorkspaceEntity.Builder<T>, PackagingElementEntity.Builder<T> {
    override var entitySource: EntitySource
    override var parentEntity: CompositePackagingElementEntity.Builder<out CompositePackagingElementEntity>?
    var filePath: VirtualFileUrl
  }

  companion object :
    EntityType<FileOrDirectoryPackagingElementEntity, Builder<FileOrDirectoryPackagingElementEntity>>(PackagingElementEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      filePath: VirtualFileUrl,
      entitySource: EntitySource,
      init: (Builder<FileOrDirectoryPackagingElementEntity>.() -> Unit)? = null,
    ): Builder<FileOrDirectoryPackagingElementEntity> {
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
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<DirectoryCopyPackagingElementEntity>,
                      FileOrDirectoryPackagingElementEntity.Builder<DirectoryCopyPackagingElementEntity> {
    override var entitySource: EntitySource
    override var parentEntity: CompositePackagingElementEntity.Builder<out CompositePackagingElementEntity>?
    override var filePath: VirtualFileUrl
  }

  companion object : EntityType<DirectoryCopyPackagingElementEntity, Builder>(FileOrDirectoryPackagingElementEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      filePath: VirtualFileUrl,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
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
fun MutableEntityStorage.modifyDirectoryCopyPackagingElementEntity(
  entity: DirectoryCopyPackagingElementEntity,
  modification: DirectoryCopyPackagingElementEntity.Builder.() -> Unit,
): DirectoryCopyPackagingElementEntity {
  return modifyEntity(DirectoryCopyPackagingElementEntity.Builder::class.java, entity, modification)
}
//endregion

interface ExtractedDirectoryPackagingElementEntity: FileOrDirectoryPackagingElementEntity {
  val pathInArchive: @NlsSafe String

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ExtractedDirectoryPackagingElementEntity>,
                      FileOrDirectoryPackagingElementEntity.Builder<ExtractedDirectoryPackagingElementEntity> {
    override var entitySource: EntitySource
    override var parentEntity: CompositePackagingElementEntity.Builder<out CompositePackagingElementEntity>?
    override var filePath: VirtualFileUrl
    var pathInArchive: String
  }

  companion object : EntityType<ExtractedDirectoryPackagingElementEntity, Builder>(FileOrDirectoryPackagingElementEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      filePath: VirtualFileUrl,
      pathInArchive: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
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
fun MutableEntityStorage.modifyExtractedDirectoryPackagingElementEntity(
  entity: ExtractedDirectoryPackagingElementEntity,
  modification: ExtractedDirectoryPackagingElementEntity.Builder.() -> Unit,
): ExtractedDirectoryPackagingElementEntity {
  return modifyEntity(ExtractedDirectoryPackagingElementEntity.Builder::class.java, entity, modification)
}
//endregion

interface FileCopyPackagingElementEntity : FileOrDirectoryPackagingElementEntity {
  val renamedOutputFileName: @NlsSafe String?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<FileCopyPackagingElementEntity>,
                      FileOrDirectoryPackagingElementEntity.Builder<FileCopyPackagingElementEntity> {
    override var entitySource: EntitySource
    override var parentEntity: CompositePackagingElementEntity.Builder<out CompositePackagingElementEntity>?
    override var filePath: VirtualFileUrl
    var renamedOutputFileName: String?
  }

  companion object : EntityType<FileCopyPackagingElementEntity, Builder>(FileOrDirectoryPackagingElementEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      filePath: VirtualFileUrl,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
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
fun MutableEntityStorage.modifyFileCopyPackagingElementEntity(
  entity: FileCopyPackagingElementEntity,
  modification: FileCopyPackagingElementEntity.Builder.() -> Unit,
): FileCopyPackagingElementEntity {
  return modifyEntity(FileCopyPackagingElementEntity.Builder::class.java, entity, modification)
}
//endregion

interface CustomPackagingElementEntity : CompositePackagingElementEntity {
  val typeId: @NonNls String
  val propertiesXmlTag: @NonNls String

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<CustomPackagingElementEntity>,
                      CompositePackagingElementEntity.Builder<CustomPackagingElementEntity> {
    override var entitySource: EntitySource
    override var parentEntity: CompositePackagingElementEntity.Builder<out CompositePackagingElementEntity>?
    override var artifact: ArtifactEntity.Builder?
    override var children: List<PackagingElementEntity.Builder<out PackagingElementEntity>>
    var typeId: String
    var propertiesXmlTag: String
  }

  companion object : EntityType<CustomPackagingElementEntity, Builder>(CompositePackagingElementEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      typeId: String,
      propertiesXmlTag: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
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
fun MutableEntityStorage.modifyCustomPackagingElementEntity(
  entity: CustomPackagingElementEntity,
  modification: CustomPackagingElementEntity.Builder.() -> Unit,
): CustomPackagingElementEntity {
  return modifyEntity(CustomPackagingElementEntity.Builder::class.java, entity, modification)
}
//endregion

/**
 * This entity stores order of artifacts in ipr file. This is needed to ensure that artifact tags are saved in the same order to avoid
 * unnecessary modifications of ipr file.
 */
interface ArtifactsOrderEntity : WorkspaceEntity {
  val orderOfArtifacts: List<@NlsSafe String>

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ArtifactsOrderEntity> {
    override var entitySource: EntitySource
    var orderOfArtifacts: MutableList<String>
  }

  companion object : EntityType<ArtifactsOrderEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      orderOfArtifacts: List<String>,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
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
fun MutableEntityStorage.modifyArtifactsOrderEntity(
  entity: ArtifactsOrderEntity,
  modification: ArtifactsOrderEntity.Builder.() -> Unit,
): ArtifactsOrderEntity {
  return modifyEntity(ArtifactsOrderEntity.Builder::class.java, entity, modification)
}
//endregion
