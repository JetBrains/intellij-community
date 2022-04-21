package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity



//region ------------------- Parent Entity --------------------------------

@Suppress("unused")
interface OoParentEntity : WorkspaceEntity {
  val parentProperty: String
  val child: @Child OoChildEntity?

  val anotherChild: @Child OoChildWithNullableParentEntity?


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: OoParentEntity, ModifiableWorkspaceEntity<OoParentEntity>, ObjBuilder<OoParentEntity> {
      override var parentProperty: String
      override var entitySource: EntitySource
      override var child: OoChildEntity?
      override var anotherChild: OoChildWithNullableParentEntity?
  }
  
  companion object: Type<OoParentEntity, Builder>()
  //@formatter:on
  //endregion

}

fun MutableEntityStorage.addOoParentEntity(
  parentProperty: String = "parent",
  source: EntitySource = MySource
): OoParentEntity {
  val ooParentEntity = OoParentEntity {
    this.parentProperty = parentProperty
    this.entitySource = source
  }
  this.addEntity(ooParentEntity)
  return ooParentEntity
}

//endregion

//region ---------------- Child entity ----------------------


@Suppress("unused")
interface OoChildEntity : WorkspaceEntity {
  val childProperty: String
  val parentEntity: OoParentEntity

  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: OoChildEntity, ModifiableWorkspaceEntity<OoChildEntity>, ObjBuilder<OoChildEntity> {
      override var childProperty: String
      override var entitySource: EntitySource
      override var parentEntity: OoParentEntity
  }
  
  companion object: Type<OoChildEntity, Builder>()
  //@formatter:on
  //endregion

}


fun MutableEntityStorage.addOoChildEntity(
  OoParentEntity: OoParentEntity,
  childProperty: String = "child",
  source: EntitySource = MySource
): OoChildEntity {
  val ooChildEntity = OoChildEntity {
    this.childProperty = childProperty
    this.parentEntity = OoParentEntity
    this.entitySource = source
  }
  this.addEntity(ooChildEntity)
  return ooChildEntity
}

//endregion

//region ----------------- Child entity with a nullable parent -----------------------------
interface OoChildWithNullableParentEntity : WorkspaceEntity {
  val parentEntity: OoParentEntity?


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: OoChildWithNullableParentEntity, ModifiableWorkspaceEntity<OoChildWithNullableParentEntity>, ObjBuilder<OoChildWithNullableParentEntity> {
      override var parentEntity: OoParentEntity?
      override var entitySource: EntitySource
  }
  
  companion object: Type<OoChildWithNullableParentEntity, Builder>()
  //@formatter:on
  //endregion

}

fun MutableEntityStorage.addOoChildWithNullableParentEntity(
  OoParentEntity: OoParentEntity,
  source: EntitySource = MySource
): OoChildWithNullableParentEntity {
  val ooChildWithNullableParentEntity = OoChildWithNullableParentEntity {
    this.parentEntity = OoParentEntity
    this.entitySource = source
  }
  this.addEntity(ooChildWithNullableParentEntity)
  return ooChildWithNullableParentEntity
}
//endregion

//region ------------------- Parent Entity with PersistentId --------------------------------

data class OoParentEntityId(val name: String) : PersistentEntityId<OoParentWithPidEntity> {
  override val presentableName: String
    get() = name
}


interface OoParentWithPidEntity : WorkspaceEntityWithPersistentId {
  val parentProperty: String

  override val persistentId: OoParentEntityId get() = OoParentEntityId(parentProperty)

  val childOne: @Child OoChildForParentWithPidEntity?
  val childThree: @Child OoChildAlsoWithPidEntity?

  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: OoParentWithPidEntity, ModifiableWorkspaceEntity<OoParentWithPidEntity>, ObjBuilder<OoParentWithPidEntity> {
      override var parentProperty: String
      override var entitySource: EntitySource
      override var childOne: OoChildForParentWithPidEntity?
      override var childThree: OoChildAlsoWithPidEntity?
  }
  
  companion object: Type<OoParentWithPidEntity, Builder>()
  //@formatter:on
  //endregion

}

fun MutableEntityStorage.addOoParentWithPidEntity(
  parentProperty: String = "parent",
  source: EntitySource = MySource
): OoParentWithPidEntity {
  val ooParentWithPidEntity = OoParentWithPidEntity {
    this.parentProperty = parentProperty
    this.entitySource = source
  }
  this.addEntity(ooParentWithPidEntity)
  return ooParentWithPidEntity
}

//endregion

// ---------------- Child entity for parent with PersistentId for Nullable ref ----------------------

interface OoChildForParentWithPidEntity : WorkspaceEntity {
  val childProperty: String
  val parentEntity: OoParentWithPidEntity

  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: OoChildForParentWithPidEntity, ModifiableWorkspaceEntity<OoChildForParentWithPidEntity>, ObjBuilder<OoChildForParentWithPidEntity> {
      override var childProperty: String
      override var entitySource: EntitySource
      override var parentEntity: OoParentWithPidEntity
  }
  
  companion object: Type<OoChildForParentWithPidEntity, Builder>()
  //@formatter:on
  //endregion

}

fun MutableEntityStorage.addOoChildForParentWithPidEntity(
  parentEntity: OoParentWithPidEntity,
  childProperty: String = "child",
  source: EntitySource = MySource
): OoChildForParentWithPidEntity {
  val ooChildForParentWithPidEntity = OoChildForParentWithPidEntity {
    this.parentEntity = parentEntity
    this.childProperty = childProperty
    this.entitySource = source
  }
  this.addEntity(ooChildForParentWithPidEntity)
  return ooChildForParentWithPidEntity
}

// ---------------- Child with PersistentId for parent with PersistentId ----------------------

interface OoChildAlsoWithPidEntity : WorkspaceEntityWithPersistentId {
  val childProperty: String
  val parentEntity: OoParentWithPidEntity

  override val persistentId: OoChildEntityId get() = OoChildEntityId(childProperty)

  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: OoChildAlsoWithPidEntity, ModifiableWorkspaceEntity<OoChildAlsoWithPidEntity>, ObjBuilder<OoChildAlsoWithPidEntity> {
      override var childProperty: String
      override var entitySource: EntitySource
      override var parentEntity: OoParentWithPidEntity
  }
  
  companion object: Type<OoChildAlsoWithPidEntity, Builder>()
  //@formatter:on
  //endregion

}

fun MutableEntityStorage.addOoChildAlsoWithPidEntity(
  parentEntity: OoParentWithPidEntity,
  childProperty: String = "child",
  source: EntitySource = MySource
): OoChildAlsoWithPidEntity {
  val ooChildAlsoWithPidEntity = OoChildAlsoWithPidEntity {
    this.parentEntity = parentEntity
    this.childProperty = childProperty
    this.entitySource = source
  }
  this.addEntity(ooChildAlsoWithPidEntity)
  return ooChildAlsoWithPidEntity
}

// ------------------- Parent Entity without PersistentId for Nullable ref --------------------------------


interface OoParentWithoutPidEntity : WorkspaceEntity {
  val parentProperty: String
  val childOne: @Child OoChildWithPidEntity?

  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: OoParentWithoutPidEntity, ModifiableWorkspaceEntity<OoParentWithoutPidEntity>, ObjBuilder<OoParentWithoutPidEntity> {
      override var parentProperty: String
      override var entitySource: EntitySource
      override var childOne: OoChildWithPidEntity?
  }
  
  companion object: Type<OoParentWithoutPidEntity, Builder>()
  //@formatter:on
  //endregion

}


fun MutableEntityStorage.addOoParentWithoutPidEntity(
  parentProperty: String = "parent",
  source: EntitySource = MySource
): OoParentWithoutPidEntity {
  val ooParentWithoutPidEntity = OoParentWithoutPidEntity {
    this.parentProperty = parentProperty
    this.entitySource = source
  }
  this.addEntity(ooParentWithoutPidEntity)
  return ooParentWithoutPidEntity
}

// ---------------- Child entity with PersistentId for Nullable ref----------------------

data class OoChildEntityId(val name: String) : PersistentEntityId<OoChildWithPidEntity> {
  override val presentableName: String
    get() = name
}

interface OoChildWithPidEntity : WorkspaceEntityWithPersistentId {
  val childProperty: String
  val parentEntity: OoParentWithoutPidEntity

  override val persistentId: OoChildEntityId get() = OoChildEntityId(childProperty)


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: OoChildWithPidEntity, ModifiableWorkspaceEntity<OoChildWithPidEntity>, ObjBuilder<OoChildWithPidEntity> {
      override var childProperty: String
      override var entitySource: EntitySource
      override var parentEntity: OoParentWithoutPidEntity
  }
  
  companion object: Type<OoChildWithPidEntity, Builder>()
  //@formatter:on
  //endregion

}

fun MutableEntityStorage.addOoChildWithPidEntity(
  parentEntity: OoParentWithoutPidEntity,
  childProperty: String = "child",
  source: EntitySource = MySource
): OoChildWithPidEntity {
  val ooChildWithPidEntity = OoChildWithPidEntity {
    this.parentEntity = parentEntity
    this.childProperty = childProperty
    this.entitySource = source
  }
  this.addEntity(ooChildWithPidEntity)
  return ooChildWithPidEntity
}
