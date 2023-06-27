// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities


import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.annotations.Open
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion

import com.intellij.platform.workspace.storage.MutableEntityStorage




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

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : OneEntityWithSymbolicId, WorkspaceEntity.Builder<OneEntityWithSymbolicId> {
    override var entitySource: EntitySource
    override var myName: String
  }

  companion object : EntityType<OneEntityWithSymbolicId, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(myName: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): OneEntityWithSymbolicId {
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
fun MutableEntityStorage.modifyEntity(entity: OneEntityWithSymbolicId,
                                      modification: OneEntityWithSymbolicId.Builder.() -> Unit) = modifyEntity(
  OneEntityWithSymbolicId.Builder::class.java, entity, modification)
//endregion

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

  val children: List<@Child SoftLinkReferencedChild>

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : EntityWithSoftLinks, WorkspaceEntity.Builder<EntityWithSoftLinks> {
    override var entitySource: EntitySource
    override var link: OneSymbolicId
    override var manyLinks: MutableList<OneSymbolicId>
    override var optionalLink: OneSymbolicId?
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

  companion object : EntityType<EntityWithSoftLinks, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(link: OneSymbolicId,
                        manyLinks: List<OneSymbolicId>,
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
  @GeneratedCodeApiVersion(2)
  interface Builder : SoftLinkReferencedChild, WorkspaceEntity.Builder<SoftLinkReferencedChild> {
    override var entitySource: EntitySource
    override var parentEntity: EntityWithSoftLinks
  }

  companion object : EntityType<SoftLinkReferencedChild, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
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
