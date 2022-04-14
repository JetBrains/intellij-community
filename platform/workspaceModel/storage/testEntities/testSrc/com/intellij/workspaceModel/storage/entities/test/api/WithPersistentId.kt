package com.intellij.workspaceModel.storage.entities.test.api


import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.PersistentEntityId
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityWithPersistentId

import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.annotations.Open
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.fields.*
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity

import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child


data class OnePersistentId(val name: String): PersistentEntityId<OneEntityWithPersistentId> {
    override val presentableName: String
        get() = name

    override fun toString(): String {
        return name
    }
}

data class Container(val id: OnePersistentId, val justData: String = "")
data class DeepContainer(val goDeep: List<Container>, val optionalId: OnePersistentId?)
data class TooDeepContainer(val goDeeper: List<DeepContainer>)

@Open
sealed class SealedContainer {
    data class BigContainer(val id: OnePersistentId) : SealedContainer()
    data class SmallContainer(val notId: OnePersistentId) : SealedContainer()
    data class EmptyContainer(val data: String) : SealedContainer()
    data class ContainerContainer(val container: List<Container>) : SealedContainer()
}

@Open
sealed class DeepSealedOne {
  @Open
  sealed class DeepSealedTwo : DeepSealedOne() {
    @Open
    sealed class DeepSealedThree : DeepSealedTwo() {
      data class DeepSealedFour(val id: OnePersistentId): DeepSealedThree()
    }
  }
}

interface OneEntityWithPersistentId : WorkspaceEntityWithPersistentId {
    val myName: String

    override val persistentId: OnePersistentId
        get() {
            return OnePersistentId(myName)
        }


    //region generated code
    //@formatter:off
    interface Builder: OneEntityWithPersistentId, ModifiableWorkspaceEntity<OneEntityWithPersistentId>, ObjBuilder<OneEntityWithPersistentId> {
        override var myName: String
        override var entitySource: EntitySource
    }
    
    companion object: Type<OneEntityWithPersistentId, Builder>(34)
    //@formatter:on
    //endregion

}

interface EntityWithSoftLinks : WorkspaceEntity {
    val link: OnePersistentId
    val manyLinks: List<OnePersistentId>
    val optionalLink: OnePersistentId?
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

    val children: List<@Child SoftLinkReferencedChild>

    //region generated code
    //@formatter:off
    interface Builder: EntityWithSoftLinks, ModifiableWorkspaceEntity<EntityWithSoftLinks>, ObjBuilder<EntityWithSoftLinks> {
        override var link: OnePersistentId
        override var entitySource: EntitySource
        override var manyLinks: List<OnePersistentId>
        override var optionalLink: OnePersistentId?
        override var inContainer: Container
        override var inOptionalContainer: Container?
        override var inContainerList: List<Container>
        override var deepContainer: List<TooDeepContainer>
        override var sealedContainer: SealedContainer
        override var listSealedContainer: List<SealedContainer>
        override var justProperty: String
        override var justNullableProperty: String?
        override var justListProperty: List<String>
        override var deepSealedClass: DeepSealedOne
        override var children: List<SoftLinkReferencedChild>
    }
    
    companion object: Type<EntityWithSoftLinks, Builder>(35)
    //@formatter:on
    //endregion

}

interface SoftLinkReferencedChild : WorkspaceEntity {
  val parentEntity: EntityWithSoftLinks
  //region generated code
  //@formatter:off
  interface Builder: SoftLinkReferencedChild, ModifiableWorkspaceEntity<SoftLinkReferencedChild>, ObjBuilder<SoftLinkReferencedChild> {
      override var parentEntity: EntityWithSoftLinks
      override var entitySource: EntitySource
  }
  
  companion object: Type<SoftLinkReferencedChild, Builder>(36)
  //@formatter:on
  //endregion

}
