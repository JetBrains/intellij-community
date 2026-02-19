// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import java.util.UUID


interface SampleEntity : WorkspaceEntity {
  val booleanProperty: Boolean
  val stringProperty: String
  val stringListProperty: List<String>
  val stringMapProperty: Map<String, String>
  val fileProperty: VirtualFileUrl
  val children: List<ChildSampleEntity>
  val nullableData: String?
  val randomUUID: UUID?

}

interface ChildSampleEntity : WorkspaceEntity {
  val data: String
  @Parent
  val parentEntity: SampleEntity?

}

abstract class MyData(val myData: MyContainer)

class MyConcreteImpl(myData: MyContainer) : MyData(myData) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is MyConcreteImpl) return false
    return this.myData == other.myData
  }

  override fun hashCode(): Int {
    return this.myData.hashCode()
  }
}

data class MyContainer(val info: String)

interface SecondSampleEntity : WorkspaceEntity {
  val intProperty: Int

}

interface SourceEntity : WorkspaceEntity {
  val data: String
  val children: List<ChildSourceEntity>

}

interface ChildSourceEntity : WorkspaceEntity {
  val data: String
  @Parent
  val parentEntity: SourceEntity

}

interface SymbolicIdEntity : WorkspaceEntityWithSymbolicId {
  val data: String
  override val symbolicId: LinkedListEntityId
    get() {
      return LinkedListEntityId(data)
    }

}
