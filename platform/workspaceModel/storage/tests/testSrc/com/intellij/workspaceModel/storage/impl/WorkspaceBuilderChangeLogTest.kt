// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.testFramework.assertInstanceOf
import com.intellij.workspaceModel.storage.createEmptyBuilder
import com.intellij.workspaceModel.storage.entities.test.addChildWithOptionalParentEntity
import com.intellij.workspaceModel.storage.entities.test.addParentEntity
import com.intellij.workspaceModel.storage.entities.test.addSourceEntity
import com.intellij.workspaceModel.storage.entities.test.api.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkspaceBuilderChangeLogTest {

  internal lateinit var builder: MutableEntityStorageImpl
  internal lateinit var another: MutableEntityStorageImpl

  @Before
  fun setUp() {
    builder = createEmptyBuilder()
    another = createEmptyBuilder()
  }

  @After
  fun tearDown() {
    builder.assertConsistency()
  }

  @Test
  fun `add plus delete`() {
    val entity = builder.addNamedEntity("Parent")
    builder.removeEntity(entity)

    val log = builder.changeLog.changeLog
    assertEquals(0, log.size)
  }

  @Test
  fun `add plus modify`() {
    val entity = builder.addParentEntity("Parent")
    builder.modifyEntity(entity) {
      this.parentProperty = "Another Parent"
    }

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.AddEntity)
    assertEquals("Another Parent", (changeEntry.entityData as XParentEntityData).parentProperty)
  }

  @Test
  fun `add plus change source`() {
    val entity = builder.addParentEntity("Parent")
    builder.modifyEntity(entity) {
      this.entitySource = AnotherSource
    }

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.AddEntity)
    assertEquals(changeEntry.entityData.entitySource, AnotherSource)
  }

  @Test
  fun `add plus change source and modify`() {
    val entity = builder.addParentEntity("Parent")
    builder.modifyEntity(entity) {
      this.entitySource = AnotherSource
    }
    builder.modifyEntity(entity) {
      this.parentProperty = "Another Parent"
    }

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.AddEntity)
    assertEquals("Another Parent", (changeEntry.entityData as XParentEntityData).parentProperty)
    assertEquals(changeEntry.entityData.entitySource, AnotherSource)
  }

  @Test
  fun `modify plus change source`() {
    val entity = builder.addParentEntity("Parent")
    builder.changeLog.clear()
    builder.modifyEntity(entity) {
      this.parentProperty = "Another Parent"
    }
    builder.modifyEntity(entity) {
      this.entitySource = AnotherSource
    }

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.ReplaceAndChangeSource)
    assertEquals((changeEntry.dataChange.newData as XParentEntityData).parentProperty,
                 "Another Parent")
    assertEquals(changeEntry.sourceChange.newData.entitySource, AnotherSource)
  }

  @Test
  fun `modify plus remove`() {
    val entity = builder.addParentEntity("Parent")
    builder.changeLog.clear()
    builder.modifyEntity(entity) {
      this.parentProperty = "Another Parent"
    }
    builder.removeEntity(entity)

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.RemoveEntity)
  }

  @Test
  fun `change source plus modify`() {
    val entity = builder.addParentEntity("Parent")
    builder.changeLog.clear()
    builder.modifyEntity(entity) {
      this.entitySource = AnotherSource
    }
    builder.modifyEntity(entity) {
      this.parentProperty = "Another Parent"
    }

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.ReplaceAndChangeSource)
    assertEquals((changeEntry.dataChange.newData as XParentEntityData).parentProperty,
                 "Another Parent")
    assertEquals(changeEntry.sourceChange.newData.entitySource, AnotherSource)
  }

  @Test
  fun `change source plus remove`() {
    val entity = builder.addParentEntity("Parent")
    builder.changeLog.clear()
    builder.modifyEntity(entity) {
      this.entitySource = AnotherSource
    }
    builder.removeEntity(entity)

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.RemoveEntity)
  }

  @Test
  fun `change source and modify plus remove`() {
    val entity = builder.addParentEntity("Parent")
    builder.changeLog.clear()
    builder.modifyEntity(entity) {
      this.entitySource = AnotherSource
    }
    builder.modifyEntity(entity) {
      this.parentProperty = "Another Parent"
    }
    builder.removeEntity(entity)

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.RemoveEntity)
  }

  @Test
  fun `change source and modify plus change source`() {
    val entity = builder.addParentEntity("Parent")
    builder.changeLog.clear()
    builder.modifyEntity(entity) {
      this.entitySource = AnotherSource
    }
    builder.modifyEntity(entity) {
      this.parentProperty = "Another Parent"
    }
    builder.modifyEntity(entity) {
      this.entitySource = SampleEntitySource("X")
    }

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertInstanceOf<ChangeEntry.ReplaceAndChangeSource>(changeEntry)
    assertEquals(((changeEntry as ChangeEntry.ReplaceAndChangeSource).dataChange.newData as XParentEntityData).parentProperty,
                 "Another Parent")
    assertEquals(changeEntry.sourceChange.newData.entitySource, SampleEntitySource("X"))
  }

  @Test
  fun `change source and modify plus modify`() {
    val entity = builder.addParentEntity("Parent")
    builder.changeLog.clear()
    builder.modifyEntity(entity) {
      entitySource = AnotherSource
    }
    builder.modifyEntity(entity) {
      this.parentProperty = "Another Parent"
    }
    builder.modifyEntity(entity) {
      this.parentProperty = "Third Parent"
    }

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertInstanceOf<ChangeEntry.ReplaceAndChangeSource>(changeEntry)
    assertEquals(((changeEntry as ChangeEntry.ReplaceAndChangeSource).dataChange.newData as XParentEntityData).parentProperty,
                 "Third Parent")
    assertEquals(changeEntry.sourceChange.newData.entitySource, AnotherSource)
  }

  @Test
  fun `modify add children`() {
    val entity = builder.addParentEntity("Parent")
    val firstChild = builder.addChildWithOptionalParentEntity(null)
    val secondChild = builder.addChildWithOptionalParentEntity(null)
    builder.changeLog.clear()

    builder.modifyEntity(entity) {
      this.optionalChildren = listOf(firstChild, secondChild)
    }

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.ReplaceEntity)
    changeEntry as ChangeEntry.ReplaceEntity
    assertEquals(0, changeEntry.removedChildren.size)
    assertEquals(2, changeEntry.newChildren.size)
    assertEquals(0, changeEntry.modifiedParents.size)
  }

  @Test
  fun `modify remove children`() {
    val entity = builder.addParentEntity("Parent")
    builder.addChildWithOptionalParentEntity(entity)
    builder.addChildWithOptionalParentEntity(entity)
    builder.changeLog.clear()

    builder.modifyEntity(entity) {
      this.optionalChildren = listOf()
    }

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.ReplaceEntity)
    changeEntry as ChangeEntry.ReplaceEntity
    assertEquals(2, changeEntry.removedChildren.size)
    assertEquals(0, changeEntry.newChildren.size)
    assertEquals(0, changeEntry.modifiedParents.size)
  }

  @Test
  fun `modify add and remove children`() {
    val entity = builder.addParentEntity("Parent")
    val child = builder.addChildWithOptionalParentEntity(null)
    builder.addChildWithOptionalParentEntity(entity)
    builder.changeLog.clear()

    builder.modifyEntity(entity) {
      this.optionalChildren = listOf(child)
    }

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.ReplaceEntity)
    changeEntry as ChangeEntry.ReplaceEntity
    assertEquals(1, changeEntry.removedChildren.size)
    assertEquals(1, changeEntry.newChildren.size)
    assertEquals(0, changeEntry.modifiedParents.size)
  }

  @Test
  fun `modify twice add and remove children`() {
    val entity = builder.addParentEntity("Parent")
    val firstChild = builder.addChildWithOptionalParentEntity(null)
    val secondChild = builder.addChildWithOptionalParentEntity(null)
    builder.changeLog.clear()

    builder.modifyEntity(entity) {
      this.optionalChildren = listOf(firstChild, secondChild)
    }

    builder.modifyEntity(entity) {
      this.optionalChildren = listOf()
    }

    val log = builder.changeLog.changeLog
    assertEquals(0, log.size)
  }

  @Test
  fun `modify twice remove and add children`() {
    val entity = builder.addParentEntity("Parent")
    val firstChild = builder.addChildWithOptionalParentEntity(entity)
    val secondChild = builder.addChildWithOptionalParentEntity(entity)
    builder.changeLog.clear()

    builder.modifyEntity(entity) {
      this.optionalChildren = listOf()
    }

    builder.modifyEntity(entity) {
      this.optionalChildren = listOf(firstChild, secondChild)
    }

    val log = builder.changeLog.changeLog
    assertEquals(0, log.size)
  }

  @Test
  fun `modify twice remove and remove children`() {
    val entity = builder.addParentEntity("Parent")
    val firstChild = builder.addChildWithOptionalParentEntity(entity)
    builder.addChildWithOptionalParentEntity(entity)
    builder.changeLog.clear()

    builder.modifyEntity(entity) {
      this.optionalChildren = listOf(firstChild)
    }

    builder.modifyEntity(entity) {
      this.optionalChildren = listOf()
    }

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.ReplaceEntity)
    changeEntry as ChangeEntry.ReplaceEntity
    assertEquals(2, changeEntry.removedChildren.size)
    assertEquals(0, changeEntry.newChildren.size)
    assertEquals(0, changeEntry.modifiedParents.size)
  }

  @Test
  fun `modify twice add and add children`() {
    val entity = builder.addParentEntity("Parent")
    val firstChild = builder.addChildWithOptionalParentEntity(null)
    val secondChild = builder.addChildWithOptionalParentEntity(null)
    builder.changeLog.clear()

    builder.modifyEntity(entity) {
      this.optionalChildren = listOf(firstChild)
    }

    builder.modifyEntity(entity) {
      this.optionalChildren = listOf(firstChild, secondChild)
    }

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.ReplaceEntity)
    changeEntry as ChangeEntry.ReplaceEntity
    assertEquals(0, changeEntry.removedChildren.size)
    assertEquals(2, changeEntry.newChildren.size)
    assertEquals(0, changeEntry.modifiedParents.size)
  }

  // ------------- Testing events collapsing ----

  @Test
  fun `collaps empty modify`() {
    val entity = builder.addNamedEntity("Parent")
    builder.changeLog.clear()

    builder.modifyEntity(entity) {}

    val log = builder.changeLog.changeLog
    assertEquals(0, log.size)
  }

  @Test
  fun `collaps two modify`() {
    val entity = builder.addNamedEntity("Parent")
    builder.changeLog.clear()

    builder.modifyEntity(entity) {
      myName = "AnotherName"
    }
    builder.modifyEntity(entity) {
      myName = "Parent"
    }

    val log = builder.changeLog.changeLog
    assertEquals(0, log.size)
  }

  @Test
  fun `collaps two modify with parent refs`() {
    val parent1 = builder.addNamedEntity("Parent")
    val parent2 = builder.addNamedEntity("Parent2")
    val child1 = builder.addNamedChildEntity(parent1)
    builder.changeLog.clear()

    builder.modifyEntity(child1) {
      this.parentEntity = parent2
    }

    builder.modifyEntity(child1) {
      this.parentEntity = parent1
    }

    val log = builder.changeLog.changeLog
    assertEquals(0, log.size)
  }

  @Test
  fun `collaps two modify with children refs`() {
    val parent = builder.addParentEntity()
    val child1 = builder.addChildWithOptionalParentEntity(parent)
    val child2 = builder.addChildWithOptionalParentEntity(parent)
    val child3 = builder.addChildWithOptionalParentEntity(parent)
    builder.changeLog.clear()

    builder.modifyEntity(parent) {
      this.optionalChildren = listOf()
    }

    builder.modifyEntity(parent) {
      this.optionalChildren = listOf(child1, child2, child3)
    }

    val log = builder.changeLog.changeLog
    assertEquals(0, log.size)
  }

  @Test
  fun `collaps three modify`() {
    val entity = builder.addNamedEntity("Parent")
    builder.changeLog.clear()

    builder.modifyEntity(entity) {
      myName = "AnotherName"
    }
    builder.modifyEntity(entity) {
      myName = "AndAnotherName"
    }
    builder.modifyEntity(entity) {
      myName = "Parent"
    }

    val log = builder.changeLog.changeLog
    assertEquals(0, log.size)
  }

  @Test
  fun `collaps two modify and source change`() {
    val entity = builder.addNamedEntity("Parent")
    builder.changeLog.clear()

    builder.modifyEntity(entity) {
      myName = "AnotherName"
    }
    builder.modifyEntity(entity) {
      this.entitySource = AnotherSource
    }
    builder.modifyEntity(entity) {
      myName = "Parent"
    }

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    assertInstanceOf<ChangeEntry.ChangeEntitySource>(log.entries.single ().value)
  }

  @Test
  fun `collaps two modify and two source change`() {
    val entity = builder.addNamedEntity("Parent")
    builder.changeLog.clear()

    builder.modifyEntity(entity) {
      myName = "AnotherName"
    }
    builder.modifyEntity(entity) {
      this.entitySource = AnotherSource
    }
    builder.modifyEntity(entity) {
      this.entitySource = MySource
    }
    builder.modifyEntity(entity) {
      myName = "Parent"
    }

    val log = builder.changeLog.changeLog
    assertEquals(0, log.size)
  }

  @Test
  fun `collaps two modify and two source change in mix`() {
    val entity = builder.addNamedEntity("Parent")
    builder.changeLog.clear()

    builder.modifyEntity(entity) {
      myName = "AnotherName"
    }
    builder.modifyEntity(entity) {
      this.entitySource = AnotherSource
    }
    builder.modifyEntity(entity) {
      myName = "Parent"
    }
    builder.modifyEntity(entity) {
      this.entitySource = MySource
    }

    val log = builder.changeLog.changeLog
    assertEquals(0, log.size)
  }

  @Test
  fun `collaps empty source change`() {
    val entity = builder.addNamedEntity("Parent", source = MySource)
    builder.changeLog.clear()

    builder.modifyEntity(entity) {
      this.entitySource = MySource
    }

    val log = builder.changeLog.changeLog
    assertEquals(0, log.size)
  }

  @Test
  fun `collaps source change twice`() {
    val entity = builder.addNamedEntity("Parent", source = MySource)
    builder.changeLog.clear()

    builder.modifyEntity(entity) {
      this.entitySource = AnotherSource
    }
    builder.modifyEntity(entity) {
      this.entitySource = MySource
    }

    val log = builder.changeLog.changeLog
    assertEquals(0, log.size)
  }

  @Test
  fun `add and change source`() {
    val oldSource = SampleEntitySource("oldSource")
    val entity = builder.addSourceEntity("one", oldSource)
    builder.modifyEntity(entity) {
      this.entitySource = SampleEntitySource("newSource")
    }
    val change = builder.changeLog.changeLog.values.single()
    assertInstanceOf<ChangeEntry.AddEntity>(change)
  }

  @Test
  fun `join remove plus add`() {
    val oldSource = SampleEntitySource("oldSource")
    val entity = builder.addSourceEntity("one", oldSource)
    builder.changeLog.clear()
    val original = builder.toSnapshot()
    builder.removeEntity(entity)
    builder.addSourceEntity("one", oldSource)
    assertTrue(builder.hasSameEntities(original as AbstractEntityStorage))
  }

  @Test
  fun `join remove plus add content root`() {
    val moduleTestEntity = ModuleTestEntity("data", MySource) {
      contentRoots = listOf(
        ContentRootTestEntity(MySource)
      )
    }
    builder.addEntity(moduleTestEntity)
    builder.changeLog.clear()
    val original = builder.toSnapshot()
    builder.removeEntity(builder.entities(ModuleTestEntity::class.java).single().contentRoots.single())
    builder.addEntity(ContentRootTestEntity(MySource) {
      module = moduleTestEntity
    })
    assertTrue(builder.hasSameEntities(original as AbstractEntityStorage))
  }

  @Test
  fun `join remove plus add content root with mappings`() {
    val moduleTestEntity = ModuleTestEntity("data", MySource) {
      contentRoots = listOf(
        ContentRootTestEntity(MySource)
      )
    }
    builder.addEntity(moduleTestEntity)
    builder.changeLog.clear()
    val contentRoot = builder.entities(ModuleTestEntity::class.java).single().contentRoots.single()
    builder.getMutableExternalMapping<Any>("data").addMapping(contentRoot, 1)
    val original = builder.toSnapshot()
    builder.removeEntity(contentRoot)
    builder.addEntity(ContentRootTestEntity(MySource) {
      module = moduleTestEntity
    })
    assertFalse(builder.hasSameEntities(original as AbstractEntityStorage))
  }

  @Test
  fun `join remove plus add content root with mappings 2`() {
    val moduleTestEntity = ModuleTestEntity("data", MySource) {
      contentRoots = listOf(
        ContentRootTestEntity(MySource)
      )
    }
    builder.addEntity(moduleTestEntity)
    builder.changeLog.clear()
    val contentRoot = builder.entities(ModuleTestEntity::class.java).single().contentRoots.single()
    val original = builder.toSnapshot()
    builder.removeEntity(contentRoot)
    val newContentRoot = ContentRootTestEntity(MySource) {
      module = moduleTestEntity
    }
    builder.addEntity(newContentRoot)
    builder.getMutableExternalMapping<Any>("data").addMapping(newContentRoot, 1)
    assertFalse(builder.hasSameEntities(original as AbstractEntityStorage))
  }

  // ------------- Testing events collapsing end ----
}