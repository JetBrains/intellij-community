// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.testEntities.entities.*
import com.intellij.platform.workspace.storage.toBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class MutableStorageTest {
  @Test
  fun `simple entity mutation test`() {
    val builder = MutableEntityStorage.create()
    val sampleEntity = SampleEntity2("ParentData", true, MySource)

    builder.addEntity(sampleEntity)
    val simpleEntityFromStore = builder.entities(SampleEntity2::class.java).single()
    builder.modifyEntity(sampleEntity) {
      entitySource = AnotherSource
      data = "NewParentData"
    }
    assertEquals(AnotherSource, sampleEntity.entitySource)
    assertEquals(AnotherSource, simpleEntityFromStore.entitySource)
    assertEquals("NewParentData", sampleEntity.data)
    assertEquals("NewParentData", simpleEntityFromStore.data)

    val newBuilder = MutableEntityStorage.from(builder.toSnapshot())

    val entityFromStoreOne = newBuilder.entities(SampleEntity2::class.java).single()
    entityFromStoreOne as SampleEntity2.Builder
    val entityFromStoreTwo = newBuilder.entities(SampleEntity2::class.java).single()
    entityFromStoreTwo as SampleEntity2.Builder

    newBuilder.modifyEntity(entityFromStoreOne) {
      entitySource = MySource
      data = "ParentData"
    }

    assertEquals(AnotherSource, sampleEntity.entitySource)
    assertEquals(AnotherSource, simpleEntityFromStore.entitySource)
    assertEquals(MySource, entityFromStoreOne.entitySource)
    assertEquals(MySource, entityFromStoreTwo.entitySource)
    assertEquals("NewParentData", sampleEntity.data)
    assertEquals("NewParentData", simpleEntityFromStore.data)
    assertEquals("ParentData", entityFromStoreOne.data)
    assertEquals("ParentData", entityFromStoreTwo.data)
  }

  @Test
  fun `check exception if request data from entity which was removed`() {
    val builder = MutableEntityStorage.create()
    val sampleEntity = SampleEntity2("ParentData", false, MySource)
    builder.addEntity(sampleEntity)
    val newBuilder = MutableEntityStorage.from(builder.toSnapshot())
    val entityFromStore = newBuilder.entities(SampleEntity2::class.java).single()
    newBuilder.removeEntity(entityFromStore)

    assertThrows<IllegalStateException> { entityFromStore.data }
    assertEquals("ParentData", sampleEntity.data)
  }

  @Test
  fun `check parent updates`() {
    val builder = MutableEntityStorage.create()
    val parentEntity = ParentMultipleEntity("ParentData", MySource) {
      children = listOf(ChildMultipleEntity("ChildOneData", MySource))
    }
    builder.addEntity(parentEntity)

    val parentEntityFromStore = builder.entities(ParentMultipleEntity::class.java).single()
    val child = ChildMultipleEntity("ChildData", MySource) {
      this.parentEntity = parentEntityFromStore
    }
    builder.addEntity(child)

    val childEntity = builder.entities(ChildMultipleEntity::class.java).single { it.childData == "ChildData" }
    assertEquals(parentEntity, childEntity.parentEntity)
    assertEquals(parentEntityFromStore, childEntity.parentEntity)

    builder.modifyEntity(parentEntityFromStore) {
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
    parentEntityFromStore as ParentMultipleEntity.Builder
    assertThrows<IllegalStateException> {
      parentEntityFromStore.parentData = "AnotherParentData"
    }
    assertEquals("ParentData", parentEntityFromStore.parentData)
    assertEquals("ParentData", parentEntity.parentData)

    assertThrows<IllegalStateException> {
      parentEntityFromStore.children = listOf(ChildMultipleEntity("ChildTwoData", MySource))
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
    builder2.modifyEntity(entity) {
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

    builder.modifyEntity(entity) {
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

    builder.modifyEntity(entity) {
      this.children = listOf(this.children[1], this.children[0])
    }

    assertEquals(AnotherSource, entity.children[0].entitySource)
    assertEquals(MySource, entity.children[1].entitySource)
  }
}