package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.MutableEntityStorage




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
  @GeneratedCodeApiVersion(1)
  interface Builder : NamedEntity, WorkspaceEntity.Builder<NamedEntity>, ObjBuilder<NamedEntity> {
    override var entitySource: EntitySource
    override var myName: String
    override var additionalProperty: String?
    override var children: List<NamedChildEntity>
  }

  companion object : Type<NamedEntity, Builder>() {
    operator fun invoke(myName: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): NamedEntity {
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
fun MutableEntityStorage.modifyEntity(entity: NamedEntity, modification: NamedEntity.Builder.() -> Unit) = modifyEntity(
  NamedEntity.Builder::class.java, entity, modification)
//endregion


fun MutableEntityStorage.addNamedEntity(
  name: String,
  additionalProperty: String? = null,
  source: EntitySource = MySource
): NamedEntity {
  val namedEntity = NamedEntity(name, source) {
    this.additionalProperty = additionalProperty
    this.children = emptyList()
  }
  this.addEntity(namedEntity)
  return namedEntity
}


//val NamedEntity.children: List<NamedChildEntity>
//    get() = TODO()
//  get() = referrers(NamedChildEntity::parent)

// ------------------------------ Child of entity with persistent id ------------------


interface NamedChildEntity : WorkspaceEntity {
  val childProperty: String
  val parentEntity: NamedEntity

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : NamedChildEntity, WorkspaceEntity.Builder<NamedChildEntity>, ObjBuilder<NamedChildEntity> {
    override var entitySource: EntitySource
    override var childProperty: String
    override var parentEntity: NamedEntity
  }

  companion object : Type<NamedChildEntity, Builder>() {
    operator fun invoke(childProperty: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): NamedChildEntity {
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
fun MutableEntityStorage.modifyEntity(entity: NamedChildEntity, modification: NamedChildEntity.Builder.() -> Unit) = modifyEntity(
  NamedChildEntity.Builder::class.java, entity, modification)
//endregion


fun MutableEntityStorage.addNamedChildEntity(
  parentEntity: NamedEntity,
  childProperty: String = "child",
  source: EntitySource = MySource
): NamedChildEntity {
  val namedChildEntity = NamedChildEntity(childProperty, source) {
    this.parentEntity = parentEntity
  }
  this.addEntity(namedChildEntity)
  return namedChildEntity
}

// ------------------------------ Entity with soft link --------------------

interface WithSoftLinkEntity : WorkspaceEntity {
  val link: NameId

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : WithSoftLinkEntity, WorkspaceEntity.Builder<WithSoftLinkEntity>, ObjBuilder<WithSoftLinkEntity> {
    override var entitySource: EntitySource
    override var link: NameId
  }

  companion object : Type<WithSoftLinkEntity, Builder>() {
    operator fun invoke(link: NameId, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): WithSoftLinkEntity {
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
fun MutableEntityStorage.modifyEntity(entity: WithSoftLinkEntity, modification: WithSoftLinkEntity.Builder.() -> Unit) = modifyEntity(
  WithSoftLinkEntity.Builder::class.java, entity, modification)
//endregion

fun MutableEntityStorage.addWithSoftLinkEntity(link: NameId, source: EntitySource = MySource): WithSoftLinkEntity {
  val withSoftLinkEntity = WithSoftLinkEntity(link, source)
  this.addEntity(withSoftLinkEntity)
  return withSoftLinkEntity
}

interface ComposedLinkEntity : WorkspaceEntity {
  val link: ComposedId

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : ComposedLinkEntity, WorkspaceEntity.Builder<ComposedLinkEntity>, ObjBuilder<ComposedLinkEntity> {
    override var entitySource: EntitySource
    override var link: ComposedId
  }

  companion object : Type<ComposedLinkEntity, Builder>() {
    operator fun invoke(link: ComposedId, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ComposedLinkEntity {
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
fun MutableEntityStorage.modifyEntity(entity: ComposedLinkEntity, modification: ComposedLinkEntity.Builder.() -> Unit) = modifyEntity(
  ComposedLinkEntity.Builder::class.java, entity, modification)
//endregion

fun MutableEntityStorage.addComposedLinkEntity(link: ComposedId, source: EntitySource = MySource): ComposedLinkEntity {
  val composedLinkEntity = ComposedLinkEntity(link, source)
  this.addEntity(composedLinkEntity)
  return composedLinkEntity
}

// ------------------------- Entity with SymbolicId and the list of soft links ------------------


interface WithListSoftLinksEntity : WorkspaceEntityWithSymbolicId {
  val myName: String
  val links: List<NameId>
  override val symbolicId: AnotherNameId get() = AnotherNameId(myName)

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : WithListSoftLinksEntity, WorkspaceEntity.Builder<WithListSoftLinksEntity>, ObjBuilder<WithListSoftLinksEntity> {
    override var entitySource: EntitySource
    override var myName: String
    override var links: MutableList<NameId>
  }

  companion object : Type<WithListSoftLinksEntity, Builder>() {
    operator fun invoke(myName: String,
                        links: List<NameId>,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): WithListSoftLinksEntity {
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
fun MutableEntityStorage.modifyEntity(entity: WithListSoftLinksEntity,
                                      modification: WithListSoftLinksEntity.Builder.() -> Unit) = modifyEntity(
  WithListSoftLinksEntity.Builder::class.java, entity, modification)
//endregion


fun MutableEntityStorage.addWithListSoftLinksEntity(
  name: String,
  links: List<NameId>,
  source: EntitySource = MySource
): WithListSoftLinksEntity {
  val withListSoftLinksEntity = WithListSoftLinksEntity(name, links, source)
  this.addEntity(withListSoftLinksEntity)
  return withListSoftLinksEntity
}

// --------------------------- Entity with composed persistent id via soft reference ------------------


interface ComposedIdSoftRefEntity : WorkspaceEntityWithSymbolicId {
  val myName: String
  val link: NameId
  override val symbolicId: ComposedId get() = ComposedId(myName, link)

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : ComposedIdSoftRefEntity, WorkspaceEntity.Builder<ComposedIdSoftRefEntity>, ObjBuilder<ComposedIdSoftRefEntity> {
    override var entitySource: EntitySource
    override var myName: String
    override var link: NameId
  }

  companion object : Type<ComposedIdSoftRefEntity, Builder>() {
    operator fun invoke(myName: String,
                        link: NameId,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): ComposedIdSoftRefEntity {
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
fun MutableEntityStorage.modifyEntity(entity: ComposedIdSoftRefEntity,
                                      modification: ComposedIdSoftRefEntity.Builder.() -> Unit) = modifyEntity(
  ComposedIdSoftRefEntity.Builder::class.java, entity, modification)
//endregion

fun MutableEntityStorage.addComposedIdSoftRefEntity(
  name: String,
  link: NameId,
  source: EntitySource = MySource
): ComposedIdSoftRefEntity {
  val composedIdSoftRefEntity = ComposedIdSoftRefEntity(name, link, source)
  this.addEntity(composedIdSoftRefEntity)
  return composedIdSoftRefEntity
}
