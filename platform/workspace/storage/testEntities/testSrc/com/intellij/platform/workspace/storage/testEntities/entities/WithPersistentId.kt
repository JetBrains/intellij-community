// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities


import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.annotations.Open
import com.intellij.platform.workspace.storage.annotations.Parent

data class OneSymbolicId(val name: String) : SymbolicEntityId<OneEntityWithSymbolicId> {
  override val presentableName: String
    get() = name

  override fun toString(): String {
    return name
  }
}

data class Container(val id: OneSymbolicId, val justData: String = "")
data class DeepContainer(val goDeep: List<Container>, val optionalId: OneSymbolicId?)
data class TooDeepContainer(val goDeeper: List<DeepContainer>)

@Open
sealed class SealedContainer {
  data class BigContainer(val id: OneSymbolicId) : SealedContainer()
  data class SmallContainer(val notId: OneSymbolicId) : SealedContainer()
  data class EmptyContainer(val data: String) : SealedContainer()
  data class ContainerContainer(val container: List<Container>) : SealedContainer()
}

@Open
sealed class DeepSealedOne {
  @Open
  sealed class DeepSealedTwo : DeepSealedOne() {
    @Open
    sealed class DeepSealedThree : DeepSealedTwo() {
      data class DeepSealedFour(val id: OneSymbolicId) : DeepSealedThree()
    }
  }
}

interface OneEntityWithSymbolicId : WorkspaceEntityWithSymbolicId {
  val myName: String

  override val symbolicId: OneSymbolicId
    get() {
      return OneSymbolicId(myName)
    }

}

interface EntityWithSoftLinks : WorkspaceEntity {
  val link: OneSymbolicId
  val manyLinks: List<OneSymbolicId>
  val optionalLink: OneSymbolicId?
  val inContainer: Container
  val inOptionalContainer: Container?
  val inContainerList: List<Container>
  val deepContainer: List<TooDeepContainer>

  val sealedContainer: SealedContainer
  val listSealedContainer: List<SealedContainer>

  val justProperty: String
  val justNullableProperty: String?
  val justListProperty: List<String>

  val deepSealedClass: DeepSealedOne

  val children: List<SoftLinkReferencedChild>

}

interface SoftLinkReferencedChild : WorkspaceEntity {
  @Parent
  val parentEntity: EntityWithSoftLinks

}
