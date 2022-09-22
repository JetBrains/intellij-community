// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.testFramework.UsefulTestCase.assertOneElement
import com.intellij.workspaceModel.storage.entities.test.*
import com.intellij.workspaceModel.storage.entities.test.api.*
import com.intellij.workspaceModel.storage.impl.EntityStorageSnapshotImpl
import com.intellij.workspaceModel.storage.impl.assertConsistency
import com.intellij.workspaceModel.storage.impl.exceptions.AddDiffException
import com.intellij.workspaceModel.storage.impl.external.ExternalEntityMappingImpl
import org.hamcrest.CoreMatchers
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

private fun MutableEntityStorage.applyDiff(anotherBuilder: MutableEntityStorage): EntityStorage {
  val builder = createBuilderFrom(this)
  builder.addDiff(anotherBuilder)
  val storage =  builder.toSnapshot() as EntityStorageSnapshotImpl
  storage.assertConsistency()
  return storage
}

class DiffBuilderTest {
  @JvmField
  @Rule
  val expectedException = ExpectedException.none()

  @Test
  fun `add entity`() {
    val source = createEmptyBuilder()
    source.addSampleEntity("first")
    val target = createEmptyBuilder()
    target.addSampleEntity("second")
    val storage = target.applyDiff(source)
    assertEquals(setOf("first", "second"), storage.entities(SampleEntity::class.java).mapTo(HashSet()) { it.stringProperty })
  }

  @Test
  fun `remove entity`() {
    val target = createEmptyBuilder()
    val entity = target.addSampleEntity("hello")
    val entity2 = target.addSampleEntity("hello")
    val source = createBuilderFrom(target.toSnapshot())
    source.removeEntity(entity.from(source))
    val storage = target.applyDiff(source)
    assertEquals(entity2, storage.singleSampleEntity())
  }

  @Test
  fun `modify entity`() {
    val target = createEmptyBuilder()
    val entity = target.addSampleEntity("hello")
    val source = createBuilderFrom(target.toSnapshot())
    source.modifyEntity(entity.from(source)) {
      stringProperty = "changed"
    }
    val storage = target.applyDiff(source)
    assertEquals("changed", storage.singleSampleEntity().stringProperty)
  }

  @Test
  fun `remove removed entity`() {
    val target = createEmptyBuilder()
    val entity = target.addSampleEntity("hello")
    val entity2 = target.addSampleEntity("hello")
    val source = createBuilderFrom(target.toSnapshot())
    target.removeEntity(entity)
    target.assertConsistency()
    source.assertConsistency()
    source.removeEntity(entity.from(source))
    val storage = target.applyDiff(source)
    assertEquals(entity2, storage.singleSampleEntity())
  }

  @Test
  fun `modify removed entity`() {
    val target = createEmptyBuilder()
    val entity = target.addSampleEntity("hello")
    val source = createBuilderFrom(target.toSnapshot())
    target.removeEntity(entity)
    source.assertConsistency()
    source.modifyEntity(entity.from(source)) {
      stringProperty = "changed"
    }
    val storage = target.applyDiff(source)
    assertEquals(emptyList<SampleEntity>(), storage.entities(SampleEntity::class.java).toList())
  }

  @Test
  fun `modify removed child entity`() {
    val target = createEmptyBuilder()
    val parent = target.addParentEntity("parent")
    val child = target.addChildEntity(parent, "child")
    val source = createBuilderFrom(target)
    target.removeEntity(child)
    source.modifyEntity(parent.from(source)) {
      this.parentProperty = "new property"
    }
    source.modifyEntity(child.from(source)) {
      this.childProperty = "new property"
    }

    val res = target.applyDiff(source) as EntityStorageSnapshotImpl
    res.assertConsistency()

    assertOneElement(res.entities(XParentEntity::class.java).toList())
    assertTrue(res.entities(XChildEntity::class.java).toList().isEmpty())
  }

  @Test
  fun `remove modified entity`() {
    val target = createEmptyBuilder()
    val entity = target.addSampleEntity("hello")
    val source = createBuilderFrom(target.toSnapshot())
    target.modifyEntity(entity) {
      stringProperty = "changed"
    }
    source.removeEntity(entity.from(source))
    source.assertConsistency()
    val storage = target.applyDiff(source)
    assertEquals(emptyList<SampleEntity>(), storage.entities(SampleEntity::class.java).toList())
  }

  @Test
  fun `add entity with refs at the same slot`() {
    val target = createEmptyBuilder()
    val source = createEmptyBuilder()
    source.addSampleEntity("Another entity")
    val parentEntity = target.addSampleEntity("hello")
    target.addChildSampleEntity("data", parentEntity)

    source.addDiff(target)
    source.assertConsistency()

    val resultingStorage = source.toSnapshot()
    assertEquals(2, resultingStorage.entities(SampleEntity::class.java).toList().size)
    assertEquals(1, resultingStorage.entities(ChildSampleEntity::class.java).toList().size)

    assertEquals(resultingStorage.entities(SampleEntity::class.java).last(), resultingStorage.entities(ChildSampleEntity::class.java).single().parentEntity)
  }

  @Test
  fun `add remove and add with refs`() {
    val source = createEmptyBuilder()
    val target = createEmptyBuilder()
    val parent = source.addSampleEntity("Another entity")
    source.addChildSampleEntity("String", parent)

    val parentEntity = target.addSampleEntity("hello")
    target.addChildSampleEntity("data", parentEntity)

    source.addDiff(target)
    source.assertConsistency()

    val resultingStorage = source.toSnapshot()
    assertEquals(2, resultingStorage.entities(SampleEntity::class.java).toList().size)
    assertEquals(2, resultingStorage.entities(ChildSampleEntity::class.java).toList().size)

    assertNotNull(resultingStorage.entities(ChildSampleEntity::class.java).first().parentEntity)
    assertNotNull(resultingStorage.entities(ChildSampleEntity::class.java).last().parentEntity)

    assertEquals(resultingStorage.entities(SampleEntity::class.java).first(), resultingStorage.entities(ChildSampleEntity::class.java).first().parentEntity)
    assertEquals(resultingStorage.entities(SampleEntity::class.java).last(), resultingStorage.entities(ChildSampleEntity::class.java).last().parentEntity)
  }

  @Test
  fun `add dependency without changing entities`() {
    val source = createEmptyBuilder()
    val parent = source.addSampleEntity("Another entity")
    source.addChildSampleEntity("String", null)

    val target = createBuilderFrom(source)
    val pchild = target.entities(ChildSampleEntity::class.java).single()
    val pparent = target.entities(SampleEntity::class.java).single()
    target.modifyEntity(pchild) {
      this.parentEntity = pparent
    }

    source.addDiff(target)
    source.assertConsistency()

    val resultingStorage = source.toSnapshot()
    assertEquals(1, resultingStorage.entities(SampleEntity::class.java).toList().size)
    assertEquals(1, resultingStorage.entities(ChildSampleEntity::class.java).toList().size)

    assertEquals(resultingStorage.entities(SampleEntity::class.java).single(), resultingStorage.entities(ChildSampleEntity::class.java).single().parentEntity)
  }

  @Test
  fun `dependency to removed parent`() {
    val source = createEmptyBuilder()
    val parent = source.addParentEntity()

    val target = createBuilderFrom(source)
    target.addChildWithOptionalParentEntity(parent)
    source.removeEntity(parent)

    source.applyDiff(target)
  }

  @Test
  fun `modify child and parent`() {
    val source = createEmptyBuilder()
    val parent = source.addParentEntity()
    source.addChildEntity(parent)

    val target = createBuilderFrom(source)
    target.modifyEntity(parent.from(target)) {
      this.parentProperty = "anotherValue"
    }
    source.addChildEntity(parent)

    source.applyDiff(target)
  }

  @Test
  fun `remove parent in both difs with dependency`() {
    val source = createEmptyBuilder()
    val parent = source.addParentEntity()

    val target = createBuilderFrom(source)
    target.addChildWithOptionalParentEntity(parent)
    target.removeEntity(parent.from(target))
    source.removeEntity(parent)

    source.applyDiff(target)
  }

  @Test
  fun `remove parent in both diffs`() {
    val source = createEmptyBuilder()
    val parent = source.addParentEntity()
    val optionalChild = source.addChildWithOptionalParentEntity(null)

    val target = createBuilderFrom(source)
    target.modifyEntity(optionalChild.from(target)) {
      this.optionalParent = parent
    }

    source.removeEntity(parent)

    source.applyDiff(target)
  }

  @Test
  fun `adding duplicated persistent ids`() {
    expectedException.expectCause(CoreMatchers.isA(AddDiffException::class.java))

    val source = createEmptyBuilder()
    val target = createBuilderFrom(source)

    target.addNamedEntity("Name")
    source.addNamedEntity("Name")

    source.applyDiff(target)
  }

  @Test
  fun `modifying duplicated persistent ids`() {
    expectedException.expectCause(CoreMatchers.isA(AddDiffException::class.java))

    val source = createEmptyBuilder()
    val namedEntity = source.addNamedEntity("Hello")
    val target = createBuilderFrom(source)

    source.addNamedEntity("Name")
    target.modifyEntity(namedEntity.from(target)) {
      this.myName = "Name"
    }

    source.applyDiff(target)
  }

  @Test
  fun `checking external mapping`() {
    val target = createEmptyBuilder()

    target.addSampleEntity("Entity at index 0")

    val source = createEmptyBuilder()
    val sourceSample = source.addSampleEntity("Entity at index 1")
    val mutableExternalMapping = source.getMutableExternalMapping<Any>("test.checking.external.mapping")
    val anyObj = Any()
    mutableExternalMapping.addMapping(sourceSample, anyObj)

    target.addDiff(source)

    val externalMapping = target.getExternalMapping<Any>("test.checking.external.mapping") as ExternalEntityMappingImpl<Any>
    assertEquals(1, externalMapping.index.size)
  }

  @Test
  fun `change source in diff`() {
    val target = createEmptyBuilder()
    val sampleEntity = target.addSampleEntity("Prop", MySource)

    val source = createBuilderFrom(target)
    source.modifyEntity(sampleEntity.from(source)) {
      this.entitySource = AnotherSource
    }

    target.addDiff(source)

    target.assertConsistency()

    val entitySourceIndex = target.indexes.entitySourceIndex
    assertEquals(1, entitySourceIndex.index.size)
    assertNotNull(entitySourceIndex.getIdsByEntry(AnotherSource)?.single())
  }

  @Test
  fun `change source and data in diff`() {
    val target = createEmptyBuilder()
    val sampleEntity = target.addSampleEntity("Prop", MySource)

    val source = createBuilderFrom(target)
    source.modifyEntity(sampleEntity.from(source)) {
      this.entitySource = AnotherSource
    }
    source.modifyEntity(sampleEntity.from(source)) {
      stringProperty = "Prop2"
    }

    target.addDiff(source)

    target.assertConsistency()

    val entitySourceIndex = target.indexes.entitySourceIndex
    assertEquals(1, entitySourceIndex.index.size)
    assertNotNull(entitySourceIndex.getIdsByEntry(AnotherSource)?.single())

    val updatedEntity = target.entities(SampleEntity::class.java).single()
    assertEquals("Prop2", updatedEntity.stringProperty)
    assertEquals(AnotherSource, updatedEntity.entitySource)
  }

  @Test
  fun `change source in target`() {
    val target = createEmptyBuilder()
    val sampleEntity = target.addSampleEntity("Prop", MySource)

    val source = createBuilderFrom(target)
    target.modifyEntity(sampleEntity) {
      this.entitySource = AnotherSource
    }

    source.modifyEntity(sampleEntity.from(source)) {
      this.stringProperty = "Updated"
    }

    target.addDiff(source)

    target.assertConsistency()

    val entitySourceIndex = target.indexes.entitySourceIndex
    assertEquals(1, entitySourceIndex.index.size)
    assertNotNull(entitySourceIndex.getIdsByEntry(AnotherSource)?.single())
  }

  @Test
  fun `adding parent with child and shifting`() {
    val parentAndChildProperty = "Bound"
    val target = createEmptyBuilder()
    target.addChildWithOptionalParentEntity(null, "Existing")

    val source = createEmptyBuilder()
    val parent = source.addParentEntity(parentAndChildProperty)
    source.addChildWithOptionalParentEntity(parent, parentAndChildProperty)

    target.addDiff(source)

    val extractedParent = assertOneElement(target.entities(XParentEntity::class.java).toList())
    val extractedOptionalChild = assertOneElement(extractedParent.optionalChildren.toList())
    assertEquals(parentAndChildProperty, extractedOptionalChild.childProperty)
  }

  @Test
  fun `adding parent with child and shifting and later connecting`() {
    val parentAndChildProperty = "Bound"
    val target = createEmptyBuilder()
    target.addChildWithOptionalParentEntity(null, "Existing")

    val source = createEmptyBuilder()
    val child = source.addChildWithOptionalParentEntity(null, parentAndChildProperty)
    val parent = source.addParentEntity(parentAndChildProperty)
    source.modifyEntity(parent) {
      this.optionalChildren = this.optionalChildren + child
    }

    target.addDiff(source)

    val extractedParent = assertOneElement(target.entities(XParentEntity::class.java).toList())
    val extractedOptionalChild = assertOneElement(extractedParent.optionalChildren.toList())
    assertEquals(parentAndChildProperty, extractedOptionalChild.childProperty)
  }

  @Test
  fun `adding parent with child and shifting and later child connecting`() {
    val parentAndChildProperty = "Bound"
    val target = createEmptyBuilder()
    target.addChildWithOptionalParentEntity(null, "Existing")

    val source = createEmptyBuilder()
    val child = source.addChildWithOptionalParentEntity(null, parentAndChildProperty)
    val parent = source.addParentEntity(parentAndChildProperty)
    source.modifyEntity(child) {
      this.optionalParent = parent
    }

    target.addDiff(source)

    val extractedParent = assertOneElement(target.entities(XParentEntity::class.java).toList())
    val extractedOptionalChild = assertOneElement(extractedParent.optionalChildren.toList())
    assertEquals(parentAndChildProperty, extractedOptionalChild.childProperty)
  }

  @Test
  fun `removing non-existing entity while adding the new one`() {
    val initial = createEmptyBuilder()
    val toBeRemoved = initial.addSampleEntity("En1")

    val source = createBuilderFrom(initial)

    initial.removeEntity(toBeRemoved)
    val target = createBuilderFrom(initial.toSnapshot())

    // In the incorrect implementation remove event will remove added entity
    source.addSampleEntity("En2")
    source.removeEntity(toBeRemoved.from(source))

    target.addDiff(source)

    assertOneElement(target.entities(SampleEntity::class.java).toList())
  }

  @Test
  fun `remove entity and reference`() {
    val initial = createEmptyBuilder()
    val parentEntity = initial.addParentEntity()
    val childEntity = initial.addChildWithOptionalParentEntity(parentEntity)

    val source = createBuilderFrom(initial)

    source.modifyEntity(childEntity.from(source)) {
      this.childProperty = "newProp"
    }

    source.modifyEntity(parentEntity.from(source)) {
      this.optionalChildren = emptyList()
    }

    source.removeEntity(childEntity.from(source))

    val res = initial.applyDiff(source)

    assertTrue(res.entities(XChildWithOptionalParentEntity::class.java).toList().isEmpty())
    val newParent = assertOneElement(res.entities(XParentEntity::class.java).toList())
    assertTrue(newParent.optionalChildren.toList().isEmpty())
  }

  @Test
  fun `remove reference to created entity`() {
    val initial = createEmptyBuilder()
    val parentEntity = initial.addParentEntity()
    initial.addChildWithOptionalParentEntity(parentEntity)
    val source = createBuilderFrom(initial)

    source.addChildWithOptionalParentEntity(parentEntity)

    source.modifyEntity(parentEntity.from(source)) {
      this.optionalChildren = emptyList()
    }

    val res = initial.applyDiff(source)

    assertEquals(2, res.entities(XChildWithOptionalParentEntity::class.java).toList().size)
    val newParent = assertOneElement(res.entities(XParentEntity::class.java).toList())
    assertTrue(newParent.optionalChildren.toList().isEmpty())
  }

  @Test
  fun `add parent with children`() {
    val target = createEmptyBuilder()
    target addEntity ParentMultipleEntity("Parent", MySource)
    val source = createBuilderFrom(target)
    source.modifyEntity(source.entities(ParentMultipleEntity::class.java).single()) {
      this.children = listOf(
        ChildMultipleEntity("child1", MySource),
        ChildMultipleEntity("child2", MySource),
      )
    }

    val result = target.applyDiff(source)

    assertEquals(2, result.entities(ParentMultipleEntity::class.java).single().children.size)
  }

  @Test
  fun `add parent with children 2`() {
    val target = createEmptyBuilder()
    target addEntity ParentMultipleEntity("Parent", MySource)
    val source = createBuilderFrom(target)
    val parentToModify = source.toSnapshot().entities(ParentMultipleEntity::class.java).single()
    source.modifyEntity(parentToModify) {
      this.children = listOf(
        ChildMultipleEntity("child1", MySource),
        ChildMultipleEntity("child2", MySource),
      )
    }

    val result = target.applyDiff(source)

    assertEquals(2, result.entities(ParentMultipleEntity::class.java).single().children.size)
  }
}
