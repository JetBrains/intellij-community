// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.workspace.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Parent
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

  val rootElement: CompositePackagingElementEntity?
  val customProperties: List<ArtifactPropertiesEntity>
  val artifactOutputPackagingElement: ArtifactOutputPackagingElementEntity?
  override val symbolicId: ArtifactId
    get() = ArtifactId(name)

  //region generated code
  @Deprecated(message = "Use ArtifactEntityBuilder instead")
  interface Builder : ArtifactEntityBuilder {
    @Deprecated(message = "Use new API instead")
    fun getRootElement(): CompositePackagingElementEntity.Builder<out CompositePackagingElementEntity>? =
      rootElement as CompositePackagingElementEntity.Builder<out CompositePackagingElementEntity>?

    @Deprecated(message = "Use new API instead")
    fun setRootElement(value: CompositePackagingElementEntity.Builder<out CompositePackagingElementEntity>?) {
      rootElement = value
    }

    @Deprecated(message = "Use new API instead")
    fun getArtifactOutputPackagingElement(): ArtifactOutputPackagingElementEntity.Builder? =
      artifactOutputPackagingElement as ArtifactOutputPackagingElementEntity.Builder?

    @Deprecated(message = "Use new API instead")
    fun setArtifactOutputPackagingElement(value: ArtifactOutputPackagingElementEntity.Builder?) {
      artifactOutputPackagingElement = value
    }
  }

  companion object : EntityType<ArtifactEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      name: String,
      artifactType: String,
      includeInProjectBuild: Boolean,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = ArtifactEntityType.compatibilityInvoke(name, artifactType, includeInProjectBuild, entitySource, init)
  }
  //endregion

}

//region generated code
@Deprecated(message = "Use new API instead")
fun MutableEntityStorage.modifyArtifactEntity(
  entity: ArtifactEntity,
  modification: ArtifactEntity.Builder.() -> Unit,
): ArtifactEntity {
  return modifyEntity(ArtifactEntity.Builder::class.java, entity, modification)
}
//endregion

interface ArtifactPropertiesEntity : WorkspaceEntity {
  @Parent
  val artifact: ArtifactEntity

  val providerType: @NonNls String
  val propertiesXmlTag: @NonNls String?

  //region generated code
  @Deprecated(message = "Use ArtifactPropertiesEntityBuilder instead")
  interface Builder : ArtifactPropertiesEntityBuilder {
    @Deprecated(message = "Use new API instead")
    fun getArtifact(): ArtifactEntity.Builder = artifact as ArtifactEntity.Builder

    @Deprecated(message = "Use new API instead")
    fun setArtifact(value: ArtifactEntity.Builder) {
      artifact = value
    }
  }

  companion object : EntityType<ArtifactPropertiesEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      providerType: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = ArtifactPropertiesEntityType.compatibilityInvoke(providerType, entitySource, init)
  }
  //endregion

}

//region generated code
@Deprecated(message = "Use new API instead")
fun MutableEntityStorage.modifyArtifactPropertiesEntity(
  entity: ArtifactPropertiesEntity,
  modification: ArtifactPropertiesEntity.Builder.() -> Unit,
): ArtifactPropertiesEntity {
  return modifyEntity(ArtifactPropertiesEntity.Builder::class.java, entity, modification)
}
//endregion

@Abstract interface PackagingElementEntity : WorkspaceEntity {
  @Parent
  val parentEntity: CompositePackagingElementEntity?

  //region generated code
  @Deprecated(message = "Use PackagingElementEntityBuilder instead")
  interface Builder<T : PackagingElementEntity> : PackagingElementEntityBuilder<T> {
    @Deprecated(message = "Use new API instead")
    fun getParentEntity(): CompositePackagingElementEntity.Builder<out CompositePackagingElementEntity>? =
      parentEntity as CompositePackagingElementEntity.Builder<out CompositePackagingElementEntity>?

    @Deprecated(message = "Use new API instead")
    fun setParentEntity(value: CompositePackagingElementEntity.Builder<out CompositePackagingElementEntity>?) {
      parentEntity = value
    }
  }

  companion object : EntityType<PackagingElementEntity, Builder<PackagingElementEntity>>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      entitySource: EntitySource,
      init: (Builder<PackagingElementEntity>.() -> Unit)? = null,
    ): Builder<PackagingElementEntity> = PackagingElementEntityType.compatibilityInvoke(entitySource, init)
  }
  //endregion

}

@Abstract interface CompositePackagingElementEntity : PackagingElementEntity {
  @Parent
  val artifact: ArtifactEntity?

  val children: List<PackagingElementEntity>

  //region generated code
  @Deprecated(message = "Use CompositePackagingElementEntityBuilder instead")
  interface Builder<T : CompositePackagingElementEntity> : CompositePackagingElementEntityBuilder<T> {
    @Deprecated(message = "Use new API instead")
    fun getArtifact(): ArtifactEntity.Builder? = artifact as ArtifactEntity.Builder?

    @Deprecated(message = "Use new API instead")
    fun setArtifact(value: ArtifactEntity.Builder?) {
      artifact = value
    }
  }

  companion object : EntityType<CompositePackagingElementEntity, Builder<CompositePackagingElementEntity>>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      entitySource: EntitySource,
      init: (Builder<CompositePackagingElementEntity>.() -> Unit)? = null,
    ): Builder<CompositePackagingElementEntity> = CompositePackagingElementEntityType.compatibilityInvoke(entitySource, init)
  }
  //endregion

}

interface DirectoryPackagingElementEntity: CompositePackagingElementEntity {
  val directoryName: @NlsSafe String

  //region generated code
  @Deprecated(message = "Use DirectoryPackagingElementEntityBuilder instead")
  interface Builder : DirectoryPackagingElementEntityBuilder
  companion object : EntityType<DirectoryPackagingElementEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      directoryName: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = DirectoryPackagingElementEntityType.compatibilityInvoke(directoryName, entitySource, init)
  }
  //endregion

}

//region generated code
@Deprecated(message = "Use new API instead")
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
  @Deprecated(message = "Use ArchivePackagingElementEntityBuilder instead")
  interface Builder : ArchivePackagingElementEntityBuilder
  companion object : EntityType<ArchivePackagingElementEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      fileName: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = ArchivePackagingElementEntityType.compatibilityInvoke(fileName, entitySource, init)
  }
  //endregion

}

//region generated code
@Deprecated(message = "Use new API instead")
fun MutableEntityStorage.modifyArchivePackagingElementEntity(
  entity: ArchivePackagingElementEntity,
  modification: ArchivePackagingElementEntity.Builder.() -> Unit,
): ArchivePackagingElementEntity {
  return modifyEntity(ArchivePackagingElementEntity.Builder::class.java, entity, modification)
}
//endregion

interface ArtifactRootElementEntity: CompositePackagingElementEntity {
  //region generated code
  @Deprecated(message = "Use ArtifactRootElementEntityBuilder instead")
  interface Builder : ArtifactRootElementEntityBuilder
  companion object : EntityType<ArtifactRootElementEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = ArtifactRootElementEntityType.compatibilityInvoke(entitySource, init)
  }
  //endregion

}

//region generated code
@Deprecated(message = "Use new API instead")
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
  @Deprecated(message = "Use ArtifactOutputPackagingElementEntityBuilder instead")
  interface Builder : ArtifactOutputPackagingElementEntityBuilder
  companion object : EntityType<ArtifactOutputPackagingElementEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = ArtifactOutputPackagingElementEntityType.compatibilityInvoke(entitySource, init)
  }
  //endregion

}

//region generated code
@Deprecated(message = "Use new API instead")
fun MutableEntityStorage.modifyArtifactOutputPackagingElementEntity(
  entity: ArtifactOutputPackagingElementEntity,
  modification: ArtifactOutputPackagingElementEntity.Builder.() -> Unit,
): ArtifactOutputPackagingElementEntity {
  return modifyEntity(ArtifactOutputPackagingElementEntity.Builder::class.java, entity, modification)
}

@Deprecated(message = "Use new API instead")
@Parent
var ArtifactOutputPackagingElementEntity.Builder.artifactEntity: ArtifactEntity.Builder?
  get() = (this as ArtifactOutputPackagingElementEntityBuilder).artifactEntity as ArtifactEntity.Builder?
  set(value) {
    (this as ArtifactOutputPackagingElementEntityBuilder).artifactEntity = value
  }
//endregion

@Parent
val ArtifactOutputPackagingElementEntity.artifactEntity: ArtifactEntity?
  by WorkspaceEntity.extension()

interface ModuleOutputPackagingElementEntity : PackagingElementEntity {
  val module: ModuleId?

  //region generated code
  @Deprecated(message = "Use ModuleOutputPackagingElementEntityBuilder instead")
  interface Builder : ModuleOutputPackagingElementEntityBuilder
  companion object : EntityType<ModuleOutputPackagingElementEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = ModuleOutputPackagingElementEntityType.compatibilityInvoke(entitySource, init)
  }
  //endregion

}

//region generated code
@Deprecated(message = "Use new API instead")
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
  @Deprecated(message = "Use LibraryFilesPackagingElementEntityBuilder instead")
  interface Builder : LibraryFilesPackagingElementEntityBuilder
  companion object : EntityType<LibraryFilesPackagingElementEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = LibraryFilesPackagingElementEntityType.compatibilityInvoke(entitySource, init)
  }
  //endregion

}

//region generated code
@Deprecated(message = "Use new API instead")
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
  @Deprecated(message = "Use ModuleSourcePackagingElementEntityBuilder instead")
  interface Builder : ModuleSourcePackagingElementEntityBuilder
  companion object : EntityType<ModuleSourcePackagingElementEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = ModuleSourcePackagingElementEntityType.compatibilityInvoke(entitySource, init)
  }
  //endregion

}

//region generated code
@Deprecated(message = "Use new API instead")
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
  @Deprecated(message = "Use ModuleTestOutputPackagingElementEntityBuilder instead")
  interface Builder : ModuleTestOutputPackagingElementEntityBuilder
  companion object : EntityType<ModuleTestOutputPackagingElementEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = ModuleTestOutputPackagingElementEntityType.compatibilityInvoke(entitySource, init)
  }
  //endregion

}

//region generated code
@Deprecated(message = "Use new API instead")
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
  @Deprecated(message = "Use FileOrDirectoryPackagingElementEntityBuilder instead")
  interface Builder<T : FileOrDirectoryPackagingElementEntity> : FileOrDirectoryPackagingElementEntityBuilder<T>
  companion object : EntityType<FileOrDirectoryPackagingElementEntity, Builder<FileOrDirectoryPackagingElementEntity>>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      filePath: VirtualFileUrl,
      entitySource: EntitySource,
      init: (Builder<FileOrDirectoryPackagingElementEntity>.() -> Unit)? = null,
    ): Builder<FileOrDirectoryPackagingElementEntity> =
      FileOrDirectoryPackagingElementEntityType.compatibilityInvoke(filePath, entitySource, init)
  }
  //endregion

}

interface DirectoryCopyPackagingElementEntity : FileOrDirectoryPackagingElementEntity {
  //region generated code
  @Deprecated(message = "Use DirectoryCopyPackagingElementEntityBuilder instead")
  interface Builder : DirectoryCopyPackagingElementEntityBuilder
  companion object : EntityType<DirectoryCopyPackagingElementEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      filePath: VirtualFileUrl,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = DirectoryCopyPackagingElementEntityType.compatibilityInvoke(filePath, entitySource, init)
  }
  //endregion

}

//region generated code
@Deprecated(message = "Use new API instead")
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
  @Deprecated(message = "Use ExtractedDirectoryPackagingElementEntityBuilder instead")
  interface Builder : ExtractedDirectoryPackagingElementEntityBuilder
  companion object : EntityType<ExtractedDirectoryPackagingElementEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      filePath: VirtualFileUrl,
      pathInArchive: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = ExtractedDirectoryPackagingElementEntityType.compatibilityInvoke(filePath, pathInArchive, entitySource, init)
  }
  //endregion

}

//region generated code
@Deprecated(message = "Use new API instead")
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
  @Deprecated(message = "Use FileCopyPackagingElementEntityBuilder instead")
  interface Builder : FileCopyPackagingElementEntityBuilder
  companion object : EntityType<FileCopyPackagingElementEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      filePath: VirtualFileUrl,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = FileCopyPackagingElementEntityType.compatibilityInvoke(filePath, entitySource, init)
  }
  //endregion

}

//region generated code
@Deprecated(message = "Use new API instead")
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
  @Deprecated(message = "Use CustomPackagingElementEntityBuilder instead")
  interface Builder : CustomPackagingElementEntityBuilder
  companion object : EntityType<CustomPackagingElementEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      typeId: String,
      propertiesXmlTag: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = CustomPackagingElementEntityType.compatibilityInvoke(typeId, propertiesXmlTag, entitySource, init)
  }
  //endregion

}

//region generated code
@Deprecated(message = "Use new API instead")
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
  @Deprecated(message = "Use ArtifactsOrderEntityBuilder instead")
  interface Builder : ArtifactsOrderEntityBuilder
  companion object : EntityType<ArtifactsOrderEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      orderOfArtifacts: List<String>,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = ArtifactsOrderEntityType.compatibilityInvoke(orderOfArtifacts, entitySource, init)
  }
  //endregion

}

//region generated code
@Deprecated(message = "Use new API instead")
fun MutableEntityStorage.modifyArtifactsOrderEntity(
  entity: ArtifactsOrderEntity,
  modification: ArtifactsOrderEntity.Builder.() -> Unit,
): ArtifactsOrderEntity {
  return modifyEntity(ArtifactsOrderEntity.Builder::class.java, entity, modification)
}
//endregion
