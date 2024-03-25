// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Child


//region ------------------- Parent Entity --------------------------------

@Suppress("unused")
interface OoParentEntity : WorkspaceEntity {
  val parentProperty: String
  val child: @Child OoChildEntity?

  val anotherChild: @Child OoChildWithNullableParentEntity?

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : OoParentEntity, WorkspaceEntity.Builder<OoParentEntity> {
    override var entitySource: EntitySource
    override var parentProperty: String
    override var child: OoChildEntity?
    override var anotherChild: OoChildWithNullableParentEntity?
  }

  companion object : EntityType<OoParentEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      parentProperty: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): OoParentEntity {
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
fun MutableEntityStorage.modifyEntity(
  entity: OoParentEntity,
  modification: OoParentEntity.Builder.() -> Unit,
): OoParentEntity {
  return modifyEntity(OoParentEntity.Builder::class.java, entity, modification)
}
//endregion

//region ---------------- Child entity ----------------------


@Suppress("unused")
interface OoChildEntity : WorkspaceEntity {
  val childProperty: String
  val parentEntity: OoParentEntity

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : OoChildEntity, WorkspaceEntity.Builder<OoChildEntity> {
    override var entitySource: EntitySource
    override var childProperty: String
    override var parentEntity: OoParentEntity
  }

  companion object : EntityType<OoChildEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      childProperty: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): OoChildEntity {
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
fun MutableEntityStorage.modifyEntity(
  entity: OoChildEntity,
  modification: OoChildEntity.Builder.() -> Unit,
): OoChildEntity {
  return modifyEntity(OoChildEntity.Builder::class.java, entity, modification)
}
//endregion


//region ----------------- Child entity with a nullable parent -----------------------------
interface OoChildWithNullableParentEntity : WorkspaceEntity {
  val parentEntity: OoParentEntity?

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : OoChildWithNullableParentEntity, WorkspaceEntity.Builder<OoChildWithNullableParentEntity> {
    override var entitySource: EntitySource
    override var parentEntity: OoParentEntity?
  }

  companion object : EntityType<OoChildWithNullableParentEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): OoChildWithNullableParentEntity {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(
  entity: OoChildWithNullableParentEntity,
  modification: OoChildWithNullableParentEntity.Builder.() -> Unit,
): OoChildWithNullableParentEntity {
  return modifyEntity(OoChildWithNullableParentEntity.Builder::class.java, entity, modification)
}
//endregion

//region ------------------- Parent Entity with SymbolicId --------------------------------

data class OoParentEntityId(val name: String) : SymbolicEntityId<OoParentWithPidEntity> {
  override val presentableName: String
    get() = name
}


interface OoParentWithPidEntity : WorkspaceEntityWithSymbolicId {
  val parentProperty: String

  override val symbolicId: OoParentEntityId get() = OoParentEntityId(parentProperty)

  val childOne: @Child OoChildForParentWithPidEntity?
  val childThree: @Child OoChildAlsoWithPidEntity?

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : OoParentWithPidEntity, WorkspaceEntity.Builder<OoParentWithPidEntity> {
    override var entitySource: EntitySource
    override var parentProperty: String
    override var childOne: OoChildForParentWithPidEntity?
    override var childThree: OoChildAlsoWithPidEntity?
  }

  companion object : EntityType<OoParentWithPidEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      parentProperty: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): OoParentWithPidEntity {
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
fun MutableEntityStorage.modifyEntity(
  entity: OoParentWithPidEntity,
  modification: OoParentWithPidEntity.Builder.() -> Unit,
): OoParentWithPidEntity {
  return modifyEntity(OoParentWithPidEntity.Builder::class.java, entity, modification)
}
//endregion


// ---------------- Child entity for parent with SymbolicId for Nullable ref ----------------------

interface OoChildForParentWithPidEntity : WorkspaceEntity {
  val childProperty: String
  val parentEntity: OoParentWithPidEntity

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : OoChildForParentWithPidEntity, WorkspaceEntity.Builder<OoChildForParentWithPidEntity> {
    override var entitySource: EntitySource
    override var childProperty: String
    override var parentEntity: OoParentWithPidEntity
  }

  companion object : EntityType<OoChildForParentWithPidEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      childProperty: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): OoChildForParentWithPidEntity {
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
fun MutableEntityStorage.modifyEntity(
  entity: OoChildForParentWithPidEntity,
  modification: OoChildForParentWithPidEntity.Builder.() -> Unit,
): OoChildForParentWithPidEntity {
  return modifyEntity(OoChildForParentWithPidEntity.Builder::class.java, entity, modification)
}
//endregion

// ---------------- Child with SymbolicId for parent with SymbolicId ----------------------

interface OoChildAlsoWithPidEntity : WorkspaceEntityWithSymbolicId {
  val childProperty: String
  val parentEntity: OoParentWithPidEntity

  override val symbolicId: OoChildEntityId get() = OoChildEntityId(childProperty)

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : OoChildAlsoWithPidEntity, WorkspaceEntity.Builder<OoChildAlsoWithPidEntity> {
    override var entitySource: EntitySource
    override var childProperty: String
    override var parentEntity: OoParentWithPidEntity
  }

  companion object : EntityType<OoChildAlsoWithPidEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      childProperty: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): OoChildAlsoWithPidEntity {
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
fun MutableEntityStorage.modifyEntity(
  entity: OoChildAlsoWithPidEntity,
  modification: OoChildAlsoWithPidEntity.Builder.() -> Unit,
): OoChildAlsoWithPidEntity {
  return modifyEntity(OoChildAlsoWithPidEntity.Builder::class.java, entity, modification)
}
//endregion

// ------------------- Parent Entity without SymbolicId for Nullable ref --------------------------------


interface OoParentWithoutPidEntity : WorkspaceEntity {
  val parentProperty: String
  val childOne: @Child OoChildWithPidEntity?

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : OoParentWithoutPidEntity, WorkspaceEntity.Builder<OoParentWithoutPidEntity> {
    override var entitySource: EntitySource
    override var parentProperty: String
    override var childOne: OoChildWithPidEntity?
  }

  companion object : EntityType<OoParentWithoutPidEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      parentProperty: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): OoParentWithoutPidEntity {
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
fun MutableEntityStorage.modifyEntity(
  entity: OoParentWithoutPidEntity,
  modification: OoParentWithoutPidEntity.Builder.() -> Unit,
): OoParentWithoutPidEntity {
  return modifyEntity(OoParentWithoutPidEntity.Builder::class.java, entity, modification)
}
//endregion


// ---------------- Child entity with SymbolicId for Nullable ref----------------------

data class OoChildEntityId(val name: String) : SymbolicEntityId<OoChildWithPidEntity> {
  override val presentableName: String
    get() = name
}

interface OoChildWithPidEntity : WorkspaceEntityWithSymbolicId {
  val childProperty: String
  val parentEntity: OoParentWithoutPidEntity

  override val symbolicId: OoChildEntityId get() = OoChildEntityId(childProperty)

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : OoChildWithPidEntity, WorkspaceEntity.Builder<OoChildWithPidEntity> {
    override var entitySource: EntitySource
    override var childProperty: String
    override var parentEntity: OoParentWithoutPidEntity
  }

  companion object : EntityType<OoChildWithPidEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      childProperty: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): OoChildWithPidEntity {
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
fun MutableEntityStorage.modifyEntity(
  entity: OoChildWithPidEntity,
  modification: OoChildWithPidEntity.Builder.() -> Unit,
): OoChildWithPidEntity {
  return modifyEntity(OoChildWithPidEntity.Builder::class.java, entity, modification)
}
//endregion
