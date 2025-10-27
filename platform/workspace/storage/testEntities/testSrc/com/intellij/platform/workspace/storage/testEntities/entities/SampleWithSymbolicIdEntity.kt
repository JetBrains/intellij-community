// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl


interface SampleWithSymbolicIdEntity : WorkspaceEntityWithSymbolicId {
  val booleanProperty: Boolean
  val stringProperty: String
  val stringListProperty: List<String>
  val stringMapProperty: Map<String, String>
  val fileProperty: VirtualFileUrl
  val children: List<ChildWpidSampleEntity>
  val nullableData: String?

  override val symbolicId: SampleSymbolicId
    get() = SampleSymbolicId(stringProperty)

}

data class SampleSymbolicId(val stringProperty: String) : SymbolicEntityId<SampleWithSymbolicIdEntity> {
  override val presentableName: String
    get() = stringProperty
}

interface ChildWpidSampleEntity : WorkspaceEntity {
  val data: String
  @Parent
  val parentEntity: SampleWithSymbolicIdEntity?

}
