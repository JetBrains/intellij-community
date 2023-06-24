// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.NonNls
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.annotations.Child
import java.io.Serializable

interface LibraryEntity : WorkspaceEntityWithSymbolicId {
    val name: @NlsSafe String
    val tableId: LibraryTableId

    val roots: List<LibraryRoot>
    val excludedRoots: List<@Child ExcludeUrlEntity>
    @Child val sdk: SdkEntity?
    @Child val libraryProperties: LibraryPropertiesEntity?

    override val symbolicId: LibraryId
        get() = LibraryId(name, tableId)

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : LibraryEntity, WorkspaceEntity.Builder<LibraryEntity> {
    override var entitySource: EntitySource
    override var name: String
    override var tableId: LibraryTableId
    override var roots: MutableList<LibraryRoot>
    override var excludedRoots: List<ExcludeUrlEntity>
    override var sdk: SdkEntity?
    override var libraryProperties: LibraryPropertiesEntity?
  }

  companion object : EntityType<LibraryEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(name: String,
                        tableId: LibraryTableId,
                        roots: List<LibraryRoot>,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): LibraryEntity {
      val builder = builder()
      builder.name = name
      builder.tableId = tableId
      builder.roots = roots.toMutableWorkspaceList()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: LibraryEntity, modification: LibraryEntity.Builder.() -> Unit) = modifyEntity(
  LibraryEntity.Builder::class.java, entity, modification)
//endregion

val ExcludeUrlEntity.library: LibraryEntity? by WorkspaceEntity.extension()

interface LibraryPropertiesEntity : WorkspaceEntity {
    val library: LibraryEntity

    val libraryType: @NonNls String
    val propertiesXmlTag: @NonNls String?

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : LibraryPropertiesEntity, WorkspaceEntity.Builder<LibraryPropertiesEntity> {
    override var entitySource: EntitySource
    override var library: LibraryEntity
    override var libraryType: String
    override var propertiesXmlTag: String?
  }

  companion object : EntityType<LibraryPropertiesEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(libraryType: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): LibraryPropertiesEntity {
      val builder = builder()
      builder.libraryType = libraryType
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: LibraryPropertiesEntity,
                                      modification: LibraryPropertiesEntity.Builder.() -> Unit) = modifyEntity(
  LibraryPropertiesEntity.Builder::class.java, entity, modification)
//endregion

/**
 * Will represent an SDK in the project configuration.
 * Currently, these entities aren't synchronized with the project model, so until IDEA-306969 is implemented, it makes no sense to use them.
 */
interface SdkEntity : WorkspaceEntity {
    val library: LibraryEntity

    val homeUrl: VirtualFileUrl

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : SdkEntity, WorkspaceEntity.Builder<SdkEntity> {
    override var entitySource: EntitySource
    override var library: LibraryEntity
    override var homeUrl: VirtualFileUrl
  }

  companion object : EntityType<SdkEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(homeUrl: VirtualFileUrl, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): SdkEntity {
      val builder = builder()
      builder.homeUrl = homeUrl
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: SdkEntity, modification: SdkEntity.Builder.() -> Unit) = modifyEntity(
  SdkEntity.Builder::class.java, entity, modification)
//endregion

data class LibraryRootTypeId(val name: @NonNls String) : Serializable {
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

