// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls

/**
 * Describes custom [library properties][com.intellij.openapi.roots.libraries.LibraryProperties].
 */
@Internal
interface LibraryPropertiesEntity : WorkspaceEntity {
  val propertiesXmlTag: @NonNls String?

  @Parent
  val library: LibraryEntity

  //region generated code
  @Deprecated(message = "Use LibraryPropertiesEntityBuilder instead")
  interface Builder : LibraryPropertiesEntityBuilder {
    @Deprecated(message = "Use new API instead")
    fun getLibrary(): LibraryEntity.Builder = library as LibraryEntity.Builder

    @Deprecated(message = "Use new API instead")
    fun setLibrary(value: LibraryEntity.Builder) {
      library = value
    }
  }

  companion object : EntityType<LibraryPropertiesEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = LibraryPropertiesEntityType.compatibilityInvoke(entitySource, init)
  }
  //endregion

}

//region generated code
@Deprecated(message = "Use new API instead")
@Internal
fun MutableEntityStorage.modifyLibraryPropertiesEntity(
  entity: LibraryPropertiesEntity,
  modification: LibraryPropertiesEntity.Builder.() -> Unit,
): LibraryPropertiesEntity {
  return modifyEntity(LibraryPropertiesEntity.Builder::class.java, entity, modification)
}
//endregion

@get:Internal
val LibraryEntity.libraryProperties: LibraryPropertiesEntity?
  by WorkspaceEntity.extension()