// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.workspaceModel.storage.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import org.jetbrains.deft.IntellijWsTestIj.modifyEntity
import org.junit.jupiter.api.BeforeEach
import kotlin.test.assertTrue

class WorkspaceBuilderChangeLogTest {

  internal lateinit var builder: WorkspaceEntityStorageBuilder
  internal lateinit var another: WorkspaceEntityStorageBuilder

  @BeforeEach
  fun setUp() {
    builder = createEmptyBuilder()
    another = createEmptyBuilder()
  }

  @Test
  fun `add plus delete`() {
    val entity = builder.addNamedEntity("Parent")
    builder.removeEntity(entity)

    val log = (builder as WorkspaceEntityStorageBuilderImpl).changeLog.changeLog
    assertEquals(0, log.size)
  }

  @Test
  fun `add plus modify`() {
    val entity = builder.addParentEntity("Parent")
    builder.modifyEntity(entity) {
      this.parentProperty = "Another Parent"
    }

    val log = (builder as WorkspaceEntityStorageBuilderImpl).changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.AddEntity)
    assertEquals("Another Parent", ((changeEntry as ChangeEntry.AddEntity).entityData as ParentEntityData).parentProperty)
  }

  @Test
  fun `add plus change source`() {
    val entity = builder.addParentEntity("Parent")
    builder.changeSource(entity, AnotherSource)

    val log = (builder as WorkspaceEntityStorageBuilderImpl).changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.AddEntity)
    assertEquals((changeEntry as ChangeEntry.AddEntity).entityData.entitySource, AnotherSource)
  }

  @Test
  fun `add plus change source and modify`() {
    val entity = builder.addParentEntity("Parent")
    builder.changeSource(entity, AnotherSource)
    builder.modifyEntity(entity) {
      this.parentProperty = "Another Parent"
    }

    val log = (builder as WorkspaceEntityStorageBuilderImpl).changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.AddEntity)
    assertEquals("Another Parent", ((changeEntry as ChangeEntry.AddEntity).entityData as ParentEntityData).parentProperty)
    assertEquals(changeEntry.entityData.entitySource, AnotherSource)
  }

  @Test
  fun `modify plus change source`() {
    val entity = builder.addParentEntity("Parent")
    (builder as WorkspaceEntityStorageBuilderImpl).changeLog.clear()
    builder.modifyEntity(entity) {
      this.parentProperty = "Another Parent"
    }
    builder.changeSource(entity, AnotherSource)

    val log = (builder as WorkspaceEntityStorageBuilderImpl).changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.ReplaceAndChangeSource)
    assertEquals(((changeEntry as ChangeEntry.ReplaceAndChangeSource).dataChange.newData as ParentEntityData).parentProperty,
                 "Another Parent")
    assertEquals(changeEntry.sourceChange.newData.entitySource, AnotherSource)
  }

  @Test
  fun `modify plus remove`() {
    val entity = builder.addParentEntity("Parent")
    (builder as WorkspaceEntityStorageBuilderImpl).changeLog.clear()
    builder.modifyEntity(entity) {
      this.parentProperty = "Another Parent"
    }
    builder.removeEntity(entity)

    val log = (builder as WorkspaceEntityStorageBuilderImpl).changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.RemoveEntity)
  }

  @Test
  fun `change source plus modify`() {
    val entity = builder.addParentEntity("Parent")
    (builder as WorkspaceEntityStorageBuilderImpl).changeLog.clear()
    builder.changeSource(entity, AnotherSource)
    builder.modifyEntity(entity) {
      this.parentProperty = "Another Parent"
    }

    val log = (builder as WorkspaceEntityStorageBuilderImpl).changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.ReplaceAndChangeSource)
    assertEquals(((changeEntry as ChangeEntry.ReplaceAndChangeSource).dataChange.newData as ParentEntityData).parentProperty,
                 "Another Parent")
    assertEquals(changeEntry.sourceChange.newData.entitySource, AnotherSource)
  }

  @Test
  fun `change source plus remove`() {
    val entity = builder.addParentEntity("Parent")
    (builder as WorkspaceEntityStorageBuilderImpl).changeLog.clear()
    builder.changeSource(entity, AnotherSource)
    builder.removeEntity(entity)

    val log = (builder as WorkspaceEntityStorageBuilderImpl).changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.RemoveEntity)
  }

  @Test
  fun `change source and modify plus remove`() {
    val entity = builder.addParentEntity("Parent")
    (builder as WorkspaceEntityStorageBuilderImpl).changeLog.clear()
    builder.changeSource(entity, AnotherSource)
    builder.modifyEntity(entity) {
      this.parentProperty = "Another Parent"
    }
    builder.removeEntity(entity)

    val log = (builder as WorkspaceEntityStorageBuilderImpl).changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.RemoveEntity)
  }

  @Test
  fun `change source and modify plus change source`() {
    val entity = builder.addParentEntity("Parent")
    (builder as WorkspaceEntityStorageBuilderImpl).changeLog.clear()
    builder.changeSource(entity, AnotherSource)
    builder.modifyEntity(entity) {
      this.parentProperty = "Another Parent"
    }
    builder.changeSource(entity, SampleEntitySource("X"))

    val log = (builder as WorkspaceEntityStorageBuilderImpl).changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.ReplaceAndChangeSource)
    assertEquals(((changeEntry as ChangeEntry.ReplaceAndChangeSource).dataChange.newData as ParentEntityData).parentProperty,
                 "Another Parent")
    assertEquals(changeEntry.sourceChange.newData.entitySource, SampleEntitySource("X"))
  }

  @Test
  fun `change source and modify plus modify`() {
    val entity = builder.addParentEntity("Parent")
    (builder as WorkspaceEntityStorageBuilderImpl).changeLog.clear()
    builder.changeSource(entity, AnotherSource)
    builder.modifyEntity(entity) {
      this.parentProperty = "Another Parent"
    }
    builder.modifyEntity(entity) {
      this.parentProperty = "Third Parent"
    }

    val log = (builder as WorkspaceEntityStorageBuilderImpl).changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.ReplaceAndChangeSource)
    assertEquals(((changeEntry as ChangeEntry.ReplaceAndChangeSource).dataChange.newData as ParentEntityData).parentProperty,
                 "Third Parent")
    assertEquals(changeEntry.sourceChange.newData.entitySource, AnotherSource)
  }

  @Test
  fun `modify add children`() {
    val entity = builder.addParentEntity("Parent")
    val firstChild = builder.addChildWithOptionalParentEntity(null)
    val secondChild = builder.addChildWithOptionalParentEntity(null)
    (builder as WorkspaceEntityStorageBuilderImpl).changeLog.clear()

    builder.modifyEntity(entity) {
      this.optionalChildren = listOf(firstChild, secondChild)
    }

    val log = (builder as WorkspaceEntityStorageBuilderImpl).changeLog.changeLog
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
    (builder as WorkspaceEntityStorageBuilderImpl).changeLog.clear()

    builder.modifyEntity(entity) {
      this.optionalChildren = listOf()
    }

    val log = (builder as WorkspaceEntityStorageBuilderImpl).changeLog.changeLog
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
    (builder as WorkspaceEntityStorageBuilderImpl).changeLog.clear()

    builder.modifyEntity(entity) {
      this.optionalChildren = listOf(child)
    }

    val log = (builder as WorkspaceEntityStorageBuilderImpl).changeLog.changeLog
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
    (builder as WorkspaceEntityStorageBuilderImpl).changeLog.clear()

    builder.modifyEntity(entity) {
      this.optionalChildren = listOf(firstChild, secondChild)
    }

    builder.modifyEntity(entity) {
      this.optionalChildren = listOf()
    }

    val log = (builder as WorkspaceEntityStorageBuilderImpl).changeLog.changeLog
    assertEquals(0, log.size)
  }

  @Test
  fun `modify twice remove and add children`() {
    val entity = builder.addParentEntity("Parent")
    val firstChild = builder.addChildWithOptionalParentEntity(entity)
    val secondChild = builder.addChildWithOptionalParentEntity(entity)
    (builder as WorkspaceEntityStorageBuilderImpl).changeLog.clear()

    builder.modifyEntity(entity) {
      this.optionalChildren = listOf()
    }

    builder.modifyEntity(entity) {
      this.optionalChildren = listOf(firstChild, secondChild)
    }

    val log = (builder as WorkspaceEntityStorageBuilderImpl).changeLog.changeLog
    assertEquals(0, log.size)
  }

  @Test
  fun `modify twice remove and remove children`() {
    val entity = builder.addParentEntity("Parent")
    val firstChild = builder.addChildWithOptionalParentEntity(entity)
    builder.addChildWithOptionalParentEntity(entity)
    (builder as WorkspaceEntityStorageBuilderImpl).changeLog.clear()

    builder.modifyEntity(entity) {
      this.optionalChildren = listOf(firstChild)
    }

    builder.modifyEntity(entity) {
      this.optionalChildren = listOf()
    }

    val log = (builder as WorkspaceEntityStorageBuilderImpl).changeLog.changeLog
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
    (builder as WorkspaceEntityStorageBuilderImpl).changeLog.clear()

    builder.modifyEntity(entity) {
      this.optionalChildren = listOf(firstChild)
    }

    builder.modifyEntity(entity) {
      this.optionalChildren = listOf(firstChild, secondChild)
    }

    val log = (builder as WorkspaceEntityStorageBuilderImpl).changeLog.changeLog
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
    (builder as WorkspaceEntityStorageBuilderImpl).changeLog.clear()

    builder.modifyEntity(entity) {}

    val log = (builder as WorkspaceEntityStorageBuilderImpl).changeLog.changeLog
    assertEquals(0, log.size)
  }

  @Test
  fun `collaps two modify`() {
    val entity = builder.addNamedEntity("Parent")
    (builder as WorkspaceEntityStorageBuilderImpl).changeLog.clear()

    builder.modifyEntity(entity) {
      myName = "AnotherName"
    }
    builder.modifyEntity(entity) {
      myName = "Parent"
    }

    val log = (builder as WorkspaceEntityStorageBuilderImpl).changeLog.changeLog
    assertEquals(0, log.size)
  }

  @Test
  fun `collaps two modify with parent refs`() {
    val parent1 = builder.addNamedEntity("Parent")
    val parent2 = builder.addNamedEntity("Parent2")
    val child1 = builder.addNamedChildEntity(parent1)
    (builder as WorkspaceEntityStorageBuilderImpl).changeLog.clear()

    builder.modifyEntity(child1) {
      this.parentEntity = parent2
    }

    builder.modifyEntity(child1) {
      this.parentEntity = parent1
    }

    val log = (builder as WorkspaceEntityStorageBuilderImpl).changeLog.changeLog
    assertEquals(0, log.size)
  }

  @Test
  fun `collaps two modify with children refs`() {
    val parent = builder.addParentEntity()
    val child1 = builder.addChildWithOptionalParentEntity(parent)
    val child2 = builder.addChildWithOptionalParentEntity(parent)
    val child3 = builder.addChildWithOptionalParentEntity(parent)
    (builder as WorkspaceEntityStorageBuilderImpl).changeLog.clear()

    builder.modifyEntity(parent) {
      this.optionalChildren = emptyList()
    }

    builder.modifyEntity(parent) {
      this.optionalChildren = listOf(child1, child2, child3)
    }

    val log = (builder as WorkspaceEntityStorageBuilderImpl).changeLog.changeLog
    assertEquals(0, log.size)
  }

  @Test
  fun `collaps three modify`() {
    val entity = builder.addNamedEntity("Parent")
    (builder as WorkspaceEntityStorageBuilderImpl).changeLog.clear()

    builder.modifyEntity(entity) {
      myName = "AnotherName"
    }
    builder.modifyEntity(entity) {
      myName = "AndAnotherName"
    }
    builder.modifyEntity(entity) {
      myName = "Parent"
    }

    val log = (builder as WorkspaceEntityStorageBuilderImpl).changeLog.changeLog
    assertEquals(0, log.size)
  }

  @Test
  fun `collaps two modify and source change`() {
    val entity = builder.addNamedEntity("Parent")
    (builder as WorkspaceEntityStorageBuilderImpl).changeLog.clear()

    builder.modifyEntity(entity) {
      myName = "AnotherName"
    }
    builder.changeSource(entity, AnotherSource)
    builder.modifyEntity(entity) {
      myName = "Parent"
    }

    val log = (builder as WorkspaceEntityStorageBuilderImpl).changeLog.changeLog
    assertEquals(1, log.size)
    assertTrue(log.entries.single ().value is ChangeEntry.ChangeEntitySource)
  }

  @Test
  fun `collaps two modify and two source change`() {
    val entity = builder.addNamedEntity("Parent")
    (builder as WorkspaceEntityStorageBuilderImpl).changeLog.clear()

    builder.modifyEntity(entity) {
      myName = "AnotherName"
    }
    builder.changeSource(entity, AnotherSource)
    builder.changeSource(entity, MySource)
    builder.modifyEntity(entity) {
      myName = "Parent"
    }

    val log = (builder as WorkspaceEntityStorageBuilderImpl).changeLog.changeLog
    assertEquals(0, log.size)
  }

  @Test
  fun `collaps two modify and two source change in mix`() {
    val entity = builder.addNamedEntity("Parent")
    (builder as WorkspaceEntityStorageBuilderImpl).changeLog.clear()

    builder.modifyEntity(entity) {
      myName = "AnotherName"
    }
    builder.changeSource(entity, AnotherSource)
    builder.modifyEntity(entity) {
      myName = "Parent"
    }
    builder.changeSource(entity, MySource)

    val log = (builder as WorkspaceEntityStorageBuilderImpl).changeLog.changeLog
    assertEquals(0, log.size)
  }

  @Test
  fun `collaps empty source change`() {
    val entity = builder.addNamedEntity("Parent", source = MySource)
    (builder as WorkspaceEntityStorageBuilderImpl).changeLog.clear()

    builder.changeSource(entity, MySource)

    val log = (builder as WorkspaceEntityStorageBuilderImpl).changeLog.changeLog
    assertEquals(0, log.size)
  }

  @Test
  fun `collaps source change twice`() {
    val entity = builder.addNamedEntity("Parent", source = MySource)
    (builder as WorkspaceEntityStorageBuilderImpl).changeLog.clear()

    builder.changeSource(entity, AnotherSource)
    builder.changeSource(entity, MySource)

    val log = (builder as WorkspaceEntityStorageBuilderImpl).changeLog.changeLog
    assertEquals(0, log.size)
  }

  // ------------- Testing events collapsing end ----
}