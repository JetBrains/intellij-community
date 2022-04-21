package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity



// ------------------------------ Persistent Id ---------------

data class NameId(private val name: String) : PersistentEntityId<NamedEntity> {
  override val presentableName: String
    get() = name

  override fun toString(): String = name
}

data class AnotherNameId(private val name: String) : PersistentEntityId<NamedEntity> {
  override val presentableName: String
    get() = name

  override fun toString(): String = name
}

data class ComposedId(val name: String, val link: NameId) : PersistentEntityId<ComposedIdSoftRefEntity> {
  override val presentableName: String
    get() = "$name - ${link.presentableName}"
}

// ------------------------------ Entity With Persistent Id ------------------

interface NamedEntity : WorkspaceEntityWithPersistentId {
  val myName: String
  val additionalProperty: String?

  val children: List<@Child NamedChildEntity>

  override val persistentId: NameId
    get() = NameId(myName)

  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: NamedEntity, ModifiableWorkspaceEntity<NamedEntity>, ObjBuilder<NamedEntity> {
      override var myName: String
      override var entitySource: EntitySource
      override var additionalProperty: String?
      override var children: List<NamedChildEntity>
  }
  
  companion object: Type<NamedEntity, Builder>()
  //@formatter:on
  //endregion

}


fun MutableEntityStorage.addNamedEntity(
  name: String,
  additionalProperty: String? = null,
  source: EntitySource = MySource
): NamedEntity {
  val namedEntity = NamedEntity {
    this.myName = name
    this.additionalProperty = additionalProperty
    this.entitySource = source
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
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: NamedChildEntity, ModifiableWorkspaceEntity<NamedChildEntity>, ObjBuilder<NamedChildEntity> {
      override var childProperty: String
      override var entitySource: EntitySource
      override var parentEntity: NamedEntity
  }
  
  companion object: Type<NamedChildEntity, Builder>()
  //@formatter:on
  //endregion

}


fun MutableEntityStorage.addNamedChildEntity(
  parentEntity: NamedEntity,
  childProperty: String = "child",
  source: EntitySource = MySource
): NamedChildEntity {
  val namedChildEntity = NamedChildEntity {
    this.parentEntity = parentEntity
    this.childProperty = childProperty
    this.entitySource = source
  }
  this.addEntity(namedChildEntity)
  return namedChildEntity
}

// ------------------------------ Entity with soft link --------------------

interface WithSoftLinkEntity : WorkspaceEntity {
  val link: NameId

  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: WithSoftLinkEntity, ModifiableWorkspaceEntity<WithSoftLinkEntity>, ObjBuilder<WithSoftLinkEntity> {
      override var link: NameId
      override var entitySource: EntitySource
  }
  
  companion object: Type<WithSoftLinkEntity, Builder>()
  //@formatter:on
  //endregion

}

fun MutableEntityStorage.addWithSoftLinkEntity(link: NameId, source: EntitySource = MySource): WithSoftLinkEntity {
  val withSoftLinkEntity = WithSoftLinkEntity {
    this.link = link
    this.entitySource = source
  }
  this.addEntity(withSoftLinkEntity)
  return withSoftLinkEntity
}

interface ComposedLinkEntity : WorkspaceEntity {
  val link: ComposedId

  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: ComposedLinkEntity, ModifiableWorkspaceEntity<ComposedLinkEntity>, ObjBuilder<ComposedLinkEntity> {
      override var link: ComposedId
      override var entitySource: EntitySource
  }
  
  companion object: Type<ComposedLinkEntity, Builder>()
  //@formatter:on
  //endregion

}

fun MutableEntityStorage.addComposedLinkEntity(link: ComposedId, source: EntitySource = MySource): ComposedLinkEntity {
  val composedLinkEntity = ComposedLinkEntity {
    this.link = link
    this.entitySource = source
  }
  this.addEntity(composedLinkEntity)
  return composedLinkEntity
}

// ------------------------- Entity with persistentId and the list of soft links ------------------


interface WithListSoftLinksEntity : WorkspaceEntityWithPersistentId {
  val myName: String
  val links: List<NameId>
  override val persistentId: AnotherNameId get() = AnotherNameId(myName)

  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: WithListSoftLinksEntity, ModifiableWorkspaceEntity<WithListSoftLinksEntity>, ObjBuilder<WithListSoftLinksEntity> {
      override var myName: String
      override var entitySource: EntitySource
      override var links: List<NameId>
  }
  
  companion object: Type<WithListSoftLinksEntity, Builder>()
  //@formatter:on
  //endregion

}


fun MutableEntityStorage.addWithListSoftLinksEntity(
  name: String,
  links: List<NameId>,
  source: EntitySource = MySource
): WithListSoftLinksEntity {
  val withListSoftLinksEntity = WithListSoftLinksEntity {
    this.myName = name
    this.links = links
    this.entitySource = source
  }
  this.addEntity(withListSoftLinksEntity)
  return withListSoftLinksEntity
}

// --------------------------- Entity with composed persistent id via soft reference ------------------


interface ComposedIdSoftRefEntity : WorkspaceEntityWithPersistentId {
  val myName: String
  val link: NameId
  override val persistentId: ComposedId get() = ComposedId(myName, link)

  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: ComposedIdSoftRefEntity, ModifiableWorkspaceEntity<ComposedIdSoftRefEntity>, ObjBuilder<ComposedIdSoftRefEntity> {
      override var myName: String
      override var entitySource: EntitySource
      override var link: NameId
  }
  
  companion object: Type<ComposedIdSoftRefEntity, Builder>()
  //@formatter:on
  //endregion

}

fun MutableEntityStorage.addComposedIdSoftRefEntity(
  name: String,
  link: NameId,
  source: EntitySource = MySource
): ComposedIdSoftRefEntity {
  val composedIdSoftRefEntity = ComposedIdSoftRefEntity {
    this.myName = name
    this.link = link
    this.entitySource = source
  }
  this.addEntity(composedIdSoftRefEntity)
  return composedIdSoftRefEntity
}
