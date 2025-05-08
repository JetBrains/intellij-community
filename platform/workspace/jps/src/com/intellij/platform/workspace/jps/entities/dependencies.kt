// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import java.io.Serializable
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls

data class LibraryTypeId(val name: @NonNls String)
/**
 * Describes a [Library][com.intellij.openapi.roots.libraries.Library].
 * See [package documentation](psi_element://com.intellij.platform.workspace.jps.entities) for more details.
 */
interface LibraryEntity : WorkspaceEntityWithSymbolicId {
    val name: @NlsSafe String
    val tableId: LibraryTableId
    val typeId: LibraryTypeId?
    val roots: List<LibraryRoot>

    val excludedRoots: List<@Child ExcludeUrlEntity>

    override val symbolicId: LibraryId
        get() = LibraryId(name, tableId)

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<LibraryEntity> {
    override var entitySource: EntitySource
    var name: String
    var tableId: LibraryTableId
    var typeId: LibraryTypeId?
    var roots: MutableList<LibraryRoot>
    var excludedRoots: List<ExcludeUrlEntity.Builder>
  }

  companion object : EntityType<LibraryEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      name: String,
      tableId: LibraryTableId,
      roots: List<LibraryRoot>,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
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
fun MutableEntityStorage.modifyLibraryEntity(
  entity: LibraryEntity,
  modification: LibraryEntity.Builder.() -> Unit,
): LibraryEntity {
  return modifyEntity(LibraryEntity.Builder::class.java, entity, modification)
}

@get:Internal
@set:Internal
var LibraryEntity.Builder.libraryProperties: @Child LibraryPropertiesEntity.Builder?
  by WorkspaceEntity.extensionBuilder(LibraryPropertiesEntity::class.java)
//endregion

val ExcludeUrlEntity.library: LibraryEntity? by WorkspaceEntity.extension()


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