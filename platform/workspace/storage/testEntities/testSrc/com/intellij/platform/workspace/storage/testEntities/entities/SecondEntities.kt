// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.MutableEntityStorage



interface SampleEntity2 : WorkspaceEntity {
  val data: String
  val boolData: Boolean
  val optionalData: String?

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : SampleEntity2, WorkspaceEntity.Builder<SampleEntity2> {
    override var entitySource: EntitySource
    override var data: String
    override var boolData: Boolean
    override var optionalData: String?
  }

  companion object : EntityType<SampleEntity2, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(data: String, boolData: Boolean, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): SampleEntity2 {
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
fun MutableEntityStorage.modifyEntity(entity: SampleEntity2, modification: SampleEntity2.Builder.() -> Unit) = modifyEntity(
  SampleEntity2.Builder::class.java, entity, modification)
//endregion

interface VFUEntity2 : WorkspaceEntity {
  val data: String
  val filePath: VirtualFileUrl?
  val directoryPath: VirtualFileUrl
  val notNullRoots: List<VirtualFileUrl>

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : VFUEntity2, WorkspaceEntity.Builder<VFUEntity2> {
    override var entitySource: EntitySource
    override var data: String
    override var filePath: VirtualFileUrl?
    override var directoryPath: VirtualFileUrl
    override var notNullRoots: MutableList<VirtualFileUrl>
  }

  companion object : EntityType<VFUEntity2, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(data: String,
                        directoryPath: VirtualFileUrl,
                        notNullRoots: List<VirtualFileUrl>,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): VFUEntity2 {
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
fun MutableEntityStorage.modifyEntity(entity: VFUEntity2, modification: VFUEntity2.Builder.() -> Unit) = modifyEntity(
  VFUEntity2.Builder::class.java, entity, modification)
//endregion
