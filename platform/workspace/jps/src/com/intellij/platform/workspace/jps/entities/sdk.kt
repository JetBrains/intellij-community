// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
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
  @Deprecated(message = "Use SdkEntityBuilder instead")
  interface Builder : SdkEntityBuilder
  companion object : EntityType<SdkEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
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
    ): Builder = SdkEntityType.compatibilityInvoke(name, type, roots, additionalData, entitySource, init)
  }
  //endregion
}

//region generated code
@Deprecated(message = "Use new API instead")
fun MutableEntityStorage.modifySdkEntity(
  entity: SdkEntity,
  modification: SdkEntity.Builder.() -> Unit,
): SdkEntity {
  return modifyEntity(SdkEntity.Builder::class.java, entity, modification)
}
//endregion


data class SdkRoot(val url: VirtualFileUrl, val type: SdkRootTypeId) : Serializable

data class SdkRootTypeId(val name: @NonNls String) : Serializable