// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.deft.api.annotations.Ignore
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type



interface IgnoredFieldsEntity: WorkspaceEntity {
  val descriptor: AnotherDataClass
  val description: String get() = "Default description"
  val anotherVersion: Int get() = 0

  @Ignore val version: Int get() = descriptor.version
  @Ignore val source: Boolean get() = descriptor.source
  @Ignore val displayName: String? get() = descriptor.displayName
  @Ignore val gitUrl: String? get() = descriptor.url
  @Ignore val gitRevision: String?  get() = descriptor.revision

  fun isEditable(): Boolean {
    return descriptor.source && displayName != null && displayName == "AnotherData"
  }
  fun isReadOnly(): Boolean {
    return !isEditable() && descriptor.url != null
  }
  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: IgnoredFieldsEntity, ModifiableWorkspaceEntity<IgnoredFieldsEntity>, ObjBuilder<IgnoredFieldsEntity> {
      override var descriptor: AnotherDataClass
      override var entitySource: EntitySource
      override var description: String
      override var anotherVersion: Int
  }
  
  companion object: Type<IgnoredFieldsEntity, Builder>() {
      operator fun invoke(descriptor: AnotherDataClass, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): IgnoredFieldsEntity {
          val builder = builder()
          builder.descriptor = descriptor
          builder.entitySource = entitySource
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: IgnoredFieldsEntity, modification: IgnoredFieldsEntity.Builder.() -> Unit) = modifyEntity(IgnoredFieldsEntity.Builder::class.java, entity, modification)
//endregion


data class AnotherDataClass(val name: String, val version: Int, val source: Boolean, val displayName: String?, val url: String?, val revision: String?)