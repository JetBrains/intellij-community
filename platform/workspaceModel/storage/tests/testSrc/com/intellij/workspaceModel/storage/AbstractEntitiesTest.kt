// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.testFramework.UsefulTestCase.assertOneElement
import com.intellij.workspaceModel.storage.entities.test.api.LeftEntity
import com.intellij.workspaceModel.storage.entities.test.api.MiddleEntity
import com.intellij.workspaceModel.storage.entities.test.api.addLeftEntity
import com.intellij.workspaceModel.storage.entities.test.api.addMiddleEntity
import com.intellij.workspaceModel.storage.entities.test.api.modifyEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AbstractEntitiesTest {
  @Test
  fun `simple adding`() {
    val builder = MutableEntityStorage.create()

    val middleEntity = builder.addMiddleEntity()
    builder.addLeftEntity(sequenceOf(middleEntity))

    val storage = builder.toSnapshot()

    val leftEntity = assertOneElement(storage.entities(LeftEntity::class.java).toList())
    assertOneElement(leftEntity.children.toList())
  }

  @Test
  fun `modifying left entity`() {
    val builder = MutableEntityStorage.create()

    val middleEntity = builder.addMiddleEntity("first")
    val leftEntity = builder.addLeftEntity(sequenceOf(middleEntity))

    val anotherMiddleEntity = builder.addMiddleEntity("second")
    builder.modifyEntity(leftEntity) {
      
    }
    builder.modifyEntity(leftEntity) {
      this.children = listOf(anotherMiddleEntity)
    }

    val storage = builder.toSnapshot()

    val actualLeftEntity = assertOneElement(storage.entities(LeftEntity::class.java).toList())
    val actualChild = assertOneElement(actualLeftEntity.children.toList())
    assertEquals(anotherMiddleEntity, actualChild)
    assertEquals(anotherMiddleEntity.property, (actualChild as MiddleEntity).property)
  }

  @Test
  fun `modifying abstract entity`() {
    val builder = MutableEntityStorage.create()

    val middleEntity = builder.addMiddleEntity()
    val leftEntity = builder.addLeftEntity(sequenceOf(middleEntity))

    val anotherMiddleEntity = builder.addMiddleEntity()
    builder.modifyEntity(leftEntity) {
      this.children = listOf(anotherMiddleEntity)
    }

    val storage = builder.toSnapshot()

    val actualLeftEntity = assertOneElement(storage.entities(LeftEntity::class.java).toList())
    val actualChild = assertOneElement(actualLeftEntity.children.toList())
    assertEquals(anotherMiddleEntity, actualChild)
    assertEquals(anotherMiddleEntity.property, (actualChild as MiddleEntity).property)
  }

  @Test
  fun `children replace in addDiff`() {
    val builder = MutableEntityStorage.create()
    val middleEntity = builder.addMiddleEntity()
    val leftEntity = builder.addLeftEntity(sequenceOf(middleEntity))

    val anotherBuilder = MutableEntityStorage.from(builder)
    val anotherMiddleEntity = anotherBuilder.addMiddleEntity("Another")
    anotherBuilder.modifyEntity(leftEntity) {
      this.children = listOf(middleEntity, anotherMiddleEntity)
    }

    val initialMiddleEntity = builder.addMiddleEntity("Initial")
    builder.modifyEntity(leftEntity) {
      this.children = listOf(middleEntity, initialMiddleEntity)
    }

    builder.addDiff(anotherBuilder)

    val actualLeftEntity = assertOneElement(builder.entities(LeftEntity::class.java).toList())
    val children = actualLeftEntity.children.toList() as List<MiddleEntity>
    assertEquals(2, children.size)
    assertTrue(children.any { it.property == "Another" })
    assertTrue(children.none { it.property == "Initial" })
  }

  @Test
  fun `keep children ordering when making storage`() {
    val builder = MutableEntityStorage.create()
    val middleEntity1 = builder.addMiddleEntity("One")
    val middleEntity2 = builder.addMiddleEntity("Two")
    builder.addLeftEntity(sequenceOf(middleEntity1, middleEntity2))

    val storage = builder.toSnapshot()
    val children = storage.entities(LeftEntity::class.java).single().children.toList()
    assertEquals(middleEntity1, children[0])
    assertEquals(middleEntity2, children[1])
  }

  @Test
  fun `keep children ordering when making storage 2`() {
    val builder = MutableEntityStorage.create()
    val middleEntity1 = builder.addMiddleEntity("Two")
    val middleEntity2 = builder.addMiddleEntity("One")
    builder.addLeftEntity(sequenceOf(middleEntity1, middleEntity2))


    val anotherBuilder = makeBuilder(builder) {
      addLeftEntity(sequenceOf(middleEntity2, middleEntity1))
    }

    builder.addDiff(anotherBuilder)

    val storage = builder.toSnapshot()
    val children = storage.entities(LeftEntity::class.java).last().children.toList()
    assertEquals(middleEntity2, children[0])
    assertEquals(middleEntity1, children[1])
  }

  @Test
  fun `keep children ordering after rbs 1`() {
    val builder = MutableEntityStorage.create()
    val middleEntity1 = builder.addMiddleEntity("One")
    val middleEntity2 = builder.addMiddleEntity("Two")
    builder.addLeftEntity(sequenceOf(middleEntity1, middleEntity2))

    val target = MutableEntityStorage.create()

    target.replaceBySource({ true }, builder)

    val children = target.toSnapshot().entities(LeftEntity::class.java).last().children.toList()
    assertEquals(middleEntity1.property, (children[0] as MiddleEntity).property)
    assertEquals(middleEntity2.property, (children[1] as MiddleEntity).property)
  }

  @Test
  fun `keep children ordering after rbs 2`() {
    val builder = MutableEntityStorage.create()
    val middleEntity1 = builder.addMiddleEntity("One")
    val middleEntity2 = builder.addMiddleEntity("Two")
    builder.addLeftEntity(sequenceOf(middleEntity2, middleEntity1))

    val target = MutableEntityStorage.create()

    target.replaceBySource({ true }, builder)

    val children = target.toSnapshot().entities(LeftEntity::class.java).last().children.toList()
    assertEquals(middleEntity2.property, (children[0] as MiddleEntity).property)
    assertEquals(middleEntity1.property, (children[1] as MiddleEntity).property)
  }
}
