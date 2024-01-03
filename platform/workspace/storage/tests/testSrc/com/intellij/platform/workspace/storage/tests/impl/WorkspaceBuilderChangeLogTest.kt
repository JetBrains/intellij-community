// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspace.storage.tests.impl

import com.intellij.platform.workspace.storage.ExternalMappingKey
import com.intellij.platform.workspace.storage.impl.ChangeEntry
import com.intellij.platform.workspace.storage.impl.MutableEntityStorageImpl
import com.intellij.platform.workspace.storage.impl.asBase
import com.intellij.platform.workspace.storage.impl.assertConsistency
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.testEntities.entities.*
import com.intellij.platform.workspace.storage.tests.createEmptyBuilder
import com.intellij.platform.workspace.storage.tests.from
import com.intellij.platform.workspace.storage.toBuilder
import com.intellij.testFramework.assertInstanceOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import kotlin.test.*

class WorkspaceBuilderChangeLogTest {
  internal lateinit var builder: MutableEntityStorageImpl
  internal lateinit var another: MutableEntityStorageImpl

  private val externalMappingKey = ExternalMappingKey.create<Any>("test.my.mapping")

  @BeforeEach
  fun setUp() {
    builder = createEmptyBuilder()
    another = createEmptyBuilder()
  }

  @AfterEach
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
    val entity = builder addEntity XParentEntity("Parent", MySource)
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
    val entity = builder addEntity XParentEntity("Parent", MySource)
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
    val entity = builder addEntity XParentEntity("Parent", MySource)
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
    val entity = builder addEntity XParentEntity("Parent", MySource)
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
    assertTrue(changeEntry is ChangeEntry.ReplaceEntity)
    assertEquals((changeEntry.data!!.newData as XParentEntityData).parentProperty, "Another Parent")
    assertEquals((changeEntry.data.newData as XParentEntityData).entitySource, AnotherSource)
  }

  @Test
  fun `modify plus remove`() {
    val entity = builder addEntity XParentEntity("Parent", MySource)
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
    val entity = builder addEntity XParentEntity("Parent", MySource)
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
    assertTrue(changeEntry is ChangeEntry.ReplaceEntity)
    assertEquals((changeEntry.data!!.newData as XParentEntityData).parentProperty, "Another Parent")
    assertEquals((changeEntry.data.newData as XParentEntityData).entitySource, AnotherSource)
  }

  @Test
  fun `change source plus remove`() {
    val entity = builder addEntity XParentEntity("Parent", MySource)
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
    val entity = builder addEntity XParentEntity("Parent", MySource)
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
    val entity = builder addEntity XParentEntity("Parent", MySource)
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
    assertInstanceOf<ChangeEntry.ReplaceEntity>(changeEntry)
    assertEquals(((changeEntry as ChangeEntry.ReplaceEntity).data!!.newData as XParentEntityData).parentProperty, "Another Parent")
    assertIs<SampleEntitySource>((changeEntry.data!!.newData as XParentEntityData).entitySource)
  }

  @Test
  fun `change source and modify plus modify`() {
    val entity = builder addEntity XParentEntity("Parent", MySource)
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
    assertInstanceOf<ChangeEntry.ReplaceEntity>(changeEntry)
    assertEquals(((changeEntry as ChangeEntry.ReplaceEntity).data!!.newData as XParentEntityData).parentProperty,
                 "Third Parent")
    assertEquals((changeEntry.data!!.newData as XParentEntityData).entitySource,
                 AnotherSource)
  }

  @Test
  fun `remove one to one child`() {
    val entity = builder addEntity XParentEntity("Parent", MySource)
    val child1 = builder addEntity XChildWithOptionalParentEntity("child", MySource) {
      optionalParent = entity
    }
    builder addEntity XChildWithOptionalParentEntity("child", MySource) {
      optionalParent = entity
    }
    builder.changeLog.clear()

    builder.removeEntity(child1)

    val log = builder.changeLog.changeLog
    assertEquals(2, log.size)
    assertTrue(log.values.any { it is ChangeEntry.RemoveEntity })
    val changeEntry = log.values.single { it is ChangeEntry.ReplaceEntity }
    assertTrue(changeEntry is ChangeEntry.ReplaceEntity)
    assertEquals(1, changeEntry.references!!.removedChildren.size)
    assertEquals(0, changeEntry.references.newChildren.size)
    assertEquals(0, changeEntry.references.newParents.size)
    assertEquals(0, changeEntry.references.removedParents.size)
  }

  @Test
  fun `modify add children`() {
    val entity = builder addEntity XParentEntity("Parent", MySource)
    val firstChild = builder addEntity XChildWithOptionalParentEntity("child", MySource)
    val secondChild = builder addEntity XChildWithOptionalParentEntity("child", MySource)
    builder.changeLog.clear()

    builder.modifyEntity(entity) {
      this.optionalChildren = listOf(firstChild, secondChild)
    }

    val log = builder.changeLog.changeLog
    assertEquals(3, log.size)

    log[entity.asBase().id]!!.assertReplaceEntity(newChildren = 2)
    log[firstChild.asBase().id]!!.assertReplaceEntity(newParents = 1)
    log[secondChild.asBase().id]!!.assertReplaceEntity(newParents = 1)
  }

  @Test
  fun `modify remove children`() {
    val entity = builder addEntity XParentEntity("Parent", MySource)
    val childOne = builder addEntity XChildWithOptionalParentEntity("child", MySource) {
      optionalParent = entity
    }
    val childTwo = builder addEntity XChildWithOptionalParentEntity("child", MySource) {
      optionalParent = entity
    }
    builder.changeLog.clear()

    builder.modifyEntity(entity) {
      this.optionalChildren = listOf()
    }

    val log = builder.changeLog.changeLog
    assertEquals(3, log.size)

    log[entity.asBase().id].assertReplaceEntity(removedChildren = 2)
    log[childOne.asBase().id].assertReplaceEntity(removedParents = 1)
    log[childTwo.asBase().id].assertReplaceEntity(removedParents = 1)
  }

  @Test
  fun `remove child with not nullable parent by modifying parent`() {
    val parent = builder addEntity XParentEntity("Parent", MySource)
    builder addEntity XChildEntity("child", MySource) {
      this.parentEntity = parent
    }
    builder.changeLog.clear()

    builder.modifyEntity(parent) {
      this.children = listOf()
    }

    val log = builder.changeLog.changeLog
    assertEquals(2, log.size)
    assertTrue(log.values.any { it is ChangeEntry.RemoveEntity })
    val changeEntry = log.values.single { it is ChangeEntry.ReplaceEntity }
    assertTrue(changeEntry is ChangeEntry.ReplaceEntity)
    assertEquals(1, changeEntry.references!!.removedChildren.size)
    assertEquals(0, changeEntry.references.newChildren.size)
    assertEquals(0, changeEntry.references.newParents.size)
    assertEquals(0, changeEntry.references.removedParents.size)
  }

  @Test
  fun `set parent to null on child`() {
    val parent = builder addEntity XParentEntity("Parent", MySource)
    val child = builder addEntity XChildWithOptionalParentEntity("child", MySource) {
      this.optionalParent = parent
    }
    builder.changeLog.clear()

    builder.modifyEntity(child) {
      this.optionalParent = null
    }

    val log = builder.changeLog.changeLog
    assertEquals(2, log.size)

    log[builder.entities(XParentEntity::class.java).single().asBase().id].assertReplaceEntity(removedChildren = 1)

    log[builder.entities(XChildWithOptionalParentEntity::class.java).single().asBase().id].assertReplaceEntity(removedParents = 1)
  }

  @Test
  fun `modify add and remove children`() {
    val entity = builder addEntity XParentEntity("Parent", MySource)
    val child = builder addEntity XChildWithOptionalParentEntity("child", MySource)
    val removedChild = builder addEntity XChildWithOptionalParentEntity("child", MySource) {
      optionalParent = entity
    }
    builder.changeLog.clear()

    builder.modifyEntity(entity) {
      this.optionalChildren = listOf(child)
    }

    val log = builder.changeLog.changeLog
    assertEquals(3, log.size)

    log[entity.asBase().id].assertReplaceEntity(removedChildren = 1, newChildren = 1)
    log[removedChild.asBase().id].assertReplaceEntity(removedParents = 1)
    log[child.asBase().id].assertReplaceEntity(newParents = 1)
  }

  @Test
  fun `change one to one child`() {
    val parent = builder addEntity OoParentEntity("data", MySource) {
      this.child = OoChildEntity("info1", MySource)
    }
    builder.changeLog.clear()

    builder.modifyEntity(parent) {
      this.child = OoChildEntity("info2", MySource)
    }

    val log = builder.changeLog.changeLog
    assertEquals(3, log.size)

    assertTrue(log.values.any { it is ChangeEntry.RemoveEntity })
    assertTrue(log.values.any { it is ChangeEntry.AddEntity })
    val changeEntry = log.values.single { it is ChangeEntry.ReplaceEntity }
    assertTrue(changeEntry is ChangeEntry.ReplaceEntity)
    assertEquals(1, changeEntry.references!!.removedChildren.size)
    assertEquals(1, changeEntry.references.newChildren.size)
    assertEquals(0, changeEntry.references.newParents.size)
    assertEquals(0, changeEntry.references.removedParents.size)
  }

  @Test
  fun `remove child by parent modification`() {
    val parent = builder addEntity OoParentEntity("data", MySource) {
      this.child = OoChildEntity("info1", MySource)
    }
    builder.changeLog.clear()

    builder.modifyEntity(parent) {
      this.child = null
    }

    val log = builder.changeLog.changeLog
    assertEquals(2, log.size)

    assertTrue(log.values.any { it is ChangeEntry.RemoveEntity })
    val changeEntry = log.values.single { it is ChangeEntry.ReplaceEntity }
    assertTrue(changeEntry is ChangeEntry.ReplaceEntity)
    assertEquals(1, changeEntry.references!!.removedChildren.size)
    assertEquals(0, changeEntry.references.newChildren.size)
    assertEquals(0, changeEntry.references.newParents.size)
    assertEquals(0, changeEntry.references.removedParents.size)
  }

  @Test
  fun `modify twice add and remove children`() {
    val entity = builder addEntity XParentEntity("Parent", MySource)
    val firstChild = builder addEntity XChildWithOptionalParentEntity("child", MySource)
    val secondChild = builder addEntity XChildWithOptionalParentEntity("child", MySource)
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
    val entity = builder addEntity XParentEntity("Parent", MySource)
    val firstChild = builder addEntity XChildWithOptionalParentEntity("child", MySource) {
      optionalParent = entity
    }
    val secondChild = builder addEntity XChildWithOptionalParentEntity("child", MySource) {
      optionalParent = entity
    }
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
    val entity = builder addEntity XParentEntity("Parent", MySource)
    val firstChild = builder addEntity XChildWithOptionalParentEntity("child", MySource) {
      optionalParent = entity
    }
    val secondChild = builder addEntity XChildWithOptionalParentEntity("child", MySource) {
      optionalParent = entity
    }
    builder.changeLog.clear()

    builder.modifyEntity(entity) {
      this.optionalChildren = listOf(firstChild)
    }

    builder.modifyEntity(entity) {
      this.optionalChildren = listOf()
    }

    val log = builder.changeLog.changeLog
    assertEquals(3, log.size)

    log[entity.asBase().id].assertReplaceEntity(removedChildren = 2)
    log[firstChild.asBase().id].assertReplaceEntity(removedParents = 1)
    log[secondChild.asBase().id].assertReplaceEntity(removedParents = 1)
  }

  @Test
  fun `modify twice add and add children`() {
    val entity = builder addEntity XParentEntity("Parent", MySource)
    val firstChild = builder addEntity XChildWithOptionalParentEntity("child", MySource)
    val secondChild = builder addEntity XChildWithOptionalParentEntity("child", MySource)
    builder.changeLog.clear()

    builder.modifyEntity(entity) {
      this.optionalChildren = listOf(firstChild)
    }

    builder.modifyEntity(entity) {
      this.optionalChildren = listOf(firstChild, secondChild)
    }

    val log = builder.changeLog.changeLog
    assertEquals(3, log.size)

    log[entity.asBase().id].assertReplaceEntity(newChildren = 2)
    log[firstChild.asBase().id].assertReplaceEntity(newParents = 1)
    log[secondChild.asBase().id].assertReplaceEntity(newParents = 1)
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
    val parent = builder addEntity XParentEntity("parent", MySource)
    val child1 = builder addEntity XChildWithOptionalParentEntity("child", MySource) {
      optionalParent = parent
    }
    val child2 = builder addEntity XChildWithOptionalParentEntity("child", MySource) {
      optionalParent = parent
    }
    val child3 = builder addEntity XChildWithOptionalParentEntity("child", MySource) {
      optionalParent = parent
    }
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
    assertInstanceOf<ChangeEntry.ReplaceEntity>(log.entries.single().value)
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
    val entity = builder addEntity SourceEntity("one", oldSource)
    builder.modifyEntity(entity) {
      this.entitySource = SampleEntitySource("newSource")
    }
    val change = builder.changeLog.changeLog.values.single()
    assertInstanceOf<ChangeEntry.AddEntity>(change)
  }

  @Test
  fun `join remove plus add`() {
    val oldSource = SampleEntitySource("oldSource")
    val entity = builder addEntity SourceEntity("one", oldSource)
    builder.changeLog.clear()
    val original = builder.toSnapshot()
    builder.removeEntity(entity)
    builder addEntity SourceEntity("one", oldSource)
    assertTrue(builder.hasSameEntities())
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  @Test
  fun `join remove plus add content root`() {
    val moduleTestEntity = ModuleTestEntity("data", MySource) {
      contentRoots = listOf(
        ContentRootTestEntity(MySource)
      )
    }
    builder.addEntity(moduleTestEntity)
    val newBuilder = builder.toSnapshot().toBuilder()
    newBuilder.removeEntity(newBuilder.entities(ModuleTestEntity::class.java).single().contentRoots.single())
    newBuilder.addEntity(ContentRootTestEntity(MySource) {
      module = moduleTestEntity
    })
    assertTrue(newBuilder.instrumentation.hasSameEntities())
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
    builder.getMutableExternalMapping(externalMappingKey).addMapping(contentRoot, 1)
    val original = builder.toSnapshot()
    builder.removeEntity(contentRoot)
    builder.addEntity(ContentRootTestEntity(MySource) {
      module = moduleTestEntity
    })
    assertFalse(builder.hasSameEntities())
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
    builder.getMutableExternalMapping(externalMappingKey).addMapping(newContentRoot, 1)
    assertFalse(builder.hasSameEntities())
  }

  // ------------- Testing events collapsing end ----

  @Test
  fun `make a modification of references then modification of data then revert modification of references`() {
    val child = builder addEntity OoChildWithNullableParentEntity(MySource)

    val newBuilder = builder.toSnapshot().toBuilder() as MutableEntityStorageImpl

    val newParent1 = newBuilder addEntity OoParentEntity("data", MySource)
    newBuilder.modifyEntity(child.from(newBuilder)) {
      this.parentEntity = newParent1
    }
    newBuilder.modifyEntity(child.from(newBuilder)) {
      this.entitySource = AnotherSource
    }
    newBuilder.modifyEntity(child.from(newBuilder)) {
      this.parentEntity = null
    }

    val log = newBuilder.changeLog.changeLog
    assertNull(log.values.filterIsInstance<ChangeEntry.ReplaceEntity>().single().references)
  }

  @Test
  fun updateParentOfOneToOneChild() {
    val child = builder addEntity ChildSampleEntity("data", MySource)
    val newBuilder = builder.toSnapshot().toBuilder() as MutableEntityStorageImpl
    newBuilder addEntity SampleEntity(true, "", listOf(), emptyMap(), VirtualFileUrlManagerImpl().fromUrl("file:///tmp"), MySource) {
      this.children = listOf(child)
    }

    val log = newBuilder.changeLog.changeLog
    assertEquals(2, log.size)
    assertNotNull(log.values.filterIsInstance<ChangeEntry.AddEntity>().singleOrNull())
    assertTrue(log.values.filterIsInstance<ChangeEntry.ReplaceEntity>().single().references!!.newParents.isNotEmpty())
  }

  @Test
  fun setNewChildToOneToOneParent() {
    val parent = builder addEntity OptionalOneToOneParentEntity(MySource) {
      this.child = OptionalOneToOneChildEntity("data", MySource)
    }
    val newBuilder = builder.toSnapshot().toBuilder() as MutableEntityStorageImpl
    newBuilder addEntity OptionalOneToOneChildEntity("newData", MySource) {
      this.parent = parent.from(newBuilder)
    }

    val log = newBuilder.changeLog.changeLog
    assertEquals(3, log.size)
    assertNotNull(log.values.filterIsInstance<ChangeEntry.AddEntity>().singleOrNull())

    val parentReferences = (log[(parent.from(newBuilder) as OptionalOneToOneParentEntityImpl).id] as ChangeEntry.ReplaceEntity).references!!
    assertTrue(parentReferences.removedChildren.isNotEmpty())
    assertTrue(parentReferences.newChildren.isNotEmpty())
    assertTrue(parentReferences.removedParents.isEmpty())
    assertTrue(parentReferences.newParents.isEmpty())

    val childReferences = (log[(parent.child!!.from(
      newBuilder) as OptionalOneToOneChildEntityImpl).id] as ChangeEntry.ReplaceEntity).references!!
    assertTrue(childReferences.removedParents.isNotEmpty())
    assertTrue(childReferences.newParents.isEmpty())
    assertTrue(childReferences.removedChildren.isEmpty())
    assertTrue(childReferences.newChildren.isEmpty())
  }

  @Test
  fun `update abstract children in entity`() {
    val remainedChild = RightEntity(MySource)
    val eliminatedChild = RightEntity(AnotherSource)
    val parent = builder addEntity LeftEntity(MySource) {
      this.children = listOf(
        remainedChild,
        eliminatedChild,
      )
    }

    builder.changeLog.clear()

    val newChild = RightEntity(SampleEntitySource("sample"))
    builder.modifyEntity(parent) {
      this.children = listOf (
        newChild,
        remainedChild,
      )
    }

    val log = builder.changeLog.changeLog
    assertEquals(3, log.size)

    assertTrue(log[newChild.asBase().id] is ChangeEntry.AddEntity)

    log[parent.asBase().id].assertReplaceEntity(removedChildren = 1, newChildren = 1)
    log[eliminatedChild.asBase().id].assertReplaceEntity(removedParents = 1)
  }

  @Test
  fun `update abstract children in one to one entity`() {
    val parent = builder addEntity ParentWithExtensionEntity("data", MySource) {
      this.child = SpecificChildEntity("data", MySource)
    }

    builder.changeLog.clear()

    builder.modifyEntity(parent) {
      this.child = SpecificChildEntity("data2", MySource)
    }

    val log = builder.changeLog.changeLog
    assertEquals(3, log.size)

    assertTrue(log.values.any { it is ChangeEntry.AddEntity })
    assertTrue(log.values.any { it is ChangeEntry.RemoveEntity })
    val replaceEntity = log.values.single { it is ChangeEntry.ReplaceEntity } as ChangeEntry.ReplaceEntity
    assertEquals(1, replaceEntity.references!!.removedChildren.size)
    assertEquals(1, replaceEntity.references.newChildren.size)
    assertEquals(0, replaceEntity.references.newParents.size)
    assertEquals(0, replaceEntity.references.removedParents.size)
  }

  @Test
  fun `update data and back with updating references`() {
    val parent = builder addEntity ParentEntity("info", MySource)

    builder.changeLog.clear()

    builder.modifyEntity(parent) {
      this.child = ChildEntity("info", MySource)
    }

    builder.modifyEntity(parent) {
      this.parentData = "ChangedInfo"
    }
    builder.modifyEntity(parent) {
      this.parentData = "info"
    }

    val log = builder.changeLog.changeLog
    assertEquals(2, log.size)

    val replaceEvent = log[parent.asBase().id].assertReplaceEntity(newChildren = 1)
    assertNull(replaceEvent.data)
  }

  private fun ChangeEntry?.assertReplaceEntity(removedChildren: Int = 0,
                                               newChildren: Int = 0,
                                               newParents: Int = 0,
                                               removedParents: Int = 0): ChangeEntry.ReplaceEntity {
    assertTrue(this is ChangeEntry.ReplaceEntity)
    assertAll(
      { assertEquals(removedChildren, references!!.removedChildren.size) },
      { assertEquals(newChildren, references!!.newChildren.size) },
      { assertEquals(newParents, references!!.newParents.size) },
      { assertEquals(removedParents, references!!.removedParents.size) },
    )
    return this
  }
}
