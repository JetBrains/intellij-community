// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList


// ------------------------------ Persistent Id ---------------

data class NameId(private val name: String) : SymbolicEntityId<NamedEntity> {
  override val presentableName: String
    get() = name

  override fun toString(): String = name
}

data class AnotherNameId(private val name: String) : SymbolicEntityId<NamedEntity> {
  override val presentableName: String
    get() = name

  override fun toString(): String = name
}

data class ComposedId(val name: String, val link: NameId) : SymbolicEntityId<ComposedIdSoftRefEntity> {
  override val presentableName: String
    get() = "$name - ${link.presentableName}"
}

// ------------------------------ Entity With Persistent Id ------------------

interface NamedEntity : WorkspaceEntityWithSymbolicId {
  val myName: String
  val additionalProperty: String?

  val children: List<@Child NamedChildEntity>

  override val symbolicId: NameId
    get() = NameId(myName)

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<NamedEntity> {
    override var entitySource: EntitySource
    var myName: String
    var additionalProperty: String?
    var children: List<NamedChildEntity.Builder>
  }

  companion object : EntityType<NamedEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      myName: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
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
fun MutableEntityStorage.modifyNamedEntity(
  entity: NamedEntity,
  modification: NamedEntity.Builder.() -> Unit,
): NamedEntity {
  return modifyEntity(NamedEntity.Builder::class.java, entity, modification)
}
//endregion


//val NamedEntity.children: List<NamedChildEntity>
//    get() = TODO()
//  get() = referrers(NamedChildEntity::parent)

// ------------------------------ Child of entity with persistent id ------------------


interface NamedChildEntity : WorkspaceEntity {
  val childProperty: String
  val parentEntity: NamedEntity

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<NamedChildEntity> {
    override var entitySource: EntitySource
    var childProperty: String
    var parentEntity: NamedEntity.Builder
  }

  companion object : EntityType<NamedChildEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      childProperty: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.childProperty = childProperty
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyNamedChildEntity(
  entity: NamedChildEntity,
  modification: NamedChildEntity.Builder.() -> Unit,
): NamedChildEntity {
  return modifyEntity(NamedChildEntity.Builder::class.java, entity, modification)
}
//endregion


// ------------------------------ Entity with soft link --------------------

interface WithSoftLinkEntity : WorkspaceEntity {
  val link: NameId

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<WithSoftLinkEntity> {
    override var entitySource: EntitySource
    var link: NameId
  }

  companion object : EntityType<WithSoftLinkEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      link: NameId,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.link = link
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyWithSoftLinkEntity(
  entity: WithSoftLinkEntity,
  modification: WithSoftLinkEntity.Builder.() -> Unit,
): WithSoftLinkEntity {
  return modifyEntity(WithSoftLinkEntity.Builder::class.java, entity, modification)
}
//endregion

interface ComposedLinkEntity : WorkspaceEntity {
  val link: ComposedId

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ComposedLinkEntity> {
    override var entitySource: EntitySource
    var link: ComposedId
  }

  companion object : EntityType<ComposedLinkEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      link: ComposedId,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.link = link
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyComposedLinkEntity(
  entity: ComposedLinkEntity,
  modification: ComposedLinkEntity.Builder.() -> Unit,
): ComposedLinkEntity {
  return modifyEntity(ComposedLinkEntity.Builder::class.java, entity, modification)
}
//endregion

// ------------------------- Entity with SymbolicId and the list of soft links ------------------


interface WithListSoftLinksEntity : WorkspaceEntityWithSymbolicId {
  val myName: String
  val links: List<NameId>
  override val symbolicId: AnotherNameId get() = AnotherNameId(myName)

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<WithListSoftLinksEntity> {
    override var entitySource: EntitySource
    var myName: String
    var links: MutableList<NameId>
  }

  companion object : EntityType<WithListSoftLinksEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      myName: String,
      links: List<NameId>,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.myName = myName
      builder.links = links.toMutableWorkspaceList()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyWithListSoftLinksEntity(
  entity: WithListSoftLinksEntity,
  modification: WithListSoftLinksEntity.Builder.() -> Unit,
): WithListSoftLinksEntity {
  return modifyEntity(WithListSoftLinksEntity.Builder::class.java, entity, modification)
}
//endregion


// --------------------------- Entity with composed persistent id via soft reference ------------------


interface ComposedIdSoftRefEntity : WorkspaceEntityWithSymbolicId {
  val myName: String
  val link: NameId
  override val symbolicId: ComposedId get() = ComposedId(myName, link)

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ComposedIdSoftRefEntity> {
    override var entitySource: EntitySource
    var myName: String
    var link: NameId
  }

  companion object : EntityType<ComposedIdSoftRefEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      myName: String,
      link: NameId,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.myName = myName
      builder.link = link
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyComposedIdSoftRefEntity(
  entity: ComposedIdSoftRefEntity,
  modification: ComposedIdSoftRefEntity.Builder.() -> Unit,
): ComposedIdSoftRefEntity {
  return modifyEntity(ComposedIdSoftRefEntity.Builder::class.java, entity, modification)
}
//endregion
