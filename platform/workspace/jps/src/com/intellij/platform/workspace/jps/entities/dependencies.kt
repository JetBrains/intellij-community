// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import java.io.Serializable

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

    val excludedRoots: List<ExcludeUrlEntity>

    override val symbolicId: LibraryId
        get() = LibraryId(name, tableId)

  //region generated code
  @Deprecated(message = "Use LibraryEntityBuilder instead")
  interface Builder : LibraryEntityBuilder
  companion object : EntityType<LibraryEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      name: String,
      tableId: LibraryTableId,
      roots: List<LibraryRoot>,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = LibraryEntityType.compatibilityInvoke(name, tableId, roots, entitySource, init)
  }
  //endregion

}

//region generated code
@Deprecated(message = "Use new API instead")
fun MutableEntityStorage.modifyLibraryEntity(
  entity: LibraryEntity,
  modification: LibraryEntity.Builder.() -> Unit,
): LibraryEntity {
  return modifyEntity(LibraryEntity.Builder::class.java, entity, modification)
}

@get:Internal
@set:Internal
@Deprecated(message = "Use new API instead")
var LibraryEntity.Builder.libraryProperties: LibraryPropertiesEntity.Builder?
  get() = (this as LibraryEntityBuilder).libraryProperties as LibraryPropertiesEntity.Builder?
  set(value) {
    (this as LibraryEntityBuilder).libraryProperties = value
  }
//endregion

@Parent
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