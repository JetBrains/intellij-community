// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.EntityStorageSnapshotImpl
import com.intellij.platform.workspace.storage.impl.MutableEntityStorageImpl
import com.intellij.platform.workspace.storage.impl.assertConsistency
import com.intellij.platform.workspace.storage.impl.exceptions.AddDiffException
import com.intellij.platform.workspace.storage.impl.external.ExternalEntityMappingImpl
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.testEntities.entities.*
import com.intellij.platform.workspace.storage.toBuilder
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.testFramework.UsefulTestCase.assertOneElement
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.*
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AddDiffTest {
  private lateinit var target: MutableEntityStorageImpl
  private var shaker = -1L

  private lateinit var virtualFileUrlManager: VirtualFileUrlManager

  private val externalMappingName = "test.checking.external.mapping"

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
    virtualFileUrlManager = VirtualFileUrlManagerImpl()
    target = createEmptyBuilder()
    // Random returns same result for nextInt(2) for the first 4095 seeds, so we generated random seed
    shaker = Random(info.currentRepetition.toLong()).nextLong()
    target.upgradeAddDiffEngine = { it.shaker = shaker }
  }

  @RepeatedTest(10)
  fun `add entity`() {
    val source = createEmptyBuilder()
    source addEntity SampleEntity(false, "first", ArrayList(), HashMap(), virtualFileUrlManager.fromUrl("file:///tmp"),
                                  SampleEntitySource("test"))
    target addEntity SampleEntity(false, "second", ArrayList(), HashMap(), virtualFileUrlManager.fromUrl("file:///tmp"),
                                  SampleEntitySource("test"))
    val storage = target.applyDiff(source)
    assertEquals(setOf("first", "second"), storage.entities(SampleEntity::class.java).mapTo(HashSet()) { it.stringProperty })
  }

  @RepeatedTest(10)
  fun `remove entity`() {
    val entity = target addEntity SampleEntity(false, "hello", ArrayList(), HashMap(), virtualFileUrlManager.fromUrl("file:///tmp"),
                                               SampleEntitySource("test"))
    val entity2 = target addEntity SampleEntity(false, "hello", ArrayList(), HashMap(), virtualFileUrlManager.fromUrl("file:///tmp"),
                                                SampleEntitySource("test"))
    val source = createBuilderFrom(target.toSnapshot())
    source.removeEntity(entity.from(source))
    val storage = target.applyDiff(source)
    assertEquals(entity2, storage.singleSampleEntity())
  }

  @RepeatedTest(10)
  fun `modify entity`() {
    val entity = target addEntity SampleEntity(false, "hello", ArrayList(), HashMap(), virtualFileUrlManager.fromUrl("file:///tmp"),
                                               SampleEntitySource("test"))
    val source = createBuilderFrom(target.toSnapshot())
    source.modifyEntity(entity.from(source)) {
      stringProperty = "changed"
    }
    val storage = target.applyDiff(source)
    assertEquals("changed", storage.singleSampleEntity().stringProperty)
  }

  @RepeatedTest(10)
  fun `remove removed entity`() {
    val entity = target addEntity SampleEntity(false, "hello", ArrayList(), HashMap(), virtualFileUrlManager.fromUrl("file:///tmp"),
                                               SampleEntitySource("test"))
    val entity2 = target addEntity SampleEntity(false, "hello", ArrayList(), HashMap(), virtualFileUrlManager.fromUrl("file:///tmp"),
                                                SampleEntitySource("test"))
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
    val entity = target addEntity SampleEntity(false, "hello", ArrayList(), HashMap(), virtualFileUrlManager.fromUrl("file:///tmp"),
                                               SampleEntitySource("test"))
    val source = createBuilderFrom(target.toSnapshot())
    target.removeEntity(entity)
    source.assertConsistency()
    source.modifyEntity(entity.from(source)) {
      stringProperty = "changed"
    }
    val storage = target.applyDiff(source)
    assertEquals(emptyList(), storage.entities(SampleEntity::class.java).toList())
  }

  @RepeatedTest(10)
  fun `modify removed child entity`() {
    val parent = target addEntity XParentEntity("parent", MySource)
    val child = target addEntity XChildEntity("child", MySource) {
      parentEntity = parent
    }
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
    val entity = target addEntity SampleEntity(false, "hello", ArrayList(), HashMap(), virtualFileUrlManager.fromUrl("file:///tmp"),
                                               SampleEntitySource("test"))
    val source = createBuilderFrom(target.toSnapshot())
    target.modifyEntity(entity) {
      stringProperty = "changed"
    }
    source.removeEntity(entity.from(source))
    source.assertConsistency()
    val storage = target.applyDiff(source)
    assertEquals(emptyList(), storage.entities(SampleEntity::class.java).toList())
  }

  @RepeatedTest(10)
  fun `add entity with refs at the same slot`() {
    val source = createEmptyBuilder()
    source addEntity SampleEntity(false, "Another entity", ArrayList(), HashMap(), virtualFileUrlManager.fromUrl("file:///tmp"),
                                  SampleEntitySource("test"))
    val parentEntity = target addEntity SampleEntity(false, "hello", ArrayList(), HashMap(),
                                                     virtualFileUrlManager.fromUrl("file:///tmp"), SampleEntitySource("test"))
    target addEntity ChildSampleEntity("data", SampleEntitySource("test")) {
      this@ChildSampleEntity.parentEntity = parentEntity
    }

    source.addDiff(target)
    source.assertConsistency()

    val resultingStorage = source.toSnapshot()
    assertEquals(2, resultingStorage.entities(SampleEntity::class.java).toList().size)
    assertEquals(1, resultingStorage.entities(ChildSampleEntity::class.java).toList().size)

    assertEquals(resultingStorage.entities(SampleEntity::class.java).last(),
                 resultingStorage.entities(ChildSampleEntity::class.java).single().parentEntity)
  }

  @RepeatedTest(10)
  fun `add remove and add with refs`() {
    val source = createEmptyBuilder()
    val parent = source addEntity SampleEntity(false, "Another entity", ArrayList(), HashMap(),
                                               virtualFileUrlManager.fromUrl("file:///tmp"), SampleEntitySource("test"))
    source addEntity ChildSampleEntity("String", SampleEntitySource("test")) {
      parentEntity = parent
    }

    val parentEntity = target addEntity SampleEntity(false, "hello", ArrayList(), HashMap(),
                                                     virtualFileUrlManager.fromUrl("file:///tmp"), SampleEntitySource("test"))
    target addEntity ChildSampleEntity("data", SampleEntitySource("test")) {
      this@ChildSampleEntity.parentEntity = parentEntity
    }

    source.addDiff(target)
    source.assertConsistency()

    val resultingStorage = source.toSnapshot()
    assertEquals(2, resultingStorage.entities(SampleEntity::class.java).toList().size)
    assertEquals(2, resultingStorage.entities(ChildSampleEntity::class.java).toList().size)

    assertNotNull(resultingStorage.entities(ChildSampleEntity::class.java).first().parentEntity)
    assertNotNull(resultingStorage.entities(ChildSampleEntity::class.java).last().parentEntity)

    assertEquals(resultingStorage.entities(SampleEntity::class.java).first(),
                 resultingStorage.entities(ChildSampleEntity::class.java).first().parentEntity)
    assertEquals(resultingStorage.entities(SampleEntity::class.java).last(),
                 resultingStorage.entities(ChildSampleEntity::class.java).last().parentEntity)
  }

  @RepeatedTest(10)
  fun `add dependency without changing entities`() {
    val source = createEmptyBuilder()
    source addEntity SampleEntity(false, "Another entity", ArrayList(), HashMap(), virtualFileUrlManager.fromUrl("file:///tmp"),
                                  SampleEntitySource("test"))
    source addEntity ChildSampleEntity("String", SampleEntitySource("test"))

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

    assertEquals(resultingStorage.entities(SampleEntity::class.java).single(),
                 resultingStorage.entities(ChildSampleEntity::class.java).single().parentEntity)
  }

  @RepeatedTest(10)
  fun `dependency to removed parent`() {
    val source = createEmptyBuilder()
    val parent = source addEntity XParentEntity("parent", MySource)

    val target = createBuilderFrom(source)
    target addEntity XChildWithOptionalParentEntity("child", MySource) {
      optionalParent = parent
    }
    source.removeEntity(parent)

    source.applyDiff(target)
  }

  @RepeatedTest(10)
  fun `modify child and parent`() {
    val source = createEmptyBuilder()
    val parent = source addEntity XParentEntity("parent", MySource)
    source addEntity XChildEntity("child", MySource) {
      parentEntity = parent
    }

    val target = createBuilderFrom(source)
    target.modifyEntity(parent.from(target)) {
      this.parentProperty = "anotherValue"
    }
    source addEntity XChildEntity("child", MySource) {
      parentEntity = parent
    }

    source.applyDiff(target)
  }

  @RepeatedTest(10)
  fun `remove parent in both diffs with dependency`() {
    val source = createEmptyBuilder()
    val parent = source addEntity XParentEntity("parent", MySource)

    val target = createBuilderFrom(source)
    target addEntity XChildWithOptionalParentEntity("child", MySource) {
      optionalParent = parent
    }
    target.removeEntity(parent.from(target))
    source.removeEntity(parent)

    source.applyDiff(target)
  }

  @RepeatedTest(10)
  fun `remove parent in both diffs`() {
    val source = createEmptyBuilder()
    val parent = source addEntity XParentEntity("parent", MySource)
    val optionalChild = source addEntity XChildWithOptionalParentEntity("child", MySource)

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
    Assertions.assertEquals(thrown.cause?.javaClass, AddDiffException::class.java, "Exception: ${thrown.stackTraceToString()}")
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
    Assertions.assertEquals(thrown.cause?.javaClass, AddDiffException::class.java, "Exception: ${thrown.stackTraceToString()}")
  }

  @RepeatedTest(10)
  fun `checking external mapping`() {
    val target = createEmptyBuilder()

    target addEntity SampleEntity(false, "Entity at index 0", ArrayList(), HashMap(), virtualFileUrlManager.fromUrl("file:///tmp"),
                                  SampleEntitySource("test"))

    val source = createEmptyBuilder()
    val sourceSample = source addEntity SampleEntity(false, "Entity at index 1", ArrayList(), HashMap(),
                                                     virtualFileUrlManager.fromUrl("file:///tmp"), SampleEntitySource("test"))
    val mutableExternalMapping = source.getMutableExternalMapping<Any>(externalMappingName)
    val anyObj = Any()
    mutableExternalMapping.addMapping(sourceSample, anyObj)

    target.addDiff(source)

    val externalMapping = target.getExternalMapping<Any>(externalMappingName) as ExternalEntityMappingImpl<Any>
    assertEquals(1, externalMapping.index.size)
  }

  @RepeatedTest(10)
  fun `checking external mapping is moved to the target builder`() {
    val target = createEmptyBuilder()

    val entity = target addEntity ParentEntity("Hey", MySource)
    val obj = Any()
    val obj2 = Any()
    target.getMutableExternalMapping<Any>(externalMappingName).addMapping(entity, obj)

    val newBuilder = target.toSnapshot().toBuilder()
    val newEntity = newBuilder addEntity ParentEntity("Hey 2", MySource)
    newBuilder.getMutableExternalMapping<Any>(externalMappingName).addMapping(newEntity, obj2)
    newBuilder.removeEntity(entity.from(newBuilder))

    target.removeEntity(entity)
    val freezed = target.toSnapshot().toBuilder()

    freezed.addDiff(newBuilder)

    assertEquals(1, freezed.entities(ParentEntity::class.java).toList().size)
    val requestedEntity = freezed.entities(ParentEntity::class.java).single()
    assertEquals("Hey 2", requestedEntity.parentData)
    assertSame(obj2, freezed.getMutableExternalMapping<Any>(externalMappingName).getDataByEntity(requestedEntity))
  }

  @RepeatedTest(10)
  fun `change source in diff`() {
    val sampleEntity = target addEntity SampleEntity(false, "Prop", ArrayList(), HashMap(),
                                                     virtualFileUrlManager.fromUrl("file:///tmp"), MySource)

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
    val sampleEntity = target addEntity SampleEntity(false, "Prop", ArrayList(), HashMap(),
                                                     virtualFileUrlManager.fromUrl("file:///tmp"), MySource)

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
    val sampleEntity = target addEntity SampleEntity(false, "Prop", ArrayList(), HashMap(),
                                                     virtualFileUrlManager.fromUrl("file:///tmp"), MySource)

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
    target addEntity XChildWithOptionalParentEntity("Existing", MySource)

    val source = createEmptyBuilder()
    val parent = source addEntity XParentEntity(parentAndChildProperty, MySource)
    source addEntity XChildWithOptionalParentEntity(parentAndChildProperty, MySource) {
      optionalParent = parent
    }

    target.addDiff(source)

    val extractedParent = assertOneElement(target.entities(XParentEntity::class.java).toList())
    val extractedOptionalChild = assertOneElement(extractedParent.optionalChildren.toList())
    assertEquals(parentAndChildProperty, extractedOptionalChild.childProperty)
  }

  @RepeatedTest(10)
  fun `adding parent with child and shifting and later connecting`() {
    val parentAndChildProperty = "Bound"
    target addEntity XChildWithOptionalParentEntity("Existing", MySource)

    val source = createEmptyBuilder()
    val child = source addEntity XChildWithOptionalParentEntity(parentAndChildProperty, MySource)
    val parent = source addEntity XParentEntity(parentAndChildProperty, MySource)
    source.modifyEntity(parent) {
      this.optionalChildren += child
    }

    target.addDiff(source)

    val extractedParent = assertOneElement(target.entities(XParentEntity::class.java).toList())
    val extractedOptionalChild = assertOneElement(extractedParent.optionalChildren.toList())
    assertEquals(parentAndChildProperty, extractedOptionalChild.childProperty)
  }

  @RepeatedTest(10)
  fun `adding parent with child and shifting and later child connecting`() {
    val parentAndChildProperty = "Bound"
    target addEntity XChildWithOptionalParentEntity("Existing", MySource)

    val source = createEmptyBuilder()
    val child = source addEntity XChildWithOptionalParentEntity(parentAndChildProperty, MySource)
    val parent = source addEntity XParentEntity(parentAndChildProperty, MySource)
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
    val toBeRemoved = initial addEntity SampleEntity(false, "En1", ArrayList(), HashMap(),
                                                     virtualFileUrlManager.fromUrl("file:///tmp"), SampleEntitySource("test"))

    val source = createBuilderFrom(initial)

    initial.removeEntity(toBeRemoved)
    val target = createBuilderFrom(initial.toSnapshot())

    // In the incorrect implementation remove event will remove added entity
    source addEntity SampleEntity(false, "En2", ArrayList(), HashMap(), virtualFileUrlManager.fromUrl("file:///tmp"),
                                  SampleEntitySource("test"))
    source.removeEntity(toBeRemoved.from(source))

    target.addDiff(source)

    assertOneElement(target.entities(SampleEntity::class.java).toList())
  }

  @RepeatedTest(10)
  fun `remove entity and reference`() {
    val initial = createEmptyBuilder()
    val parentEntity = initial addEntity XParentEntity("parent", MySource)
    val childEntity = initial addEntity XChildWithOptionalParentEntity("child", MySource) {
      optionalParent = parentEntity
    }

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
    val parentEntity = initial addEntity XParentEntity("parent", MySource)
    initial addEntity XChildWithOptionalParentEntity("child", MySource) {
      optionalParent = parentEntity
    }
    val source = createBuilderFrom(initial)

    source addEntity XChildWithOptionalParentEntity("child", MySource) {
      optionalParent = parentEntity
    }

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

  @RepeatedTest(10)
  fun `check one to one connection change`() {
    var builder = MutableEntityStorage.create()
    builder addEntity OoParentEntity("aaa", MySource) {
      child = OoChildEntity("bbb", MySource)
    }
    val snapshot = builder.toSnapshot()
    builder = snapshot.toBuilder()

    val parentEntity = builder.entities(OoParentEntity::class.java).single()
    builder.modifyEntity(parentEntity) {
      this.parentProperty = "eee"
    }
    parentEntity.child?.let { builder.removeEntity(it) }
    builder.modifyEntity(parentEntity) {
      this.child = OoChildEntity("ccc", MySource)
    }

    snapshot.toBuilder().addDiff(builder)
  }

  @RepeatedTest(10)
  fun `check one to one connection change 2`() {
    var builder = MutableEntityStorage.create()
    builder addEntity OoParentEntity("aaa", MySource)
    val snapshot = builder.toSnapshot()
    builder = snapshot.toBuilder()

    val parentEntity = builder.entities(OoParentEntity::class.java).single()
    val newChild = OoChildEntity("ccc", MySource)
    builder.modifyEntity(parentEntity) {
      this.child = newChild
    }
    builder.removeEntity(newChild)

    snapshot.toBuilder().addDiff(builder)
  }
}
