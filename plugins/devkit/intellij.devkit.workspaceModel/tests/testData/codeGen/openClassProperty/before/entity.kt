package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.WorkspaceEntity

/**
 * com.intellij.platform.workspace.storage.testEntities.entities.ProjectModelTestEntity
 */
interface SimpleEntity : WorkspaceEntity {
  val info: String
  val descriptor: Descriptor
}

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