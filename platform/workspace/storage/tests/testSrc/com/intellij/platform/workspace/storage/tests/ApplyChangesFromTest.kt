// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.ExternalMappingKey
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.ImmutableEntityStorageImpl
import com.intellij.platform.workspace.storage.impl.MutableEntityStorageImpl
import com.intellij.platform.workspace.storage.impl.assertConsistency
import com.intellij.platform.workspace.storage.impl.exceptions.ApplyChangesFromException
import com.intellij.platform.workspace.storage.impl.external.ExternalEntityMappingImpl
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.testEntities.entities.*
import com.intellij.platform.workspace.storage.toBuilder
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.testFramework.UsefulTestCase.assertOneElement
import com.intellij.testFramework.assertErrorLogged
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.RepetitionInfo
import java.util.*
import kotlin.test.*

class ApplyChangesFromTest {
  private lateinit var target: MutableEntityStorageImpl
  private var shaker = -1L

  private lateinit var virtualFileUrlManager: VirtualFileUrlManager

  private val externalMappingName = ExternalMappingKey.create<Any>("test.checking.external.mapping")

  private fun MutableEntityStorage.applyChanges(anotherBuilder: MutableEntityStorage): EntityStorage {
    val builder = createBuilderFrom(this)
    builder.upgradeApplyChangesFromEngine = { it.shaker = shaker }
    builder.applyChangesFrom(anotherBuilder)
    val storage = builder.toSnapshot() as ImmutableEntityStorageImpl
    storage.assertConsistency()
    return storage
  }

  @BeforeEach
  internal fun setUp(info: RepetitionInfo) {
    virtualFileUrlManager = VirtualFileUrlManagerImpl()
    target = createEmptyBuilder()
    // Random returns same result for nextInt(2) for the first 4095 seeds, so we generated random seed
    shaker = Random(info.currentRepetition.toLong()).nextLong()
    target.upgradeApplyChangesFromEngine = { it.shaker = shaker }
  }

  @RepeatedTest(10)
  fun `add entity`() {
    val source = createEmptyBuilder()
    source addEntity SampleEntity(false, "first", ArrayList(), HashMap(), virtualFileUrlManager.getOrCreateFromUrl("file:///tmp"),
                                  SampleEntitySource("test"))
    target addEntity SampleEntity(false, "second", ArrayList(), HashMap(), virtualFileUrlManager.getOrCreateFromUrl("file:///tmp"),
                                  SampleEntitySource("test"))
    val storage = target.applyChanges(source)
    assertEquals(setOf("first", "second"), storage.entities(SampleEntity::class.java).mapTo(HashSet()) { it.stringProperty })
  }

  @RepeatedTest(10)
  fun `remove entity`() {
    val entity = target addEntity SampleEntity(false, "hello", ArrayList(), HashMap(),
                                               virtualFileUrlManager.getOrCreateFromUrl("file:///tmp"),
                                               SampleEntitySource("test"))
    val entity2 = target addEntity SampleEntity(false, "hello", ArrayList(), HashMap(),
                                                virtualFileUrlManager.getOrCreateFromUrl("file:///tmp"),
                                                SampleEntitySource("test"))
    val source = createBuilderFrom(target.toSnapshot())
    source.removeEntity(entity.from(source))
    val storage = target.applyChanges(source)
    assertEquals(entity2, storage.singleSampleEntity())
  }

  @RepeatedTest(10)
  fun `modify entity`() {
    val entity = target addEntity SampleEntity(false, "hello", ArrayList(), HashMap(),
                                               virtualFileUrlManager.getOrCreateFromUrl("file:///tmp"),
                                               SampleEntitySource("test"))
    val source = createBuilderFrom(target.toSnapshot())
    source.modifySampleEntity(entity.from(source)) {
      stringProperty = "changed"
    }
    val storage = target.applyChanges(source)
    assertEquals("changed", storage.singleSampleEntity().stringProperty)
  }

  @RepeatedTest(10)
  fun `remove removed entity`() {
    val entity = target addEntity SampleEntity(false, "hello", ArrayList(), HashMap(),
                                               virtualFileUrlManager.getOrCreateFromUrl("file:///tmp"),
                                               SampleEntitySource("test"))
    val entity2 = target addEntity SampleEntity(false, "hello", ArrayList(), HashMap(),
                                                virtualFileUrlManager.getOrCreateFromUrl("file:///tmp"),
                                                SampleEntitySource("test"))
    val source = createBuilderFrom(target.toSnapshot())
    target.removeEntity(entity)
    target.assertConsistency()
    source.assertConsistency()
    source.removeEntity(entity.from(source))
    val storage = target.applyChanges(source)
    assertEquals(entity2, storage.singleSampleEntity())
  }

  @RepeatedTest(10)
  fun `modify removed entity`() {
    val entity = target addEntity SampleEntity(false, "hello", ArrayList(), HashMap(),
                                               virtualFileUrlManager.getOrCreateFromUrl("file:///tmp"),
                                               SampleEntitySource("test"))
    val source = createBuilderFrom(target.toSnapshot())
    target.removeEntity(entity)
    source.assertConsistency()
    source.modifySampleEntity(entity.from(source)) {
      stringProperty = "changed"
    }
    val storage = target.applyChanges(source)
    assertEquals(emptyList(), storage.entities(SampleEntity::class.java).toList())
  }

  @RepeatedTest(10)
  fun `modify removed child entity`() {
    val parent = target addEntity XParentEntity("parent", MySource)
    target.modifyXParentEntity(parent) {
      this.children = listOf(XChildEntity("child", MySource))
    }
    val child = parent.children.single()
    val source = createBuilderFrom(target)
    target.removeEntity(child)
    source.modifyXParentEntity(parent.from(source)) {
      this.parentProperty = "new property"
    }
    source.modifyXChildEntity(child.from(source)) {
      this.childProperty = "new property"
    }

    val res = target.applyChanges(source) as ImmutableEntityStorageImpl
    res.assertConsistency()

    assertOneElement(res.entities(XParentEntity::class.java).toList())
    assertTrue(res.entities(XChildEntity::class.java).toList().isEmpty())
  }

  @RepeatedTest(10)
  fun `remove modified entity`() {
    val entity = target addEntity SampleEntity(false, "hello", ArrayList(), HashMap(),
                                               virtualFileUrlManager.getOrCreateFromUrl("file:///tmp"),
                                               SampleEntitySource("test"))
    val source = createBuilderFrom(target.toSnapshot())
    target.modifySampleEntity(entity) {
      stringProperty = "changed"
    }
    source.removeEntity(entity.from(source))
    source.assertConsistency()
    val storage = target.applyChanges(source)
    assertEquals(emptyList(), storage.entities(SampleEntity::class.java).toList())
  }

  @RepeatedTest(10)
  fun `add entity with refs at the same slot`() {
    val source = createEmptyBuilder()
    source addEntity SampleEntity(false, "Another entity", ArrayList(), HashMap(), virtualFileUrlManager.getOrCreateFromUrl("file:///tmp"),
                                  SampleEntitySource("test"))
    val parentEntity = SampleEntity(false, "hello", ArrayList(), HashMap(),
                                                     virtualFileUrlManager.getOrCreateFromUrl("file:///tmp"), SampleEntitySource("test"))
    target addEntity ChildSampleEntity("data", SampleEntitySource("test")) {
      this@ChildSampleEntity.parentEntity = parentEntity
    }

    source.applyChangesFrom(target)
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
    val parent = SampleEntity(false, "Another entity", ArrayList(), HashMap(),
                              virtualFileUrlManager.getOrCreateFromUrl("file:///tmp"), SampleEntitySource("test"))
    source addEntity ChildSampleEntity("String", SampleEntitySource("test")) {
      parentEntity = parent
    }

    val parentEntity = SampleEntity(false, "hello", ArrayList(), HashMap(),
                                    virtualFileUrlManager.getOrCreateFromUrl("file:///tmp"), SampleEntitySource("test"))
    target addEntity ChildSampleEntity("data", SampleEntitySource("test")) {
      this@ChildSampleEntity.parentEntity = parentEntity
    }

    source.applyChangesFrom(target)
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
    source addEntity SampleEntity(false, "Another entity", ArrayList(), HashMap(), virtualFileUrlManager.getOrCreateFromUrl("file:///tmp"),
                                  SampleEntitySource("test"))
    source addEntity ChildSampleEntity("String", SampleEntitySource("test"))

    val target = createBuilderFrom(source)
    val pchild = target.entities(ChildSampleEntity::class.java).single()
    val pparent = target.entities(SampleEntity::class.java).single()
    target.modifyChildSampleEntity(pchild) pchild@ {
      target.modifySampleEntity(pparent) pparent@ {
        this@pchild.parentEntity = this@pparent
      }
    }

    source.applyChangesFrom(target)
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
    target.modifyXParentEntity(parent.from(target)) parent@ {
      target addEntity XChildWithOptionalParentEntity("child", MySource) {
        optionalParent = this@parent
      }
    }
    source.removeEntity(parent)

    source.applyChanges(target)
  }

  @RepeatedTest(10)
  fun `modify child and parent`() {
    val source = createEmptyBuilder()
    val parent = source addEntity XParentEntity("parent", MySource)
    source.modifyXParentEntity(parent) {
      this.children += XChildEntity("child", MySource)
    }

    val target = createBuilderFrom(source)
    target.modifyXParentEntity(parent.from(target)) {
      this.parentProperty = "anotherValue"
    }
    source.modifyXParentEntity(parent) {
      this.children += XChildEntity("child", MySource)
    }

    source.applyChanges(target)
  }

  @RepeatedTest(10)
  fun `remove parent in both diffs with dependency`() {
    val source = createEmptyBuilder()
    val parent = source addEntity XParentEntity("parent", MySource)

    val target = createBuilderFrom(source)
    target.modifyXParentEntity(parent.from(target)) {
      this.optionalChildren += XChildWithOptionalParentEntity("child", MySource)
    }
    target.removeEntity(parent.from(target))
    source.removeEntity(parent)

    source.applyChanges(target)
  }

  @RepeatedTest(10)
  fun `remove parent in both diffs`() {
    val source = createEmptyBuilder()
    val parent = source addEntity XParentEntity("parent", MySource)
    val optionalChild = source addEntity XChildWithOptionalParentEntity("child", MySource)

    val target = createBuilderFrom(source)
    target.modifyXChildWithOptionalParentEntity(optionalChild.from(target)) optionalChild@{
      target.modifyXParentEntity(parent.from(target)) parent@{
        this@optionalChild.optionalParent = this@parent
      }
    }

    source.removeEntity(parent)

    source.applyChanges(target)
  }

  @RepeatedTest(10)
  fun `adding duplicated persistent ids`() {
    val source = createEmptyBuilder()
    val target = createBuilderFrom(source)

    target addEntity NamedEntity("Name", MySource) {
      children = emptyList()
    }
    source addEntity NamedEntity("Name", MySource) {
      children = emptyList()
    }

    assertErrorLogged<ApplyChangesFromException> {
      source.applyChanges(target)
    }
  }

  @RepeatedTest(10)
  fun `modifying duplicated persistent ids`() {
    val source = createEmptyBuilder()
    val namedEntity = source addEntity NamedEntity("Hello", MySource)
    val target = createBuilderFrom(source)

    source addEntity NamedEntity("Name", MySource) {
      children = emptyList()
    }
    target.modifyNamedEntity(namedEntity.from(target)) {
      this.myName = "Name"
    }

    assertErrorLogged<ApplyChangesFromException> {
      source.applyChanges(target)
    }
  }

  @RepeatedTest(10)
  fun `checking external mapping`() {
    val target = createEmptyBuilder()

    target addEntity SampleEntity(false, "Entity at index 0", ArrayList(), HashMap(),
                                  virtualFileUrlManager.getOrCreateFromUrl("file:///tmp"),
                                  SampleEntitySource("test"))

    val source = createEmptyBuilder()
    val sourceSample = source addEntity SampleEntity(false, "Entity at index 1", ArrayList(), HashMap(),
                                                     virtualFileUrlManager.getOrCreateFromUrl("file:///tmp"), SampleEntitySource("test"))
    val mutableExternalMapping = source.getMutableExternalMapping(externalMappingName)
    val anyObj = Any()
    mutableExternalMapping.addMapping(sourceSample, anyObj)

    target.applyChangesFrom(source)

    val externalMapping = target.getExternalMapping(externalMappingName) as ExternalEntityMappingImpl<Any>
    assertEquals(1, externalMapping.index.size)
  }

  @RepeatedTest(10)
  fun `checking external mapping is moved to the target builder`() {
    val target = createEmptyBuilder()

    val entity = target addEntity ParentEntity("Hey", MySource)
    val obj = Any()
    val obj2 = Any()
    target.getMutableExternalMapping(externalMappingName).addMapping(entity, obj)

    val newBuilder = target.toSnapshot().toBuilder()
    val newEntity = newBuilder addEntity ParentEntity("Hey 2", MySource)
    newBuilder.getMutableExternalMapping(externalMappingName).addMapping(newEntity, obj2)
    newBuilder.removeEntity(entity.from(newBuilder))

    target.removeEntity(entity)
    val freezed = target.toSnapshot().toBuilder()

    freezed.applyChangesFrom(newBuilder)

    assertEquals(1, freezed.entities(ParentEntity::class.java).toList().size)
    val requestedEntity = freezed.entities(ParentEntity::class.java).single()
    assertEquals("Hey 2", requestedEntity.parentData)
    assertSame(obj2, freezed.getMutableExternalMapping(externalMappingName).getDataByEntity(requestedEntity))
  }

  @RepeatedTest(10)
  fun `change source in diff`() {
    val sampleEntity = target addEntity SampleEntity(false, "Prop", ArrayList(), HashMap(),
                                                     virtualFileUrlManager.getOrCreateFromUrl("file:///tmp"), MySource)

    val source = createBuilderFrom(target)
    source.modifySampleEntity(sampleEntity.from(source)) {
      this.entitySource = AnotherSource
    }

    target.applyChangesFrom(source)

    target.assertConsistency()

    val entitySourceIndex = target.indexes.entitySourceIndex
    assertEquals(1, entitySourceIndex.index.size)
    assertNotNull(entitySourceIndex.getIdsByEntry(AnotherSource)?.single())
  }

  @RepeatedTest(10)
  fun `change source and data in diff`() {
    val sampleEntity = target addEntity SampleEntity(false, "Prop", ArrayList(), HashMap(),
                                                     virtualFileUrlManager.getOrCreateFromUrl("file:///tmp"), MySource)

    val source = createBuilderFrom(target)
    source.modifySampleEntity(sampleEntity.from(source)) {
      this.entitySource = AnotherSource
    }
    source.modifySampleEntity(sampleEntity.from(source)) {
      stringProperty = "Prop2"
    }

    target.applyChangesFrom(source)

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
                                                     virtualFileUrlManager.getOrCreateFromUrl("file:///tmp"), MySource)

    val source = createBuilderFrom(target)
    target.modifySampleEntity(sampleEntity) {
      this.entitySource = AnotherSource
    }

    source.modifySampleEntity(sampleEntity.from(source)) {
      this.stringProperty = "Updated"
    }

    target.applyChangesFrom(source)

    target.assertConsistency()

    val entitySourceIndex = target.indexes.entitySourceIndex
    assertEquals(1, entitySourceIndex.index.size)
    assertNull(entitySourceIndex.getIdsByEntry(AnotherSource)?.single())
  }

  @RepeatedTest(10)
  fun `adding parent with child and shifting`() {
    val parentAndChildProperty = "Bound"
    target addEntity XChildWithOptionalParentEntity("Existing", MySource)

    val source = createEmptyBuilder()
    source addEntity XChildWithOptionalParentEntity(parentAndChildProperty, MySource) {
      optionalParent = XParentEntity(parentAndChildProperty, MySource)
    }

    target.applyChangesFrom(source)

    val extractedParent = assertOneElement(target.entities(XParentEntity::class.java).toList())
    val extractedOptionalChild = assertOneElement(extractedParent.optionalChildren.toList())
    assertEquals(parentAndChildProperty, extractedOptionalChild.childProperty)
  }

  @RepeatedTest(10)
  fun `adding parent with child and shifting and later connecting`() {
    val parentAndChildProperty = "Bound"
    target addEntity XChildWithOptionalParentEntity("Existing", MySource)

    val source = createEmptyBuilder()
    val parent = source addEntity XParentEntity(parentAndChildProperty, MySource)
    source.modifyXParentEntity(parent) {
      this.optionalChildren += XChildWithOptionalParentEntity(parentAndChildProperty, MySource)
    }

    target.applyChangesFrom(source)

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
    source.modifyXChildWithOptionalParentEntity(child) {
      this.optionalParent = XParentEntity(parentAndChildProperty, MySource)
    }

    target.applyChangesFrom(source)

    val extractedParent = assertOneElement(target.entities(XParentEntity::class.java).toList())
    val extractedOptionalChild = assertOneElement(extractedParent.optionalChildren.toList())
    assertEquals(parentAndChildProperty, extractedOptionalChild.childProperty)
  }

  @RepeatedTest(10)
  fun `removing non-existing entity while adding the new one`() {
    val initial = createEmptyBuilder()
    val toBeRemoved = initial addEntity SampleEntity(false, "En1", ArrayList(), HashMap(),
                                                     virtualFileUrlManager.getOrCreateFromUrl("file:///tmp"), SampleEntitySource("test"))

    val source = createBuilderFrom(initial)

    initial.removeEntity(toBeRemoved)
    val target = createBuilderFrom(initial.toSnapshot())

    // In the incorrect implementation remove event will remove added entity
    source addEntity SampleEntity(false, "En2", ArrayList(), HashMap(), virtualFileUrlManager.getOrCreateFromUrl("file:///tmp"),
                                  SampleEntitySource("test"))
    source.removeEntity(toBeRemoved.from(source))

    target.applyChangesFrom(source)

    assertOneElement(target.entities(SampleEntity::class.java).toList())
  }

  @RepeatedTest(10)
  fun `remove entity and reference`() {
    val initial = createEmptyBuilder()
    val parentEntity = initial addEntity XParentEntity("parent", MySource)
    val childEntity = initial addEntity XChildWithOptionalParentEntity("child", MySource) child@ {
      initial.modifyXParentEntity(parentEntity) parent@ {
        this@child.optionalParent = this@parent
      }
    }

    val source = createBuilderFrom(initial)

    source.modifyXChildWithOptionalParentEntity(childEntity.from(source)) {
      this.childProperty = "newProp"
    }

    source.modifyXParentEntity(parentEntity.from(source)) {
      this.optionalChildren = emptyList()
    }

    source.removeEntity(childEntity.from(source))

    val res = initial.applyChanges(source)

    assertTrue(res.entities(XChildWithOptionalParentEntity::class.java).toList().isEmpty())
    val newParent = assertOneElement(res.entities(XParentEntity::class.java).toList())
    assertTrue(newParent.optionalChildren.toList().isEmpty())
  }

  @RepeatedTest(10)
  fun `remove reference to created entity`() {
    val initial = createEmptyBuilder()
    val parentEntity = initial addEntity XParentEntity("parent", MySource)
    initial addEntity XChildWithOptionalParentEntity("child", MySource) child@ {
      initial.modifyXParentEntity(parentEntity) parent@ {
        this@child.optionalParent = this@parent
      }
    }
    val source = createBuilderFrom(initial)

    source addEntity XChildWithOptionalParentEntity("child", MySource) child@ {
      source.modifyXParentEntity(parentEntity.from(source)) parent@ {
        this@child.optionalParent = this@parent
      }
    }

    source.modifyXParentEntity(parentEntity.from(source)) {
      this.optionalChildren = emptyList()
    }

    val res = initial.applyChanges(source)

    assertEquals(2, res.entities(XChildWithOptionalParentEntity::class.java).toList().size)
    val newParent = assertOneElement(res.entities(XParentEntity::class.java).toList())
    assertTrue(newParent.optionalChildren.toList().isEmpty())
  }

  @RepeatedTest(10)
  fun `add parent with children`() {
    target addEntity ParentMultipleEntity("Parent", MySource)
    val source = createBuilderFrom(target)
    source.modifyParentMultipleEntity(source.entities(ParentMultipleEntity::class.java).single()) {
      this.children = listOf(
        ChildMultipleEntity("child1", MySource),
        ChildMultipleEntity("child2", MySource),
      )
    }

    val result = target.applyChanges(source)

    assertEquals(2, result.entities(ParentMultipleEntity::class.java).single().children.size)
  }

  @RepeatedTest(10)
  fun `add parent with children 2`() {
    target addEntity ParentMultipleEntity("Parent", MySource)
    val source = createBuilderFrom(target)
    val parentToModify = source.toSnapshot().entities(ParentMultipleEntity::class.java).single()
    source.modifyParentMultipleEntity(parentToModify) {
      this.children = listOf(
        ChildMultipleEntity("child1", MySource),
        ChildMultipleEntity("child2", MySource),
      )
    }

    val result = target.applyChanges(source)

    assertEquals(2, result.entities(ParentMultipleEntity::class.java).single().children.size)
  }

  @RepeatedTest(10)
  fun `add parent with children 3`() {
    target addEntity ParentMultipleEntity("Parent", MySource)
    val source = createBuilderFrom(target)
    val parentToModify = source.toSnapshot().entities(ParentMultipleEntity::class.java).single()
    source.modifyParentMultipleEntity(parentToModify) {
      this.children = listOf(
        ChildMultipleEntity("child1", MySource),
      )
    }

    val result = target.applyChanges(source)

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
    builder.modifyOoParentEntity(parentEntity) {
      this.parentProperty = "eee"
    }
    parentEntity.child?.let { builder.removeEntity(it) }
    builder.modifyOoParentEntity(parentEntity) {
      this.child = OoChildEntity("ccc", MySource)
    }

    snapshot.toBuilder().applyChangesFrom(builder)
  }

  @RepeatedTest(10)
  fun `check one to one connection change 2`() {
    var builder = MutableEntityStorage.create()
    builder addEntity OoParentEntity("aaa", MySource)
    val snapshot = builder.toSnapshot()
    builder = snapshot.toBuilder()

    val parentEntity = builder.entities(OoParentEntity::class.java).single()
    val newChild = OoChildEntity("ccc", MySource)
    val updatedParent = builder.modifyOoParentEntity(parentEntity) {
      this.child = newChild
    }
    builder.removeEntity(updatedParent.child!!)

    snapshot.toBuilder().applyChangesFrom(builder)
  }

  @RepeatedTest(10)
  fun `change source and data`() {
    val sampleEntity = target addEntity SampleEntity(false, "Prop", ArrayList(), HashMap(),
                                                     virtualFileUrlManager.getOrCreateFromUrl("file:///tmp"), MySource)

    val source = createBuilderFrom(target)
    source.modifySampleEntity(sampleEntity.from(source)) {
      this.stringProperty = "Updated"
      this.entitySource = AnotherSource
    }

    target.applyChangesFrom(source)

    target.assertConsistency()

    val entitySourceIndex = target.indexes.entitySourceIndex
    assertEquals(1, entitySourceIndex.index.size)
    assertNotNull(entitySourceIndex.getIdsByEntry(AnotherSource)?.single())
  }

  @RepeatedTest(10)
  fun `modify and update`() {
    val parentEntity1 = target addEntity XParentEntity("Parent1", MySource)
    val childEntity = target addEntity XChildEntity("property", MySource) {
      this.parentEntity = XParentEntity("Parent2", MySource)
    }

    val source = createBuilderFrom(target)
    source.modifyXChildEntity(childEntity.from(source)) child@ {
      source.modifyXParentEntity(parentEntity1.from(source)) parent@ {
        this@child.parentEntity = this@parent
      }
    }

    target.applyChangesFrom(source)

    target.assertConsistency()

    assertEquals("Parent1", target.entities(XChildEntity::class.java).single().parentEntity.parentProperty)
  }

  @RepeatedTest(10)
  fun `detach child and remove parent`() {
    val parentEntity = target addEntity XParentEntity("Parent2", MySource) {
      this.optionalChildren = listOf(XChildWithOptionalParentEntity("property", MySource))
    }

    val source = createBuilderFrom(target)
    source.modifyXParentEntity(parentEntity.from(source)) {
      this.optionalChildren = emptyList()
    }

    source.removeEntity(parentEntity.from(source))

    // The child was not removed
    assertTrue(source.entities(XChildWithOptionalParentEntity::class.java).any())

    target.applyChangesFrom(source)

    target.assertConsistency()

    assertTrue(target.entities(XParentEntity::class.java).none())
    assertTrue(target.entities(XChildWithOptionalParentEntity::class.java).any())
  }

  @RepeatedTest(10)
  fun `attach child and remove parent`() {
    val child = target addEntity XChildWithOptionalParentEntity("property", MySource)
    val parentEntity = target addEntity XParentEntity("Parent2", MySource)

    val source = createBuilderFrom(target)
    source.modifyXParentEntity(parentEntity.from(source)) parent@ {
      source.modifyXChildWithOptionalParentEntity(child.from(source)) child@ {
        this@parent.optionalChildren = listOf(this@child)
      }
    }

    source.removeEntity(parentEntity.from(source))

    // The child was removed
    assertTrue(source.entities(XChildWithOptionalParentEntity::class.java).none())

    target.applyChangesFrom(source)

    target.assertConsistency()

    assertTrue(target.entities(XParentEntity::class.java).none())
    assertTrue(target.entities(XChildWithOptionalParentEntity::class.java).none())
  }

  @RepeatedTest(10)
  fun `replace child by modification`() {
    val parentEntity = target addEntity OoParentEntity("Parent2", MySource) {
      this.anotherChild = OoChildWithNullableParentEntity(MySource)
    }

    val source = createBuilderFrom(target)
    source.modifyOoParentEntity(parentEntity.from(source)) {
      this.anotherChild = OoChildWithNullableParentEntity(AnotherSource)
    }

    // The previous child was not removed because it has optional parent
    assertEquals(2, source.entities(OoChildWithNullableParentEntity::class.java).toList().size)

    target.applyChangesFrom(source)

    target.assertConsistency()

    assertEquals(AnotherSource, target.entities(OoParentEntity::class.java).single().anotherChild!!.entitySource)
    assertEquals(2, target.entities(OoChildWithNullableParentEntity::class.java).toList().size)
  }

  @RepeatedTest(10)
  fun `replace child by modification second child untouched`() {
    val parentEntity = target addEntity OoParentEntity("Parent2", MySource) {
      this.anotherChild = OoChildWithNullableParentEntity(MySource)
      this.child = OoChildEntity("", MySource)
    }

    val source = createBuilderFrom(target)
    source.modifyOoParentEntity(parentEntity.from(source)) {
      this.anotherChild = OoChildWithNullableParentEntity(AnotherSource)
    }

    // The previous child was not removed because it has optional parent
    assertEquals(2, source.entities(OoChildWithNullableParentEntity::class.java).toList().size)

    target.applyChangesFrom(source)

    target.assertConsistency()

    assertEquals(AnotherSource, target.entities(OoParentEntity::class.java).single().anotherChild!!.entitySource)
    assertEquals("", target.entities(OoParentEntity::class.java).single().child!!.childProperty)
    assertEquals(2, target.entities(OoChildWithNullableParentEntity::class.java).toList().size)
  }

  @RepeatedTest(10)
  fun `add child with parallel update`() {
    val parentEntity = target addEntity OoParentEntity("Parent2", MySource)

    val source = createBuilderFrom(target)
    source.modifyOoParentEntity(parentEntity.from(source)) {
      this.anotherChild = OoChildWithNullableParentEntity(AnotherSource)
    }

    // Update target builder in parallel with the source builder
    target.modifyOoParentEntity(parentEntity.from(source)) {
      this.anotherChild = OoChildWithNullableParentEntity(MySource)
    }

    target.applyChangesFrom(source)

    target.assertConsistency()

    assertEquals(AnotherSource, target.entities(OoParentEntity::class.java).single().anotherChild!!.entitySource)
    assertEquals(2, target.entities(OoChildWithNullableParentEntity::class.java).toList().size)
  }

  @RepeatedTest(10)
  fun `remove entity with soft link`() {
    val entity = target addEntity WithSoftLinkEntity(NameId("id"), MySource)

    val source = createBuilderFrom(target)
    source.removeEntity(entity.from(source))

    target.applyChangesFrom(source)

    target.assertConsistency()

    assertTrue(target.referrers(NameId("id"), WithSoftLinkEntity::class.java).toList().isEmpty())
  }

  @RepeatedTest(10)
  fun `the order of children is not changed in abstract entities`() {
    val source = createBuilderFrom(target)
    source addEntity RightEntity(MySource) {
      this.children = listOf(
        LeftEntity(MySource),
        LeftEntity(AnotherSource),
      )
    }

    target.applyChangesFrom(source)

    target.assertConsistency()

    val children = target.entities(RightEntity::class.java).single().children
    assertEquals(2, children.size)
    assertEquals(MySource, children.first().entitySource)
    assertEquals(AnotherSource, children.last().entitySource)
  }

  @RepeatedTest(10)
  fun `the order of children is not changed`() {
    val source = createBuilderFrom(target)
    source addEntity ParentMultipleEntity("", MySource) {
      this.children = listOf(
        ChildMultipleEntity("data1", MySource),
        ChildMultipleEntity("data2", AnotherSource),
      )
    }

    target.applyChangesFrom(source)

    target.assertConsistency()

    val children = target.entities(ParentMultipleEntity::class.java).single().children
    assertEquals(2, children.size)
    assertEquals(MySource, children.first().entitySource)
    assertEquals(AnotherSource, children.last().entitySource)
  }

  @RepeatedTest(10)
  fun `the order of children is not changed with new children`() {
    val parent = target addEntity ParentMultipleEntity("info", MySource) {
      this.children = listOf(
        ChildMultipleEntity("data1", MySource),
        ChildMultipleEntity("data2", MySource),
        ChildMultipleEntity("data3", MySource),
      )
    }
    val source = createBuilderFrom(target)
    source.entities(ChildMultipleEntity::class.java).forEach { source.removeEntity(it) }
    source.modifyParentMultipleEntity(parent.from(source)) {
      this.children = listOf(
        ChildMultipleEntity("data11", MySource),
        ChildMultipleEntity("data21", MySource),
        ChildMultipleEntity("data31", MySource),
      )
    }

    target.applyChangesFrom(source)

    target.assertConsistency()

    val children = target.entities(ParentMultipleEntity::class.java).single().children
    assertEquals(3, children.size)
    assertEquals("data11", children.get(0).childData)
    assertEquals("data21", children.get(1).childData)
    assertEquals("data31", children.get(2).childData)
  }
}
