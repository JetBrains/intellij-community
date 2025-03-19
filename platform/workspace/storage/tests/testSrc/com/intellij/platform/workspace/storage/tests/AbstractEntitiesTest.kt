// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.assertConsistency
import com.intellij.platform.workspace.storage.testEntities.entities.*
import com.intellij.testFramework.UsefulTestCase.assertOneElement
import org.junit.jupiter.api.Test
import kotlin.test.*

class AbstractEntitiesTest {
  @Test
  fun `simple adding`() {
    val builder = MutableEntityStorage.create()

    val middleEntity = MiddleEntity("prop", MySource)
    builder addEntity LeftEntity(MySource) {
      children = listOf(middleEntity)
    }

    val storage = builder.toSnapshot()

    val leftEntity = assertOneElement(storage.entities(LeftEntity::class.java).toList())
    assertOneElement(leftEntity.children.toList())
  }

  @Test
  fun `modifying left entity`() {
    val builder = MutableEntityStorage.create()

    val middleEntity = MiddleEntity("first", MySource)
    val leftEntity = builder addEntity LeftEntity(MySource) {
      children = listOf(middleEntity)
    }

    val anotherMiddleEntity = MiddleEntity("second", MySource)
    builder.modifyLeftEntity(leftEntity) {

    }
    builder.modifyLeftEntity(leftEntity) {
      this.children = listOf(anotherMiddleEntity)
    }

    val storage = builder.toSnapshot()

    val actualLeftEntity = assertOneElement(storage.entities(LeftEntity::class.java).toList())
    val actualChild = assertOneElement(actualLeftEntity.children.toList())
    assertEquals(anotherMiddleEntity.property, (actualChild as MiddleEntity).property)
    assertEquals(anotherMiddleEntity.property, (actualChild as MiddleEntity).property)
  }

  @Test
  fun `modifying abstract entity`() {
    val builder = MutableEntityStorage.create()

    val middleEntity = MiddleEntity("prop", MySource)
    val leftEntity = builder addEntity LeftEntity(MySource) {
      children = listOf(middleEntity)
    }

    val anotherMiddleEntity = MiddleEntity("prop", MySource)
    builder.modifyLeftEntity(leftEntity) {
      this.children = listOf(anotherMiddleEntity)
    }

    val storage = builder.toSnapshot()

    val actualLeftEntity = assertOneElement(storage.entities(LeftEntity::class.java).toList())
    val actualChild = assertOneElement(actualLeftEntity.children.toList())
    assertEquals(anotherMiddleEntity.property, (actualChild as MiddleEntity).property)
    assertEquals(anotherMiddleEntity.property, (actualChild as MiddleEntity).property)
  }

  @Test
  fun `children replace in applyChangesFrom`() {
    val builder = MutableEntityStorage.create()
    val middleEntity = MiddleEntity("prop", MySource)
    val leftEntity = builder addEntity LeftEntity(MySource) {
      children = listOf(middleEntity)
    }

    val anotherBuilder = MutableEntityStorage.from(builder.toSnapshot())
    val anotherMiddleEntity = MiddleEntity("Another", MySource)
    anotherBuilder.modifyLeftEntity(leftEntity.from(anotherBuilder)) {
      this.children = listOf(middleEntity, anotherMiddleEntity)
    }

    val initialMiddleEntity = MiddleEntity("Initial", MySource)
    builder.modifyLeftEntity(leftEntity) {
      this.children = listOf(middleEntity, initialMiddleEntity)
    }

    builder.applyChangesFrom(anotherBuilder)

    val actualLeftEntity = assertOneElement(builder.entities(LeftEntity::class.java).toList())
    val children = actualLeftEntity.children.toList() as List<MiddleEntity>
    assertEquals(2, children.size)
    assertTrue(children.any { it.property == "Another" })
    assertTrue(children.none { it.property == "Initial" })
  }

  @Test
  fun `keep children ordering when making storage`() {
    val builder = MutableEntityStorage.create()
    val middleEntity1 = MiddleEntity("One", MySource)
    val middleEntity2 = MiddleEntity("Two", MySource)
    builder addEntity LeftEntity(MySource) {
      children = listOf(middleEntity1, middleEntity2)
    }

    val storage = builder.toSnapshot()
    val children = storage.entities(LeftEntity::class.java).single().children.toList()
    assertEquals(middleEntity1.property, (children[0] as MiddleEntity).property)
    assertEquals(middleEntity2.property, (children[1] as MiddleEntity).property)
  }

  @Test
  fun `keep children ordering when making storage 2`() {
    val builder = MutableEntityStorage.create()
    val middleEntity1 = MiddleEntity("Two", MySource)
    val middleEntity2 = MiddleEntity("One", MySource)
    builder addEntity LeftEntity(MySource) {
      children = listOf(middleEntity1, middleEntity2)
    }


    val anotherBuilder = makeBuilder(builder) {
      this addEntity LeftEntity(MySource) {
        children = listOf(middleEntity2, middleEntity1)
      }
    }

    builder.applyChangesFrom(anotherBuilder)

    val storage = builder.toSnapshot()
    val children = storage.entities(LeftEntity::class.java).last().children.toList()
    assertEquals(middleEntity2.property, (children[0] as MiddleEntity).property)
    assertEquals(middleEntity1.property, (children[1] as MiddleEntity).property)
  }

  @Test
  fun `keep children ordering after rbs 1`() {
    val builder = MutableEntityStorage.create()
    val middleEntity1 = MiddleEntity("One", MySource)
    val middleEntity2 = MiddleEntity("Two", MySource)
    builder addEntity LeftEntity(MySource) {
      children = listOf(middleEntity1, middleEntity2)
    }

    val target = MutableEntityStorage.create()

    target.replaceBySource({ true }, builder)

    val children = target.toSnapshot().entities(LeftEntity::class.java).last().children.toList()
    assertEquals(middleEntity1.property, (children[0] as MiddleEntity).property)
    assertEquals(middleEntity2.property, (children[1] as MiddleEntity).property)
  }

  @Test
  fun `keep children ordering after rbs 2`() {
    val builder = MutableEntityStorage.create()
    val middleEntity1 = MiddleEntity("One", MySource)
    val middleEntity2 = MiddleEntity("Two", MySource)
    builder addEntity LeftEntity(MySource) {
      children = listOf(middleEntity2, middleEntity1)
    }

    val target = MutableEntityStorage.create()

    target.replaceBySource({ true }, builder)

    val children = target.toSnapshot().entities(LeftEntity::class.java).last().children.toList()
    assertEquals(middleEntity2.property, (children[0] as MiddleEntity).property)
    assertEquals(middleEntity1.property, (children[1] as MiddleEntity).property)
  }


  @Test
  fun `modifying one to one child switch`() {
    val builder = MutableEntityStorage.create()

    val headAbstractionEntity = HeadAbstractionEntity("info", MySource)
    builder.addEntity(headAbstractionEntity)

    builder.addEntity(LeftEntity(AnotherSource) {
      this.parent = headAbstractionEntity
    })

    builder.addEntity(LeftEntity(MySource) {
      this.parent = headAbstractionEntity
    })

    builder.assertConsistency()
    assertNull(builder.entities(LeftEntity::class.java).single { it.entitySource == AnotherSource }.parent)
    assertNotNull(builder.entities(LeftEntity::class.java).single { it.entitySource == MySource }.parent)
  }

  @Test
  fun `modifying one to one parent switch`() {
    val builder = MutableEntityStorage.create()

    val child = LeftEntity(AnotherSource)

    builder addEntity HeadAbstractionEntity("Info", MySource) {
      this.child = child
    }
    builder addEntity HeadAbstractionEntity("Info2", MySource) {
      this.child = child
    }

    builder.assertConsistency()
    assertNull(builder.entities(HeadAbstractionEntity::class.java).single { it.data == "Info" }.child)
    assertNotNull(builder.entities(HeadAbstractionEntity::class.java).single { it.data == "Info2" }.child)
  }

  @Test
  fun `add entity with child then steal this child with different entity`() {
    val builder = MutableEntityStorage.create()

    val child = MiddleEntity("", SampleEntitySource(""))
    val anotherChild = LeftEntity(MySource) {
      this.children = listOf(child)
    }

    builder addEntity LeftEntity(AnotherSource) {
      this.children = listOf(
        anotherChild,
        child,
      )
    }

    val children = builder.entities(LeftEntity::class.java).single { it.entitySource is AnotherSource }.children
    assertEquals(2, children.size)
    assertEquals(MySource, children[0].entitySource)
    assertIs<SampleEntitySource>(children[1].entitySource)
  }
}
