// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.testEntities.entities.AnotherSource
import com.intellij.platform.workspace.storage.testEntities.entities.ChildMultipleEntity
import com.intellij.platform.workspace.storage.testEntities.entities.LeftEntity
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.platform.workspace.storage.testEntities.entities.NamedChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.NamedEntity
import com.intellij.platform.workspace.storage.testEntities.entities.ParentMultipleEntity
import com.intellij.platform.workspace.storage.testEntities.entities.ParentMultipleEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.SampleEntity2
import com.intellij.platform.workspace.storage.testEntities.entities.SampleEntity2Builder
import com.intellij.platform.workspace.storage.testEntities.entities.modifyLeftEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifyNamedEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifyParentMultipleEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifySampleEntity2
import com.intellij.platform.workspace.storage.toBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class MutableStorageTest {
  @Test
  fun `simple entity mutation test`() {
    val builder = MutableEntityStorage.create()
    val sampleEntity = builder addEntity SampleEntity2("ParentData", true, MySource)

    val simpleEntityFromStore = builder.entities(SampleEntity2::class.java).single()
    builder.modifySampleEntity2(sampleEntity) {
      entitySource = AnotherSource
      data = "NewParentData"
    }
    assertEquals(AnotherSource, sampleEntity.entitySource)
    assertEquals(AnotherSource, simpleEntityFromStore.entitySource)
    assertEquals("NewParentData", sampleEntity.data)
    assertEquals("NewParentData", simpleEntityFromStore.data)

    val newBuilder = MutableEntityStorage.from(builder.toSnapshot())

    val entityFromStoreOne = newBuilder.entities(SampleEntity2::class.java).single()
    val sampleBuilder = entityFromStoreOne.builderFrom(newBuilder) as SampleEntity2Builder
    val entityFromStoreTwo = newBuilder.entities(SampleEntity2::class.java).single()
    val entityTwoBuilder = entityFromStoreTwo.builderFrom(newBuilder) as SampleEntity2Builder

    newBuilder.modifySampleEntity2(entityFromStoreOne) {
      entitySource = MySource
      data = "ParentData"
    }

    assertEquals(AnotherSource, sampleEntity.entitySource)
    assertEquals(AnotherSource, simpleEntityFromStore.entitySource)
    assertEquals(MySource, sampleBuilder.entitySource)
    assertEquals(MySource, entityTwoBuilder.entitySource)
    assertEquals("NewParentData", sampleEntity.data)
    assertEquals("NewParentData", simpleEntityFromStore.data)
    assertEquals("ParentData", sampleBuilder.data)
    assertEquals("ParentData", entityTwoBuilder.data)
  }

  @Test
  fun `check parent updates`() {
    val builder = MutableEntityStorage.create()
    val parentEntity = builder addEntity ParentMultipleEntity("ParentData", MySource) {
      children = listOf(ChildMultipleEntity("ChildOneData", MySource))
    }

    val parentEntityFromStore = builder.entities(ParentMultipleEntity::class.java).single()
    val child = ChildMultipleEntity("ChildData", MySource) child@{
      builder.modifyParentMultipleEntity(parentEntityFromStore) parent@{
        this@child.parentEntity = this@parent

      }
    }
    builder.addEntity(child)

    val childEntity = builder.entities(ChildMultipleEntity::class.java).single { it.childData == "ChildData" }
    assertEquals(parentEntity, childEntity.parentEntity)
    assertEquals(parentEntityFromStore, childEntity.parentEntity)

    builder.modifyParentMultipleEntity(parentEntityFromStore) {
      parentData = "AnotherParentData"
    }
    assertEquals("AnotherParentData", parentEntityFromStore.parentData)
    assertEquals("AnotherParentData", parentEntity.parentData)
    assertEquals(2, parentEntity.children.size)
    assertEquals(2, parentEntityFromStore.children.size)
  }

  @Test
  fun `fields modification without lambda not allowed test`() {
    val builder = MutableEntityStorage.create()
    val parentEntity = ParentMultipleEntity("ParentData", MySource) {
      children = listOf(ChildMultipleEntity("ChildOneData", MySource))
    }
    builder.addEntity(parentEntity)

    val parentEntityFromStore = builder.entities(ParentMultipleEntity::class.java).single()
    val parentBuilder = parentEntityFromStore.builderFrom(builder) as ParentMultipleEntityBuilder
    assertThrows<IllegalStateException> {
      parentBuilder.parentData = "AnotherParentData"
    }
    assertEquals("ParentData", parentBuilder.parentData)
    assertEquals("ParentData", parentEntity.parentData)

    assertThrows<IllegalStateException> {
      parentBuilder.children = listOf(ChildMultipleEntity("ChildTwoData", MySource))
    }
  }

  @Test
  fun `change entity source in snapshot`() {
    val builder = MutableEntityStorage.create()
    builder.addEntity(SampleEntity2("data", true, MySource))
    val snapshot = builder.toSnapshot()
    val entity = snapshot.entities(SampleEntity2::class.java).single()
    val builder2 = snapshot.toBuilder()
    val builder3 = snapshot.toBuilder()
    builder2.modifySampleEntity2(entity) {
      entitySource = AnotherSource
    }
    builder3.applyChangesFrom(builder2)
    assertEquals(MySource, snapshot.entities(SampleEntity2::class.java).single().entitySource)
  }

  @Test
  fun `check that order of children is preserved after modification`() {
    val builder = MutableEntityStorage.create()
    val entity = builder addEntity NamedEntity("123", MySource) {
      this.children = listOf(
        NamedChildEntity("One", MySource),
        NamedChildEntity("Two", MySource),
      )
    }

    assertEquals("One", entity.children[0].childProperty)
    assertEquals("Two", entity.children[1].childProperty)

    builder.modifyNamedEntity(entity) {
      this.children = listOf(this.children[1], this.children[0])
    }

    assertEquals("Two", entity.children[0].childProperty)
    assertEquals("One", entity.children[1].childProperty)
  }

  @Test
  fun `check that order of children is preserved after modification with abstract children`() {
    val builder = MutableEntityStorage.create()
    val entity = builder addEntity LeftEntity(MySource) {
      this.children = listOf(
        LeftEntity(MySource),
        LeftEntity(AnotherSource),
      )
    }

    assertEquals(MySource, entity.children[0].entitySource)
    assertEquals(AnotherSource, entity.children[1].entitySource)

    builder.modifyLeftEntity(entity) {
      this.children = listOf(this.children[1], this.children[0])
    }

    assertEquals(AnotherSource, entity.children[0].entitySource)
    assertEquals(MySource, entity.children[1].entitySource)
  }
}