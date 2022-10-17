package com.intellij.workspaceModel.storage.entities.test.api


import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.annotations.Open
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion

import com.intellij.workspaceModel.storage.MutableEntityStorage




data class OnePersistentId(val name: String) : PersistentEntityId<OneEntityWithPersistentId> {
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
      data class DeepSealedFour(val id: OnePersistentId) : DeepSealedThree()
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
  @GeneratedCodeApiVersion(1)
  interface Builder : OneEntityWithPersistentId, WorkspaceEntity.Builder<OneEntityWithPersistentId>, ObjBuilder<OneEntityWithPersistentId> {
    override var entitySource: EntitySource
    override var myName: String
  }

  companion object : Type<OneEntityWithPersistentId, Builder>() {
    operator fun invoke(myName: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): OneEntityWithPersistentId {
      val builder = builder()
      builder.myName = myName
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: OneEntityWithPersistentId,
                                      modification: OneEntityWithPersistentId.Builder.() -> Unit) = modifyEntity(
  OneEntityWithPersistentId.Builder::class.java, entity, modification)
//endregion

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
  @GeneratedCodeApiVersion(1)
  interface Builder : EntityWithSoftLinks, WorkspaceEntity.Builder<EntityWithSoftLinks>, ObjBuilder<EntityWithSoftLinks> {
    override var entitySource: EntitySource
    override var link: OnePersistentId
    override var manyLinks: MutableList<OnePersistentId>
    override var optionalLink: OnePersistentId?
    override var inContainer: Container
    override var inOptionalContainer: Container?
    override var inContainerList: MutableList<Container>
    override var deepContainer: MutableList<TooDeepContainer>
    override var sealedContainer: SealedContainer
    override var listSealedContainer: MutableList<SealedContainer>
    override var justProperty: String
    override var justNullableProperty: String?
    override var justListProperty: MutableList<String>
    override var deepSealedClass: DeepSealedOne
    override var children: List<SoftLinkReferencedChild>
  }

  companion object : Type<EntityWithSoftLinks, Builder>() {
    operator fun invoke(link: OnePersistentId,
                        manyLinks: List<OnePersistentId>,
                        inContainer: Container,
                        inContainerList: List<Container>,
                        deepContainer: List<TooDeepContainer>,
                        sealedContainer: SealedContainer,
                        listSealedContainer: List<SealedContainer>,
                        justProperty: String,
                        justListProperty: List<String>,
                        deepSealedClass: DeepSealedOne,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): EntityWithSoftLinks {
      val builder = builder()
      builder.link = link
      builder.manyLinks = manyLinks.toMutableWorkspaceList()
      builder.inContainer = inContainer
      builder.inContainerList = inContainerList.toMutableWorkspaceList()
      builder.deepContainer = deepContainer.toMutableWorkspaceList()
      builder.sealedContainer = sealedContainer
      builder.listSealedContainer = listSealedContainer.toMutableWorkspaceList()
      builder.justProperty = justProperty
      builder.justListProperty = justListProperty.toMutableWorkspaceList()
      builder.deepSealedClass = deepSealedClass
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: EntityWithSoftLinks, modification: EntityWithSoftLinks.Builder.() -> Unit) = modifyEntity(
  EntityWithSoftLinks.Builder::class.java, entity, modification)
//endregion

interface SoftLinkReferencedChild : WorkspaceEntity {
  val parentEntity: EntityWithSoftLinks

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : SoftLinkReferencedChild, WorkspaceEntity.Builder<SoftLinkReferencedChild>, ObjBuilder<SoftLinkReferencedChild> {
    override var entitySource: EntitySource
    override var parentEntity: EntityWithSoftLinks
  }

  companion object : Type<SoftLinkReferencedChild, Builder>() {
    operator fun invoke(entitySource: EntitySource, init: (Builder.() -> Unit)? = null): SoftLinkReferencedChild {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: SoftLinkReferencedChild,
                                      modification: SoftLinkReferencedChild.Builder.() -> Unit) = modifyEntity(
  SoftLinkReferencedChild.Builder::class.java, entity, modification)
//endregion
