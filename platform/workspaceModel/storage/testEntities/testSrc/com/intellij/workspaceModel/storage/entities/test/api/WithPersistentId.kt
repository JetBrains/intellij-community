package com.intellij.workspaceModel.storage.entities.test.api


import com.intellij.workspaceModel.storage.*
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.annotations.Open
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
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
  //@formatter:off
  @GeneratedCodeApiVersion(1)
  interface Builder: OneEntityWithPersistentId, ModifiableWorkspaceEntity<OneEntityWithPersistentId>, ObjBuilder<OneEntityWithPersistentId> {
      override var myName: String
      override var entitySource: EntitySource
  }
  
  companion object: Type<OneEntityWithPersistentId, Builder>() {
      operator fun invoke(myName: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): OneEntityWithPersistentId {
          val builder = builder()
          builder.myName = myName
          builder.entitySource = entitySource
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: OneEntityWithPersistentId, modification: OneEntityWithPersistentId.Builder.() -> Unit) = modifyEntity(OneEntityWithPersistentId.Builder::class.java, entity, modification)
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
  //@formatter:off
  @GeneratedCodeApiVersion(1)
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
  
  companion object: Type<EntityWithSoftLinks, Builder>() {
      operator fun invoke(link: OnePersistentId, manyLinks: List<OnePersistentId>, inContainer: Container, inContainerList: List<Container>, deepContainer: List<TooDeepContainer>, sealedContainer: SealedContainer, listSealedContainer: List<SealedContainer>, justProperty: String, justListProperty: List<String>, deepSealedClass: DeepSealedOne, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): EntityWithSoftLinks {
          val builder = builder()
          builder.link = link
          builder.entitySource = entitySource
          builder.manyLinks = manyLinks
          builder.inContainer = inContainer
          builder.inContainerList = inContainerList
          builder.deepContainer = deepContainer
          builder.sealedContainer = sealedContainer
          builder.listSealedContainer = listSealedContainer
          builder.justProperty = justProperty
          builder.justListProperty = justListProperty
          builder.deepSealedClass = deepSealedClass
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: EntityWithSoftLinks, modification: EntityWithSoftLinks.Builder.() -> Unit) = modifyEntity(EntityWithSoftLinks.Builder::class.java, entity, modification)
//endregion

interface SoftLinkReferencedChild : WorkspaceEntity {
  val parentEntity: EntityWithSoftLinks

  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(1)
  interface Builder: SoftLinkReferencedChild, ModifiableWorkspaceEntity<SoftLinkReferencedChild>, ObjBuilder<SoftLinkReferencedChild> {
      override var parentEntity: EntityWithSoftLinks
      override var entitySource: EntitySource
  }
  
  companion object: Type<SoftLinkReferencedChild, Builder>() {
      operator fun invoke(entitySource: EntitySource, init: (Builder.() -> Unit)? = null): SoftLinkReferencedChild {
          val builder = builder()
          builder.entitySource = entitySource
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: SoftLinkReferencedChild, modification: SoftLinkReferencedChild.Builder.() -> Unit) = modifyEntity(SoftLinkReferencedChild.Builder::class.java, entity, modification)
//endregion
