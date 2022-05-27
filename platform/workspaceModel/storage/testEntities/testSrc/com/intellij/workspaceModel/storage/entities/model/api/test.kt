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
import com.intellij.workspaceModel.storage.impl.ExtRefKey
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.updateOneToOneChildOfParent





interface FooEntity: WorkspaceEntity {
  val name: String
  val moduleDependency: ModuleDependencyItem.Exportable.ModuleDependency
  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: FooEntity, ModifiableWorkspaceEntity<FooEntity>, ObjBuilder<FooEntity> {
      override var name: String
      override var entitySource: EntitySource
      override var moduleDependency: ModuleDependency
  }
  
  companion object: Type<FooEntity, Builder>() {
      operator fun invoke(name: String, entitySource: EntitySource, moduleDependency: ModuleDependency, init: (Builder.() -> Unit)? = null): FooEntity {
          val builder = builder()
          builder.name = name
          builder.entitySource = entitySource
          builder.moduleDependency = moduleDependency
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
      override var testField: TestEntity
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
var TestEntity.Builder.anotherTest: @Child AnotherTest?
    get() {
        return referrersx(AnotherTest::testField).singleOrNull()
    }
    set(value) {
        val diff = (this as ModifiableWorkspaceEntityBase<*>).diff
        if (diff != null) {
            if (value != null) {
                if ((value as AnotherTestImpl.Builder).diff == null) {
                    value._testField = this
                    diff.addEntity(value)
                }
            }
            diff.updateOneToOneChildOfParent(AnotherTestImpl.TESTFIELD_CONNECTION_ID, this, value)
        }
        else {
            val key = ExtRefKey("AnotherTest", "testField", true, AnotherTestImpl.TESTFIELD_CONNECTION_ID)
            this.extReferences[key] = value
            
            if (value != null) {
                (value as AnotherTestImpl.Builder)._testField = this
            }
        }
    }

//endregion

val TestEntity.anotherTest: @Child AnotherTest?
  get() = referrersx(AnotherTest::testField).singleOrNull()