// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent


interface ProjectModelTestEntity : WorkspaceEntity {
  val info: String
  val descriptor: Descriptor

  @Parent
  val parentEntity: ProjectModelTestEntity?
  val childrenEntities: List<ProjectModelTestEntity>

  val contentRoot: ContentRootTestEntity?
}

@Parent
private val ContentRootTestEntity.projectModelTestEntity: ProjectModelTestEntity? by WorkspaceEntity.extension()


open class Descriptor(val data: String) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Descriptor

    return data == other.data
  }

  override fun hashCode(): Int {
    return data.hashCode()
  }
}
class DescriptorInstance(data: String) : Descriptor(data)