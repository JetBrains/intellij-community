// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
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
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<LibraryPropertiesEntity> {
    override var entitySource: EntitySource
    var propertiesXmlTag: String?
    var library: LibraryEntity.Builder
  }

  companion object : EntityType<LibraryPropertiesEntity, Builder>() {
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