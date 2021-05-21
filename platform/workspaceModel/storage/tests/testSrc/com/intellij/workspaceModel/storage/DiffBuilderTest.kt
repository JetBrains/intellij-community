// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.testFramework.UsefulTestCase.assertOneElement
import com.intellij.workspaceModel.storage.entities.*
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityStorageImpl
import com.intellij.workspaceModel.storage.impl.exceptions.AddDiffException
import com.intellij.workspaceModel.storage.impl.external.ExternalEntityMappingImpl
import org.hamcrest.CoreMatchers
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

private fun WorkspaceEntityStorageBuilder.applyDiff(anotherBuilder: WorkspaceEntityStorageBuilder): WorkspaceEntityStorage {
  val builder = createBuilderFrom(this)
  builder.addDiff(anotherBuilder)
  val storage =  builder.toStorage()
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
    val source = createBuilderFrom(target.toStorage())
    source.removeEntity(entity)
    val storage = target.applyDiff(source)
    assertEquals(entity2, storage.singleSampleEntity())
  }

  @Test
  fun `modify entity`() {
    val target = createEmptyBuilder()
    val entity = target.addSampleEntity("hello")
    val source = createBuilderFrom(target.toStorage())
    source.modifyEntity(ModifiableSampleEntity::class.java, entity) {
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
    val source = createBuilderFrom(target.toStorage())
    target.removeEntity(entity)
    target.assertConsistency()
    source.assertConsistency()
    source.removeEntity(entity)
    val storage = target.applyDiff(source)
    assertEquals(entity2, storage.singleSampleEntity())
  }

  @Test
  fun `modify removed entity`() {
    val target = createEmptyBuilder()
    val entity = target.addSampleEntity("hello")
    val source = createBuilderFrom(target.toStorage())
    target.removeEntity(entity)
    source.assertConsistency()
    source.modifyEntity(ModifiableSampleEntity::class.java, entity) {
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
    source.modifyEntity(ModifiableParentEntity::class.java, parent) {
      this.parentProperty = "new property"
    }
    source.modifyEntity(ModifiableChildEntity::class.java, child) {
      this.childProperty = "new property"
    }

    val res = target.applyDiff(source) as WorkspaceEntityStorageImpl
    res.assertConsistency()

    assertOneElement(res.entities(ParentEntity::class.java).toList())
    assertTrue(res.entities(ChildEntity::class.java).toList().isEmpty())
  }

  @Test
  fun `remove modified entity`() {
    val target = createEmptyBuilder()
    val entity = target.addSampleEntity("hello")
    val source = createBuilderFrom(target.toStorage())
    target.modifyEntity(ModifiableSampleEntity::class.java, entity) {
      stringProperty = "changed"
    }
    source.removeEntity(entity)
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

    val resultingStorage = source.toStorage()
    assertEquals(2, resultingStorage.entities(SampleEntity::class.java).toList().size)
    assertEquals(1, resultingStorage.entities(ChildSampleEntity::class.java).toList().size)

    assertEquals(resultingStorage.entities(SampleEntity::class.java).last(), resultingStorage.entities(ChildSampleEntity::class.java).single().parent)
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

    val resultingStorage = source.toStorage()
    assertEquals(2, resultingStorage.entities(SampleEntity::class.java).toList().size)
    assertEquals(2, resultingStorage.entities(ChildSampleEntity::class.java).toList().size)

    assertNotNull(resultingStorage.entities(ChildSampleEntity::class.java).first().parent)
    assertNotNull(resultingStorage.entities(ChildSampleEntity::class.java).last().parent)

    assertEquals(resultingStorage.entities(SampleEntity::class.java).first(), resultingStorage.entities(ChildSampleEntity::class.java).first().parent)
    assertEquals(resultingStorage.entities(SampleEntity::class.java).last(), resultingStorage.entities(ChildSampleEntity::class.java).last().parent)
  }

  @Test
  fun `add dependency without changing entities`() {
    val source = createEmptyBuilder()
    val parent = source.addSampleEntity("Another entity")
    source.addChildSampleEntity("String", null)

    val target = createBuilderFrom(source)
    val pchild = target.entities(ChildSampleEntity::class.java).single()
    val pparent = target.entities(SampleEntity::class.java).single()
    target.modifyEntity(ModifiableChildSampleEntity::class.java, pchild) {
      this.parent = pparent
    }

    source.addDiff(target)
    source.assertConsistency()

    val resultingStorage = source.toStorage()
    assertEquals(1, resultingStorage.entities(SampleEntity::class.java).toList().size)
    assertEquals(1, resultingStorage.entities(ChildSampleEntity::class.java).toList().size)

    assertEquals(resultingStorage.entities(SampleEntity::class.java).single(), resultingStorage.entities(ChildSampleEntity::class.java).single().parent)
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
    target.modifyEntity(ModifiableParentEntity::class.java, parent) {
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
    target.removeEntity(parent)
    source.removeEntity(parent)

    source.applyDiff(target)
  }

  @Test
  fun `remove parent in both diffs`() {
    val source = createEmptyBuilder()
    val parent = source.addParentEntity()
    val optionalChild = source.addChildWithOptionalParentEntity(null)

    val target = createBuilderFrom(source)
    target.modifyEntity(ModifiableChildWithOptionalParentEntity::class.java, optionalChild) {
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
    target.modifyEntity(ModifiableNamedEntity::class.java, namedEntity) {
      this.name = "Name"
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
    source.changeSource(sampleEntity, AnotherSource)

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
    source.changeSource(sampleEntity, AnotherSource)
    source.modifyEntity(ModifiableSampleEntity::class.java, sampleEntity) {
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
    target.changeSource(sampleEntity, AnotherSource)

    source.modifyEntity(ModifiableSampleEntity::class.java, sampleEntity) {
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

    val extractedParent = assertOneElement(target.entities(ParentEntity::class.java).toList())
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
    source.modifyEntity(ModifiableParentEntity::class.java, parent) {
      this.optionalChildren = this.optionalChildren + child
    }

    target.addDiff(source)

    val extractedParent = assertOneElement(target.entities(ParentEntity::class.java).toList())
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
    source.modifyEntity(ModifiableChildWithOptionalParentEntity::class.java, child) {
      this.optionalParent = parent
    }

    target.addDiff(source)

    val extractedParent = assertOneElement(target.entities(ParentEntity::class.java).toList())
    val extractedOptionalChild = assertOneElement(extractedParent.optionalChildren.toList())
    assertEquals(parentAndChildProperty, extractedOptionalChild.childProperty)
  }

  @Test
  fun `removing non-existing entity while adding the new one`() {
    val initial = createEmptyBuilder()
    val toBeRemoved = initial.addSampleEntity("En1")

    val source = createBuilderFrom(initial)

    initial.removeEntity(toBeRemoved)
    val target = createBuilderFrom(initial.toStorage())

    // In the incorrect implementation remove event will remove added entity
    source.addSampleEntity("En2")
    source.removeEntity(toBeRemoved)

    target.addDiff(source)

    assertOneElement(target.entities(SampleEntity::class.java).toList())
  }

  @Test
  fun `remove entity and reference`() {
    val initial = createEmptyBuilder()
    val parentEntity = initial.addParentEntity()
    val childEntity = initial.addChildWithOptionalParentEntity(parentEntity)

    val source = createBuilderFrom(initial)

    source.modifyEntity(ModifiableChildWithOptionalParentEntity::class.java, childEntity) {
      this.childProperty = "newProp"
    }

    source.modifyEntity(ModifiableParentEntity::class.java, parentEntity) {
      this.optionalChildren = emptySequence()
    }

    source.removeEntity(childEntity)

    val res = initial.applyDiff(source)

    assertTrue(res.entities(ChildWithOptionalParentEntity::class.java).toList().isEmpty())
    val newParent = assertOneElement(res.entities(ParentEntity::class.java).toList())
    assertTrue(newParent.optionalChildren.toList().isEmpty())
  }

  @Test
  fun `remove reference to created entity`() {
    val initial = createEmptyBuilder()
    val parentEntity = initial.addParentEntity()
    initial.addChildWithOptionalParentEntity(parentEntity)
    val source = createBuilderFrom(initial)

    source.addChildWithOptionalParentEntity(parentEntity)

    source.modifyEntity(ModifiableParentEntity::class.java, parentEntity) {
      this.optionalChildren = emptySequence()
    }

    val res = initial.applyDiff(source)

    assertEquals(2, res.entities(ChildWithOptionalParentEntity::class.java).toList().size)
    val newParent = assertOneElement(res.entities(ParentEntity::class.java).toList())
    assertTrue(newParent.optionalChildren.toList().isEmpty())
  }
}
