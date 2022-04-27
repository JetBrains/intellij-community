// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.entities.model.api

import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleDependencyItem
import com.intellij.workspaceModel.storage.entity.TestEntity
import com.intellij.workspaceModel.storage.referrersx
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import com.intellij.workspaceModel.storage.MutableEntityStorage




interface FooEntity: WorkspaceEntity {
  val name: String
  val moduleDependency: ModuleDependencyItem.Exportable.ModuleDependency
  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: FooEntity, ModifiableWorkspaceEntity<FooEntity>, ObjBuilder<FooEntity> {
      override var name: String
      override var entitySource: EntitySource
  }
  
  companion object: Type<FooEntity, Builder>() {
      operator fun invoke(name: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): FooEntity {
          val builder = builder()
          builder.name = name
          builder.entitySource = entitySource
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: FooEntity, modification: FooEntity.Builder.() -> Unit) = modifyEntity(FooEntity.Builder::class.java, entity, modification)
//endregion


interface AnotherTest: WorkspaceEntity {
  val name: String
  val testField: TestEntity
  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: AnotherTest, ModifiableWorkspaceEntity<AnotherTest>, ObjBuilder<AnotherTest> {
      override var name: String
      override var entitySource: EntitySource
  }
  
  companion object: Type<AnotherTest, Builder>() {
      operator fun invoke(name: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): AnotherTest {
          val builder = builder()
          builder.name = name
          builder.entitySource = entitySource
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: AnotherTest, modification: AnotherTest.Builder.() -> Unit) = modifyEntity(AnotherTest.Builder::class.java, entity, modification)
//endregion

val TestEntity.anotherTest: @Child AnotherTest?
  get() = referrersx(AnotherTest::testField).singleOrNull()