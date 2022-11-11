package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage




//region ------------------- Parent Entity --------------------------------

@Suppress("unused")
interface OoParentEntity : WorkspaceEntity {
  val parentProperty: String
  val child: @Child OoChildEntity?

  val anotherChild: @Child OoChildWithNullableParentEntity?

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : OoParentEntity, ModifiableWorkspaceEntity<OoParentEntity>, ObjBuilder<OoParentEntity> {
    override var entitySource: EntitySource
    override var parentProperty: String
    override var child: OoChildEntity?
    override var anotherChild: OoChildWithNullableParentEntity?
  }

  companion object : Type<OoParentEntity, Builder>() {
    operator fun invoke(parentProperty: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): OoParentEntity {
      val builder = builder()
      builder.parentProperty = parentProperty
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: OoParentEntity, modification: OoParentEntity.Builder.() -> Unit) = modifyEntity(
  OoParentEntity.Builder::class.java, entity, modification)
//endregion

fun MutableEntityStorage.addOoParentEntity(
  parentProperty: String = "parent",
  source: EntitySource = MySource
): OoParentEntity {
  val ooParentEntity = OoParentEntity(parentProperty, source)
  this.addEntity(ooParentEntity)
  return ooParentEntity
}

//region ---------------- Child entity ----------------------


@Suppress("unused")
interface OoChildEntity : WorkspaceEntity {
  val childProperty: String
  val parentEntity: OoParentEntity

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : OoChildEntity, ModifiableWorkspaceEntity<OoChildEntity>, ObjBuilder<OoChildEntity> {
    override var entitySource: EntitySource
    override var childProperty: String
    override var parentEntity: OoParentEntity
  }

  companion object : Type<OoChildEntity, Builder>() {
    operator fun invoke(childProperty: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): OoChildEntity {
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
fun MutableEntityStorage.modifyEntity(entity: OoChildEntity, modification: OoChildEntity.Builder.() -> Unit) = modifyEntity(
  OoChildEntity.Builder::class.java, entity, modification)
//endregion


fun MutableEntityStorage.addOoChildEntity(
  OoParentEntity: OoParentEntity,
  childProperty: String = "child",
  source: EntitySource = MySource
): OoChildEntity {
  val ooChildEntity = OoChildEntity(childProperty, source) {
    this.parentEntity = OoParentEntity
  }
  this.addEntity(ooChildEntity)
  return ooChildEntity
}

//region ----------------- Child entity with a nullable parent -----------------------------
interface OoChildWithNullableParentEntity : WorkspaceEntity {
  val parentEntity: OoParentEntity?

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : OoChildWithNullableParentEntity, ModifiableWorkspaceEntity<OoChildWithNullableParentEntity>, ObjBuilder<OoChildWithNullableParentEntity> {
    override var entitySource: EntitySource
    override var parentEntity: OoParentEntity?
  }

  companion object : Type<OoChildWithNullableParentEntity, Builder>() {
    operator fun invoke(entitySource: EntitySource, init: (Builder.() -> Unit)? = null): OoChildWithNullableParentEntity {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: OoChildWithNullableParentEntity,
                                      modification: OoChildWithNullableParentEntity.Builder.() -> Unit) = modifyEntity(
  OoChildWithNullableParentEntity.Builder::class.java, entity, modification)
//endregion

fun MutableEntityStorage.addOoChildWithNullableParentEntity(
  OoParentEntity: OoParentEntity,
  source: EntitySource = MySource
): OoChildWithNullableParentEntity {
  val ooChildWithNullableParentEntity = OoChildWithNullableParentEntity(source) {
    this.parentEntity = OoParentEntity
  }
  this.addEntity(ooChildWithNullableParentEntity)
  return ooChildWithNullableParentEntity
}


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
  @GeneratedCodeApiVersion(1)
  interface Builder : OoParentWithPidEntity, ModifiableWorkspaceEntity<OoParentWithPidEntity>, ObjBuilder<OoParentWithPidEntity> {
    override var entitySource: EntitySource
    override var parentProperty: String
    override var childOne: OoChildForParentWithPidEntity?
    override var childThree: OoChildAlsoWithPidEntity?
  }

  companion object : Type<OoParentWithPidEntity, Builder>() {
    operator fun invoke(parentProperty: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): OoParentWithPidEntity {
      val builder = builder()
      builder.parentProperty = parentProperty
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: OoParentWithPidEntity, modification: OoParentWithPidEntity.Builder.() -> Unit) = modifyEntity(
  OoParentWithPidEntity.Builder::class.java, entity, modification)
//endregion

fun MutableEntityStorage.addOoParentWithPidEntity(
  parentProperty: String = "parent",
  source: EntitySource = MySource
): OoParentWithPidEntity {
  val ooParentWithPidEntity = OoParentWithPidEntity(parentProperty, source)
  this.addEntity(ooParentWithPidEntity)
  return ooParentWithPidEntity
}


// ---------------- Child entity for parent with PersistentId for Nullable ref ----------------------

interface OoChildForParentWithPidEntity : WorkspaceEntity {
  val childProperty: String
  val parentEntity: OoParentWithPidEntity

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : OoChildForParentWithPidEntity, ModifiableWorkspaceEntity<OoChildForParentWithPidEntity>, ObjBuilder<OoChildForParentWithPidEntity> {
    override var entitySource: EntitySource
    override var childProperty: String
    override var parentEntity: OoParentWithPidEntity
  }

  companion object : Type<OoChildForParentWithPidEntity, Builder>() {
    operator fun invoke(childProperty: String,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): OoChildForParentWithPidEntity {
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
fun MutableEntityStorage.modifyEntity(entity: OoChildForParentWithPidEntity,
                                      modification: OoChildForParentWithPidEntity.Builder.() -> Unit) = modifyEntity(
  OoChildForParentWithPidEntity.Builder::class.java, entity, modification)
//endregion

fun MutableEntityStorage.addOoChildForParentWithPidEntity(
  parentEntity: OoParentWithPidEntity,
  childProperty: String = "child",
  source: EntitySource = MySource
): OoChildForParentWithPidEntity {
  val ooChildForParentWithPidEntity = OoChildForParentWithPidEntity(childProperty, source) {
    this.parentEntity = parentEntity
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
  @GeneratedCodeApiVersion(1)
  interface Builder : OoChildAlsoWithPidEntity, ModifiableWorkspaceEntity<OoChildAlsoWithPidEntity>, ObjBuilder<OoChildAlsoWithPidEntity> {
    override var entitySource: EntitySource
    override var childProperty: String
    override var parentEntity: OoParentWithPidEntity
  }

  companion object : Type<OoChildAlsoWithPidEntity, Builder>() {
    operator fun invoke(childProperty: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): OoChildAlsoWithPidEntity {
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
fun MutableEntityStorage.modifyEntity(entity: OoChildAlsoWithPidEntity,
                                      modification: OoChildAlsoWithPidEntity.Builder.() -> Unit) = modifyEntity(
  OoChildAlsoWithPidEntity.Builder::class.java, entity, modification)
//endregion

fun MutableEntityStorage.addOoChildAlsoWithPidEntity(
  parentEntity: OoParentWithPidEntity,
  childProperty: String = "child",
  source: EntitySource = MySource
): OoChildAlsoWithPidEntity {
  val ooChildAlsoWithPidEntity = OoChildAlsoWithPidEntity(childProperty, source) {
    this.parentEntity = parentEntity
  }
  this.addEntity(ooChildAlsoWithPidEntity)
  return ooChildAlsoWithPidEntity
}

// ------------------- Parent Entity without PersistentId for Nullable ref --------------------------------


interface OoParentWithoutPidEntity : WorkspaceEntity {
  val parentProperty: String
  val childOne: @Child OoChildWithPidEntity?

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : OoParentWithoutPidEntity, ModifiableWorkspaceEntity<OoParentWithoutPidEntity>, ObjBuilder<OoParentWithoutPidEntity> {
    override var entitySource: EntitySource
    override var parentProperty: String
    override var childOne: OoChildWithPidEntity?
  }

  companion object : Type<OoParentWithoutPidEntity, Builder>() {
    operator fun invoke(parentProperty: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): OoParentWithoutPidEntity {
      val builder = builder()
      builder.parentProperty = parentProperty
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: OoParentWithoutPidEntity,
                                      modification: OoParentWithoutPidEntity.Builder.() -> Unit) = modifyEntity(
  OoParentWithoutPidEntity.Builder::class.java, entity, modification)
//endregion


fun MutableEntityStorage.addOoParentWithoutPidEntity(
  parentProperty: String = "parent",
  source: EntitySource = MySource
): OoParentWithoutPidEntity {
  val ooParentWithoutPidEntity = OoParentWithoutPidEntity(parentProperty, source)
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
  @GeneratedCodeApiVersion(1)
  interface Builder : OoChildWithPidEntity, ModifiableWorkspaceEntity<OoChildWithPidEntity>, ObjBuilder<OoChildWithPidEntity> {
    override var entitySource: EntitySource
    override var childProperty: String
    override var parentEntity: OoParentWithoutPidEntity
  }

  companion object : Type<OoChildWithPidEntity, Builder>() {
    operator fun invoke(childProperty: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): OoChildWithPidEntity {
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
fun MutableEntityStorage.modifyEntity(entity: OoChildWithPidEntity, modification: OoChildWithPidEntity.Builder.() -> Unit) = modifyEntity(
  OoChildWithPidEntity.Builder::class.java, entity, modification)
//endregion

fun MutableEntityStorage.addOoChildWithPidEntity(
  parentEntity: OoParentWithoutPidEntity,
  childProperty: String = "child",
  source: EntitySource = MySource
): OoChildWithPidEntity {
  val ooChildWithPidEntity = OoChildWithPidEntity(childProperty, source) {
    this.parentEntity = parentEntity
  }
  this.addEntity(ooChildWithPidEntity)
  return ooChildWithPidEntity
}
