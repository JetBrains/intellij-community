// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl


interface SampleEntity2 : WorkspaceEntity {
  val data: String
  val boolData: Boolean
  val optionalData: String?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<SampleEntity2> {
    override var entitySource: EntitySource
    var data: String
    var boolData: Boolean
    var optionalData: String?
  }

  companion object : EntityType<SampleEntity2, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      data: String,
      boolData: Boolean,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.data = data
      builder.boolData = boolData
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifySampleEntity2(
  entity: SampleEntity2,
  modification: SampleEntity2.Builder.() -> Unit,
): SampleEntity2 {
  return modifyEntity(SampleEntity2.Builder::class.java, entity, modification)
}
//endregion

interface VFUEntity2 : WorkspaceEntity {
  val data: String
  val filePath: VirtualFileUrl?
  val directoryPath: VirtualFileUrl
  val notNullRoots: List<VirtualFileUrl>

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<VFUEntity2> {
    override var entitySource: EntitySource
    var data: String
    var filePath: VirtualFileUrl?
    var directoryPath: VirtualFileUrl
    var notNullRoots: MutableList<VirtualFileUrl>
  }

  companion object : EntityType<VFUEntity2, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      data: String,
      directoryPath: VirtualFileUrl,
      notNullRoots: List<VirtualFileUrl>,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.data = data
      builder.directoryPath = directoryPath
      builder.notNullRoots = notNullRoots.toMutableWorkspaceList()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyVFUEntity2(
  entity: VFUEntity2,
  modification: VFUEntity2.Builder.() -> Unit,
): VFUEntity2 {
  return modifyEntity(VFUEntity2.Builder::class.java, entity, modification)
}
//endregion
