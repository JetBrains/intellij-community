// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.testFramework.UsefulTestCase.assertOneElement
import com.intellij.workspaceModel.storage.entities.test.*
import com.intellij.workspaceModel.storage.entities.test.api.*
import com.intellij.workspaceModel.storage.impl.EntityStorageSnapshotImpl
import com.intellij.workspaceModel.storage.impl.MutableEntityStorageImpl
import com.intellij.workspaceModel.storage.impl.assertConsistency
import com.intellij.workspaceModel.storage.impl.exceptions.AddDiffException
import com.intellij.workspaceModel.storage.impl.external.ExternalEntityMappingImpl
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.RepetitionInfo
import org.junit.jupiter.api.assertThrows
import java.util.*
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DiffBuilderTest {
  private lateinit var target: MutableEntityStorageImpl
  private var shaker = -1L

  private fun MutableEntityStorage.applyDiff(anotherBuilder: MutableEntityStorage): EntityStorage {
    val builder = createBuilderFrom(this)
    builder.upgradeAddDiffEngine = { it.shaker = shaker }
    builder.addDiff(anotherBuilder)
    val storage = builder.toSnapshot() as EntityStorageSnapshotImpl
    storage.assertConsistency()
    return storage
  }

  @BeforeEach
  internal fun setUp(info: RepetitionInfo) {
    target = createEmptyBuilder()
    // Random returns same result for nextInt(2) for the first 4095 seeds, so we generated random seed
    shaker = Random(info.currentRepetition.toLong()).nextLong()
    target.upgradeAddDiffEngine = { it.shaker = shaker }
  }

  @RepeatedTest(10)
  fun `add entity`() {
    val source = createEmptyBuilder()
    source.addSampleEntity("first")
    target.addSampleEntity("second")
    val storage = target.applyDiff(source)
    assertEquals(setOf("first", "second"), storage.entities(SampleEntity::class.java).mapTo(HashSet()) { it.stringProperty })
  }

  @RepeatedTest(10)
  fun `remove entity`() {
    val entity = target.addSampleEntity("hello")
    val entity2 = target.addSampleEntity("hello")
    val source = createBuilderFrom(target.toSnapshot())
    source.removeEntity(entity.from(source))
    val storage = target.applyDiff(source)
    assertEquals(entity2, storage.singleSampleEntity())
  }

  @RepeatedTest(10)
  fun `modify entity`() {
    val entity = target.addSampleEntity("hello")
    val source = createBuilderFrom(target.toSnapshot())
    source.modifyEntity(entity.from(source)) {
      stringProperty = "changed"
    }
    val storage = target.applyDiff(source)
    assertEquals("changed", storage.singleSampleEntity().stringProperty)
  }

  @RepeatedTest(10)
  fun `remove removed entity`() {
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

  @RepeatedTest(10)
  fun `modify removed entity`() {
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

  @RepeatedTest(10)
  fun `modify removed child entity`() {
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

  @RepeatedTest(10)
  fun `remove modified entity`() {
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

  @RepeatedTest(10)
  fun `add entity with refs at the same slot`() {
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

  @RepeatedTest(10)
  fun `add remove and add with refs`() {
    val source = createEmptyBuilder()
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

  @RepeatedTest(10)
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

  @RepeatedTest(10)
  fun `dependency to removed parent`() {
    val source = createEmptyBuilder()
    val parent = source.addParentEntity()

    val target = createBuilderFrom(source)
    target.addChildWithOptionalParentEntity(parent)
    source.removeEntity(parent)

    source.applyDiff(target)
  }

  @RepeatedTest(10)
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

  @RepeatedTest(10)
  fun `remove parent in both difs with dependency`() {
    val source = createEmptyBuilder()
    val parent = source.addParentEntity()

    val target = createBuilderFrom(source)
    target.addChildWithOptionalParentEntity(parent)
    target.removeEntity(parent.from(target))
    source.removeEntity(parent)

    source.applyDiff(target)
  }

  @RepeatedTest(10)
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

  @RepeatedTest(10)
  fun `adding duplicated persistent ids`() {
    val source = createEmptyBuilder()
    val target = createBuilderFrom(source)

    target.addNamedEntity("Name")
    source.addNamedEntity("Name")

    val thrown = assertThrows<Throwable> {
      source.applyDiff(target)
    }
    assertEquals(thrown.cause!!.javaClass, AddDiffException::class.java)
  }

  @RepeatedTest(10)
  fun `modifying duplicated persistent ids`() {
    val source = createEmptyBuilder()
    val namedEntity = source.addNamedEntity("Hello")
    val target = createBuilderFrom(source)

    source.addNamedEntity("Name")
    target.modifyEntity(namedEntity.from(target)) {
      this.myName = "Name"
    }

    val thrown = assertThrows<Throwable> {
      source.applyDiff(target)
    }
    assertEquals(thrown.cause!!.javaClass, AddDiffException::class.java)
  }

  @RepeatedTest(10)
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

  @RepeatedTest(10)
  fun `change source in diff`() {
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

  @RepeatedTest(10)
  fun `change source and data in diff`() {
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

  @RepeatedTest(10)
  fun `change source in target`() {
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

  @RepeatedTest(10)
  fun `adding parent with child and shifting`() {
    val parentAndChildProperty = "Bound"
    target.addChildWithOptionalParentEntity(null, "Existing")

    val source = createEmptyBuilder()
    val parent = source.addParentEntity(parentAndChildProperty)
    source.addChildWithOptionalParentEntity(parent, parentAndChildProperty)

    target.addDiff(source)

    val extractedParent = assertOneElement(target.entities(XParentEntity::class.java).toList())
    val extractedOptionalChild = assertOneElement(extractedParent.optionalChildren.toList())
    assertEquals(parentAndChildProperty, extractedOptionalChild.childProperty)
  }

  @RepeatedTest(10)
  fun `adding parent with child and shifting and later connecting`() {
    val parentAndChildProperty = "Bound"
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

  @RepeatedTest(10)
  fun `adding parent with child and shifting and later child connecting`() {
    val parentAndChildProperty = "Bound"
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

  @RepeatedTest(10)
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

  @RepeatedTest(10)
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

  @RepeatedTest(10)
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

  @RepeatedTest(10)
  fun `add parent with children`() {
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

  @RepeatedTest(10)
  fun `add parent with children 2`() {
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

  @RepeatedTest(10)
  fun `add parent with children 3`() {
    target addEntity ParentMultipleEntity("Parent", MySource)
    val source = createBuilderFrom(target)
    val parentToModify = source.toSnapshot().entities(ParentMultipleEntity::class.java).single()
    source.modifyEntity(parentToModify) {
      this.children = listOf(
        ChildMultipleEntity("child1", MySource),
      )
    }

    val result = target.applyDiff(source)

    assertEquals(1, result.entities(ParentMultipleEntity::class.java).single().children.size)
  }
}
