// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Abstract

interface SimpleEntity : WorkspaceEntityWithSymbolicId {
  override val symbolicId: SimpleId
    get() = SimpleId(name)

  val name: String

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<SimpleEntity> {
    override var entitySource: EntitySource
    var name: String
  }

  companion object : EntityType<SimpleEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      name: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.name = name
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifySimpleEntity(
  entity: SimpleEntity,
  modification: SimpleEntity.Builder.() -> Unit,
): SimpleEntity {
  return modifyEntity(SimpleEntity.Builder::class.java, entity, modification)
}
//endregion

data class SimpleId(val name: String) : SymbolicEntityId<SimpleEntity> {
  override val presentableName: String
    get() = name
}

// partial copy of org.jetbrains.kotlin.idea.workspaceModel.KotlinSettingsEntity
@Abstract
interface BaseEntity : WorkspaceEntity {
  val name: String
  val moduleId: SimpleId

  val aBaseEntityProperty: String
  val dBaseEntityProperty: String
  val bBaseEntityProperty: String

  val sealedDataClassProperty: BaseDataClass

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder<T : BaseEntity> : WorkspaceEntity.Builder<T> {
    override var entitySource: EntitySource
    var name: String
    var moduleId: SimpleId
    var aBaseEntityProperty: String
    var dBaseEntityProperty: String
    var bBaseEntityProperty: String
    var sealedDataClassProperty: BaseDataClass
  }

  companion object : EntityType<BaseEntity, Builder<BaseEntity>>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      name: String,
      moduleId: SimpleId,
      aBaseEntityProperty: String,
      dBaseEntityProperty: String,
      bBaseEntityProperty: String,
      sealedDataClassProperty: BaseDataClass,
      entitySource: EntitySource,
      init: (Builder<BaseEntity>.() -> Unit)? = null,
    ): Builder<BaseEntity> {
      val builder = builder()
      builder.name = name
      builder.moduleId = moduleId
      builder.aBaseEntityProperty = aBaseEntityProperty
      builder.dBaseEntityProperty = dBaseEntityProperty
      builder.bBaseEntityProperty = bBaseEntityProperty
      builder.sealedDataClassProperty = sealedDataClassProperty
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

interface ChildEntity : BaseEntity {
  val cChildEntityProperty: String

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ChildEntity>, BaseEntity.Builder<ChildEntity> {
    override var entitySource: EntitySource
    override var name: String
    override var moduleId: SimpleId
    override var aBaseEntityProperty: String
    override var dBaseEntityProperty: String
    override var bBaseEntityProperty: String
    override var sealedDataClassProperty: BaseDataClass
    var cChildEntityProperty: String
  }

  companion object : EntityType<ChildEntity, Builder>(BaseEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      name: String,
      moduleId: SimpleId,
      aBaseEntityProperty: String,
      dBaseEntityProperty: String,
      bBaseEntityProperty: String,
      sealedDataClassProperty: BaseDataClass,
      cChildEntityProperty: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.name = name
      builder.moduleId = moduleId
      builder.aBaseEntityProperty = aBaseEntityProperty
      builder.dBaseEntityProperty = dBaseEntityProperty
      builder.bBaseEntityProperty = bBaseEntityProperty
      builder.sealedDataClassProperty = sealedDataClassProperty
      builder.cChildEntityProperty = cChildEntityProperty
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyChildEntity(
  entity: ChildEntity,
  modification: ChildEntity.Builder.() -> Unit,
): ChildEntity {
  return modifyEntity(ChildEntity.Builder::class.java, entity, modification)
}
//endregion

sealed class BaseDataClass(val baseConstructorProperty: String) {
  open val baseBodyProperty: Int = 15
  val anotherBaseBodyProperty: Int = 16
}

sealed class DerivedDataClass(
  baseConstructorPropertyValue: String,
  val derivedConstructorProperty: String
) : BaseDataClass(baseConstructorPropertyValue) {
  val derivedBodyProperty: Int = 23
}

class DerivedDerivedDataClass(
  baseConstructorPropertyValue: String,
  override val baseBodyProperty: Int,
  derivedConstructorPropertyValue: String,
  val derivedDerivedConstructorProperty: String
) : DerivedDataClass(baseConstructorPropertyValue, derivedConstructorPropertyValue) {
  val deriveDerivedBodyProperty: Int = 42
}
