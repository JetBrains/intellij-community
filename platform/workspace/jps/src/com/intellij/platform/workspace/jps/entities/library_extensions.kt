// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls

/**
 * Describes custom [library properties][com.intellij.openapi.roots.libraries.LibraryProperties].
 */
@Internal
interface LibraryPropertiesEntity : WorkspaceEntity {
  val library: LibraryEntity
  val propertiesXmlTag: @NonNls String?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<LibraryPropertiesEntity> {
    override var entitySource: EntitySource
    var library: LibraryEntity.Builder
    var propertiesXmlTag: String?
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
fun MutableEntityStorage.modifyEntity(
  entity: LibraryPropertiesEntity,
  modification: LibraryPropertiesEntity.Builder.() -> Unit,
): LibraryPropertiesEntity {
  return modifyEntity(LibraryPropertiesEntity.Builder::class.java, entity, modification)
}
//endregion

@get:Internal
val LibraryEntity.libraryProperties: @Child LibraryPropertiesEntity?
  by WorkspaceEntity.extension()