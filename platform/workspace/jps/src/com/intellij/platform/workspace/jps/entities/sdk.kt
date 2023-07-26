// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.NonNls
import java.io.Serializable

interface SdkMainEntity: WorkspaceEntity {
  val name: String
  val type: String
  val version: String
  val homePath: VirtualFileUrl
  val roots: List<SdkRoot>
  val additionalData: String

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : SdkMainEntity, WorkspaceEntity.Builder<SdkMainEntity> {
    override var entitySource: EntitySource
    override var name: String
    override var type: String
    override var version: String
    override var homePath: VirtualFileUrl
    override var roots: MutableList<SdkRoot>
    override var additionalData: String
  }

  companion object : EntityType<SdkMainEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(name: String,
                        type: String,
                        version: String,
                        homePath: VirtualFileUrl,
                        roots: List<SdkRoot>,
                        additionalData: String,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): SdkMainEntity {
      val builder = builder()
      builder.name = name
      builder.type = type
      builder.version = version
      builder.homePath = homePath
      builder.roots = roots.toMutableWorkspaceList()
      builder.additionalData = additionalData
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: SdkMainEntity, modification: SdkMainEntity.Builder.() -> Unit) = modifyEntity(
  SdkMainEntity.Builder::class.java, entity, modification)
//endregion


data class SdkRoot(val url: VirtualFileUrl, val type: SdkRootTypeId) : Serializable

data class SdkRootTypeId(val name: @NonNls String) : Serializable