// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

/**
 * Describes custom [library properties][com.intellij.openapi.roots.libraries.LibraryProperties].
 */
@ApiStatus.Internal
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
    operator fun invoke(
      libraryType: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): LibraryPropertiesEntity {
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
fun MutableEntityStorage.modifyEntity(
  entity: LibraryPropertiesEntity,
  modification: LibraryPropertiesEntity.Builder.() -> Unit,
): LibraryPropertiesEntity {
  return modifyEntity(LibraryPropertiesEntity.Builder::class.java, entity, modification)
}
//endregion

@get:ApiStatus.Internal
val LibraryEntity.libraryProperties: @Child LibraryPropertiesEntity?
  by WorkspaceEntity.extension()