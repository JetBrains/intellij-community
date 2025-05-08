// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import java.io.Serializable
import org.jetbrains.annotations.NonNls

interface SdkEntity : WorkspaceEntityWithSymbolicId {
  override val symbolicId: SdkId
    get() = SdkId(name, type)

  val name: String
  val type: String
  val version: String?
  val homePath: VirtualFileUrl?
  val roots: List<SdkRoot>
  val additionalData: String

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<SdkEntity> {
    override var entitySource: EntitySource
    var name: String
    var type: String
    var version: String?
    var homePath: VirtualFileUrl?
    var roots: MutableList<SdkRoot>
    var additionalData: String
  }

  companion object : EntityType<SdkEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      name: String,
      type: String,
      roots: List<SdkRoot>,
      additionalData: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.name = name
      builder.type = type
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
fun MutableEntityStorage.modifySdkEntity(
  entity: SdkEntity,
  modification: SdkEntity.Builder.() -> Unit,
): SdkEntity {
  return modifyEntity(SdkEntity.Builder::class.java, entity, modification)
}
//endregion


data class SdkRoot(val url: VirtualFileUrl, val type: SdkRootTypeId) : Serializable

data class SdkRootTypeId(val name: @NonNls String) : Serializable