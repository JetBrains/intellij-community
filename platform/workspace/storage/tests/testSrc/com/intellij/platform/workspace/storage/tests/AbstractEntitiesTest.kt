// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.assertConsistency
import com.intellij.platform.workspace.storage.testEntities.entities.*
import com.intellij.testFramework.UsefulTestCase.assertOneElement
import org.junit.jupiter.api.Test
import kotlin.test.*

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

    val anotherBuilder = MutableEntityStorage.from(builder.toSnapshot())
    val anotherMiddleEntity = anotherBuilder.addMiddleEntity("Another")
    anotherBuilder.modifyEntity(leftEntity.from(anotherBuilder)) {
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

    val child = builder addEntity LeftEntity(AnotherSource)

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
  fun `entity changes visible in mutable storage`() {
    var builder = MutableEntityStorage.create()
    val entity = ParentEntity("ParentData", MySource)
    builder.addEntity(entity)

    builder = MutableEntityStorage.from(builder.toSnapshot())
    val resultEntity = builder.entities(ParentEntity::class.java).single()
    resultEntity.parentData
    var firstEntityData = (entity as ModifiableWorkspaceEntityBase<*, *>).getEntityData()
    var secondEntityData = (resultEntity as ModifiableWorkspaceEntityBase<*, *>).getEntityData()
    assertSame(firstEntityData, secondEntityData)
    val originalEntityData = firstEntityData

    builder.modifyEntity(resultEntity) {
      this.parentData = "NewParentData"
    }
    val anotherResult = builder.entities(ParentEntity::class.java).single()
    assertEquals(resultEntity.parentData, anotherResult.parentData)

    firstEntityData = (resultEntity as ModifiableWorkspaceEntityBase<*, *>).getEntityData()
    secondEntityData = (anotherResult as ModifiableWorkspaceEntityBase<*, *>).getEntityData()
    assertSame(firstEntityData, secondEntityData)
    assertNotSame(firstEntityData, originalEntityData)

    builder.modifyEntity(anotherResult) {
      this.parentData = "AnotherParentData"
    }
    val oneMoreResult = builder.entities(ParentEntity::class.java).single()
    assertEquals(resultEntity.parentData, anotherResult.parentData)
    assertEquals(oneMoreResult.parentData, anotherResult.parentData)
    assertEquals(oneMoreResult.parentData, resultEntity.parentData)
  }

  @Test
  fun `add entity with child then steal this child with different entity`() {
    val builder = MutableEntityStorage.create()

    val child = builder addEntity MiddleEntity("", SampleEntitySource(""))
    val anotherChild = builder addEntity LeftEntity(MySource) {
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
