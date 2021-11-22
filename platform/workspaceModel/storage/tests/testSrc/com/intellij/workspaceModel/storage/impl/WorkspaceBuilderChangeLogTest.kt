// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.workspaceModel.storage.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WorkspaceBuilderChangeLogTest {

  internal lateinit var builder: WorkspaceEntityStorageBuilderImpl
  internal lateinit var another: WorkspaceEntityStorageBuilderImpl

  @Before
  fun setUp() {
    builder = createEmptyBuilder()
    another = createEmptyBuilder()
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
    builder.modifyEntity(ModifiableParentEntity::class.java, entity) {
      this.parentProperty = "Another Parent"
    }

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.AddEntity<*>)
    assertEquals("Another Parent", ((changeEntry as ChangeEntry.AddEntity<*>).entityData as ParentEntityData).parentProperty)
  }

  @Test
  fun `add plus change source`() {
    val entity = builder.addParentEntity("Parent")
    builder.changeSource(entity, AnotherSource)

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.AddEntity<*>)
    assertEquals((changeEntry as ChangeEntry.AddEntity<*>).entityData.entitySource, AnotherSource)
  }

  @Test
  fun `add plus change source and modify`() {
    val entity = builder.addParentEntity("Parent")
    builder.changeSource(entity, AnotherSource)
    builder.modifyEntity(ModifiableParentEntity::class.java, entity) {
      this.parentProperty = "Another Parent"
    }

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.AddEntity<*>)
    assertEquals("Another Parent", ((changeEntry as ChangeEntry.AddEntity<*>).entityData as ParentEntityData).parentProperty)
    assertEquals(changeEntry.entityData.entitySource, AnotherSource)
  }

  @Test
  fun `modify plus change source`() {
    val entity = builder.addParentEntity("Parent")
    builder.changeLog.clear()
    builder.modifyEntity(ModifiableParentEntity::class.java, entity) {
      this.parentProperty = "Another Parent"
    }
    builder.changeSource(entity, AnotherSource)

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.ReplaceAndChangeSource<*>)
    assertEquals(((changeEntry as ChangeEntry.ReplaceAndChangeSource<*>).dataChange.newData as ParentEntityData).parentProperty,
                 "Another Parent")
    assertEquals(changeEntry.sourceChange.newData.entitySource, AnotherSource)
  }

  @Test
  fun `modify plus remove`() {
    val entity = builder.addParentEntity("Parent")
    builder.changeLog.clear()
    builder.modifyEntity(ModifiableParentEntity::class.java, entity) {
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
    builder.changeSource(entity, AnotherSource)
    builder.modifyEntity(ModifiableParentEntity::class.java, entity) {
      this.parentProperty = "Another Parent"
    }

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.ReplaceAndChangeSource<*>)
    assertEquals(((changeEntry as ChangeEntry.ReplaceAndChangeSource<*>).dataChange.newData as ParentEntityData).parentProperty,
                 "Another Parent")
    assertEquals(changeEntry.sourceChange.newData.entitySource, AnotherSource)
  }

  @Test
  fun `change source plus remove`() {
    val entity = builder.addParentEntity("Parent")
    builder.changeLog.clear()
    builder.changeSource(entity, AnotherSource)
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
    builder.changeSource(entity, AnotherSource)
    builder.modifyEntity(ModifiableParentEntity::class.java, entity) {
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
    builder.changeSource(entity, AnotherSource)
    builder.modifyEntity(ModifiableParentEntity::class.java, entity) {
      this.parentProperty = "Another Parent"
    }
    builder.changeSource(entity, MySource)

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.ReplaceAndChangeSource<*>)
    assertEquals(((changeEntry as ChangeEntry.ReplaceAndChangeSource<*>).dataChange.newData as ParentEntityData).parentProperty,
                 "Another Parent")
    assertEquals(changeEntry.sourceChange.newData.entitySource, MySource)
  }

  @Test
  fun `change source and modify plus modify`() {
    val entity = builder.addParentEntity("Parent")
    builder.changeLog.clear()
    builder.changeSource(entity, AnotherSource)
    builder.modifyEntity(ModifiableParentEntity::class.java, entity) {
      this.parentProperty = "Another Parent"
    }
    builder.modifyEntity(ModifiableParentEntity::class.java, entity) {
      this.parentProperty = "Third Parent"
    }

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.ReplaceAndChangeSource<*>)
    assertEquals(((changeEntry as ChangeEntry.ReplaceAndChangeSource<*>).dataChange.newData as ParentEntityData).parentProperty,
                 "Third Parent")
    assertEquals(changeEntry.sourceChange.newData.entitySource, AnotherSource)
  }

  @Test
  fun `modify add children`() {
    val entity = builder.addParentEntity("Parent")
    val firstChild = builder.addChildWithOptionalParentEntity(null)
    val secondChild = builder.addChildWithOptionalParentEntity(null)
    builder.changeLog.clear()

    builder.modifyEntity(ModifiableParentEntity::class.java, entity) {
      this.optionalChildren = sequenceOf(firstChild, secondChild)
    }

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.ReplaceEntity<*>)
    changeEntry as ChangeEntry.ReplaceEntity<*>
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

    builder.modifyEntity(ModifiableParentEntity::class.java, entity) {
      this.optionalChildren = sequenceOf()
    }

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.ReplaceEntity<*>)
    changeEntry as ChangeEntry.ReplaceEntity<*>
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

    builder.modifyEntity(ModifiableParentEntity::class.java, entity) {
      this.optionalChildren = sequenceOf(child)
    }

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.ReplaceEntity<*>)
    changeEntry as ChangeEntry.ReplaceEntity<*>
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

    builder.modifyEntity(ModifiableParentEntity::class.java, entity) {
      this.optionalChildren = sequenceOf(firstChild, secondChild)
    }

    builder.modifyEntity(ModifiableParentEntity::class.java, entity) {
      this.optionalChildren = sequenceOf()
    }

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.ReplaceEntity<*>)
    changeEntry as ChangeEntry.ReplaceEntity<*>
    assertEquals(0, changeEntry.removedChildren.size)
    assertEquals(0, changeEntry.newChildren.size)
    assertEquals(0, changeEntry.modifiedParents.size)
  }

  @Test
  fun `modify twice remove and add children`() {
    val entity = builder.addParentEntity("Parent")
    val firstChild = builder.addChildWithOptionalParentEntity(entity)
    val secondChild = builder.addChildWithOptionalParentEntity(entity)
    builder.changeLog.clear()

    builder.modifyEntity(ModifiableParentEntity::class.java, entity) {
      this.optionalChildren = sequenceOf()
    }

    builder.modifyEntity(ModifiableParentEntity::class.java, entity) {
      this.optionalChildren = sequenceOf(firstChild, secondChild)
    }

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.ReplaceEntity<*>)
    changeEntry as ChangeEntry.ReplaceEntity<*>
    assertEquals(0, changeEntry.removedChildren.size)
    assertEquals(0, changeEntry.newChildren.size)
    assertEquals(0, changeEntry.modifiedParents.size)
  }

  @Test
  fun `modify twice remove and remove children`() {
    val entity = builder.addParentEntity("Parent")
    val firstChild = builder.addChildWithOptionalParentEntity(entity)
    builder.addChildWithOptionalParentEntity(entity)
    builder.changeLog.clear()

    builder.modifyEntity(ModifiableParentEntity::class.java, entity) {
      this.optionalChildren = sequenceOf(firstChild)
    }

    builder.modifyEntity(ModifiableParentEntity::class.java, entity) {
      this.optionalChildren = sequenceOf()
    }

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.ReplaceEntity<*>)
    changeEntry as ChangeEntry.ReplaceEntity<*>
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

    builder.modifyEntity(ModifiableParentEntity::class.java, entity) {
      this.optionalChildren = sequenceOf(firstChild)
    }

    builder.modifyEntity(ModifiableParentEntity::class.java, entity) {
      this.optionalChildren = sequenceOf(firstChild, secondChild)
    }

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.ReplaceEntity<*>)
    changeEntry as ChangeEntry.ReplaceEntity<*>
    assertEquals(0, changeEntry.removedChildren.size)
    assertEquals(2, changeEntry.newChildren.size)
    assertEquals(0, changeEntry.modifiedParents.size)
  }
}