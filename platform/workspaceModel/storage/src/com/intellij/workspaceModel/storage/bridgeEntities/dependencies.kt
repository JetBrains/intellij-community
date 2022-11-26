// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.bridgeEntities

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child
import java.io.Serializable

interface LibraryEntity : WorkspaceEntityWithSymbolicId {
    val name: String
    val tableId: LibraryTableId

    val roots: List<LibraryRoot>
    val excludedRoots: List<@Child ExcludeUrlEntity>
    @Child val sdk: SdkEntity?
    @Child val libraryProperties: LibraryPropertiesEntity?
    @Child val libraryFilesPackagingElement: LibraryFilesPackagingElementEntity?

    override val symbolicId: LibraryId
        get() = LibraryId(name, tableId)

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : LibraryEntity, WorkspaceEntity.Builder<LibraryEntity>, ObjBuilder<LibraryEntity> {
    override var entitySource: EntitySource
    override var name: String
    override var tableId: LibraryTableId
    override var roots: MutableList<LibraryRoot>
    override var excludedRoots: List<ExcludeUrlEntity>
    override var sdk: SdkEntity?
    override var libraryProperties: LibraryPropertiesEntity?
    override var libraryFilesPackagingElement: LibraryFilesPackagingElementEntity?
  }

  companion object : Type<LibraryEntity, Builder>() {
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

var LibraryEntity.Builder.externalSystemId: @Child LibraryExternalSystemIdEntity?
  by WorkspaceEntity.extension()
//endregion

val ExcludeUrlEntity.library: LibraryEntity? by WorkspaceEntity.extension()

interface LibraryPropertiesEntity : WorkspaceEntity {
    val library: LibraryEntity

    val libraryType: String
    val propertiesXmlTag: String?

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : LibraryPropertiesEntity, WorkspaceEntity.Builder<LibraryPropertiesEntity>, ObjBuilder<LibraryPropertiesEntity> {
    override var entitySource: EntitySource
    override var library: LibraryEntity
    override var libraryType: String
    override var propertiesXmlTag: String?
  }

  companion object : Type<LibraryPropertiesEntity, Builder>() {
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

interface SdkEntity : WorkspaceEntity {
    val library: LibraryEntity

    val homeUrl: VirtualFileUrl

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : SdkEntity, WorkspaceEntity.Builder<SdkEntity>, ObjBuilder<SdkEntity> {
    override var entitySource: EntitySource
    override var library: LibraryEntity
    override var homeUrl: VirtualFileUrl
  }

  companion object : Type<SdkEntity, Builder>() {
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

