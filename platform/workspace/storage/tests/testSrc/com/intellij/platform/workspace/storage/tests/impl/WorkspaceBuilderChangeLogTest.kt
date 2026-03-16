// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests.impl

import com.intellij.platform.workspace.storage.ExternalMappingKey
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.ChangeEntry
import com.intellij.platform.workspace.storage.impl.MutableEntityStorageImpl
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.asBase
import com.intellij.platform.workspace.storage.impl.assertConsistency
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.testEntities.entities.AnotherSource
import com.intellij.platform.workspace.storage.testEntities.entities.ChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.ChildSampleEntity
import com.intellij.platform.workspace.storage.testEntities.entities.ContentRootTestEntity
import com.intellij.platform.workspace.storage.testEntities.entities.LeftEntity
import com.intellij.platform.workspace.storage.testEntities.entities.ModuleTestEntity
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.platform.workspace.storage.testEntities.entities.NamedChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.NamedEntity
import com.intellij.platform.workspace.storage.testEntities.entities.OoChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.OoChildWithNullableParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.OoParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.OptionalOneToOneChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.OptionalOneToOneParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.ParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.ParentWithExtensionEntity
import com.intellij.platform.workspace.storage.testEntities.entities.RightEntity
import com.intellij.platform.workspace.storage.testEntities.entities.SampleEntity
import com.intellij.platform.workspace.storage.testEntities.entities.SampleEntitySource
import com.intellij.platform.workspace.storage.testEntities.entities.SourceEntity
import com.intellij.platform.workspace.storage.testEntities.entities.SpecificChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.XChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.XChildWithOptionalParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.XParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.child
import com.intellij.platform.workspace.storage.testEntities.entities.modifyLeftEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifyNamedChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifyNamedEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifyOoChildWithNullableParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifyOoParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifyParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifyParentWithExtensionEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifySourceEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifyXChildWithOptionalParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifyXParentEntity
import com.intellij.platform.workspace.storage.tests.builderFrom
import com.intellij.platform.workspace.storage.tests.createEmptyBuilder
import com.intellij.platform.workspace.storage.tests.from
import com.intellij.platform.workspace.storage.toBuilder
import com.intellij.testFramework.assertInstanceOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    val entity = builder addEntity NamedEntity("Parent", MySource)
    builder.removeEntity(entity)

    val log = builder.changeLog.changeLog
    assertEquals(0, log.size)
  }

  @Test
  fun `add plus modify`() {
    val entity = builder addEntity XParentEntity("Parent", MySource)
    builder.modifyXParentEntity(entity) {
      this.parentProperty = "Another Parent"
    }

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.AddEntity)

    val entityData = changeEntry.entityData
    assertEquals("com.intellij.platform.workspace.storage.testEntities.entities.impl.XParentEntityData", entityData::class.java.name)
    assertEquals("Another Parent", entityData.getPropertyValue("parentProperty"))
  }

  @Test
  fun `add plus change source`() {
    val entity = builder addEntity XParentEntity("Parent", MySource)
    builder.modifyXParentEntity(entity) {
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
    builder.modifyXParentEntity(entity) {
      this.entitySource = AnotherSource
    }
    builder.modifyXParentEntity(entity) {
      this.parentProperty = "Another Parent"
    }

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.AddEntity)

    val entityData = changeEntry.entityData
    assertEquals("com.intellij.platform.workspace.storage.testEntities.entities.impl.XParentEntityData", entityData::class.java.name)
    assertEquals("Another Parent", entityData.getPropertyValue("parentProperty"))
    assertIs<AnotherSource>(entityData.getPropertyValue("entitySource"))
  }

  @Test
  fun `modify plus change source`() {
    val entity = builder addEntity XParentEntity("Parent", MySource)
    builder.changeLog.clear()
    builder.modifyXParentEntity(entity) {
      this.parentProperty = "Another Parent"
    }
    builder.modifyXParentEntity(entity) {
      this.entitySource = AnotherSource
    }

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.ReplaceEntity)

    val entityData = changeEntry.data!!.newData
    assertEquals("com.intellij.platform.workspace.storage.testEntities.entities.impl.XParentEntityData", entityData::class.java.name)
    assertEquals("Another Parent", entityData.getPropertyValue("parentProperty"))
    assertIs<AnotherSource>(entityData.getPropertyValue("entitySource"))
  }

  @Test
  fun `modify plus remove`() {
    val entity = builder addEntity XParentEntity("Parent", MySource)
    builder.changeLog.clear()
    builder.modifyXParentEntity(entity) {
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
    builder.modifyXParentEntity(entity) {
      this.entitySource = AnotherSource
    }
    builder.modifyXParentEntity(entity) {
      this.parentProperty = "Another Parent"
    }

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertTrue(changeEntry is ChangeEntry.ReplaceEntity)

    val entityData = changeEntry.data!!.newData
    assertEquals("com.intellij.platform.workspace.storage.testEntities.entities.impl.XParentEntityData", entityData::class.java.name)
    assertEquals("Another Parent", entityData.getPropertyValue("parentProperty"))
    assertIs<AnotherSource>(entityData.getPropertyValue("entitySource"))
  }

  @Test
  fun `change source plus remove`() {
    val entity = builder addEntity XParentEntity("Parent", MySource)
    builder.changeLog.clear()
    builder.modifyXParentEntity(entity) {
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
    builder.modifyXParentEntity(entity) {
      this.entitySource = AnotherSource
    }
    builder.modifyXParentEntity(entity) {
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
    builder.modifyXParentEntity(entity) {
      this.entitySource = AnotherSource
    }
    builder.modifyXParentEntity(entity) {
      this.parentProperty = "Another Parent"
    }
    builder.modifyXParentEntity(entity) {
      this.entitySource = SampleEntitySource("X")
    }

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertInstanceOf<ChangeEntry.ReplaceEntity>(changeEntry)

    val entityData = (changeEntry as ChangeEntry.ReplaceEntity).data!!.newData
    assertEquals("com.intellij.platform.workspace.storage.testEntities.entities.impl.XParentEntityData", entityData::class.java.name)
    assertEquals("Another Parent", entityData.getPropertyValue("parentProperty"))
    assertIs<SampleEntitySource>(entityData.getPropertyValue("entitySource"))
  }

  @Test
  fun `change source and modify plus modify`() {
    val entity = builder addEntity XParentEntity("Parent", MySource)
    builder.changeLog.clear()
    builder.modifyXParentEntity(entity) {
      entitySource = AnotherSource
    }
    builder.modifyXParentEntity(entity) {
      this.parentProperty = "Another Parent"
    }
    builder.modifyXParentEntity(entity) {
      this.parentProperty = "Third Parent"
    }

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    val changeEntry = log.values.single()
    assertInstanceOf<ChangeEntry.ReplaceEntity>(changeEntry)

    val entityData = (changeEntry as ChangeEntry.ReplaceEntity).data!!.newData
    assertEquals("com.intellij.platform.workspace.storage.testEntities.entities.impl.XParentEntityData", entityData::class.java.name)
    assertEquals("Third Parent", entityData.getPropertyValue("parentProperty"))
    assertEquals(AnotherSource, entityData.getPropertyValue("entitySource"))
  }

  private fun <T> WorkspaceEntityData<*>.getPropertyValue(propertyName: String): T {
    val property = this::class.memberProperties.first { it.name == propertyName } as KProperty1<WorkspaceEntityData<out WorkspaceEntity>, T>
    return property.get(this)
  }

  @Test
  fun `remove one to one child`() {
    val entity = builder addEntity XParentEntity("Parent", MySource)
    val child1 = builder addEntity XChildWithOptionalParentEntity("child", MySource) {
      optionalParent = entity.builderFrom(builder)
    }
    builder addEntity XChildWithOptionalParentEntity("child", MySource) {
      optionalParent = entity.builderFrom(builder)
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

    builder.modifyXParentEntity(entity) {
      this.optionalChildren = listOf(firstChild.builderFrom(builder), secondChild.builderFrom(builder))
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
      optionalParent = entity.builderFrom(builder)
    }
    val childTwo = builder addEntity XChildWithOptionalParentEntity("child", MySource) {
      optionalParent = entity.builderFrom(builder)
    }
    builder.changeLog.clear()

    builder.modifyXParentEntity(entity) {
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
      this.parentEntity = parent.builderFrom(builder)
    }
    builder.changeLog.clear()

    builder.modifyXParentEntity(parent) {
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
      this.optionalParent = parent.builderFrom(builder)
    }
    builder.changeLog.clear()

    builder.modifyXChildWithOptionalParentEntity(child) {
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
      optionalParent = entity.builderFrom(builder)
    }
    builder.changeLog.clear()

    builder.modifyXParentEntity(entity) {
      this.optionalChildren = listOf(child.builderFrom(builder))
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

    builder.modifyOoParentEntity(parent) {
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

    builder.modifyOoParentEntity(parent) {
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

    builder.modifyXParentEntity(entity) {
      this.optionalChildren = listOf(firstChild.builderFrom(builder), secondChild.builderFrom(builder))
    }

    builder.modifyXParentEntity(entity) {
      this.optionalChildren = listOf()
    }

    val log = builder.changeLog.changeLog
    assertEquals(0, log.size)
  }

  @Test
  fun `modify twice remove and add children`() {
    val entity = builder addEntity XParentEntity("Parent", MySource)
    val firstChild = builder addEntity XChildWithOptionalParentEntity("child", MySource) {
      optionalParent = entity.builderFrom(builder)
    }
    val secondChild = builder addEntity XChildWithOptionalParentEntity("child", MySource) {
      optionalParent = entity.builderFrom(builder)
    }
    builder.changeLog.clear()

    builder.modifyXParentEntity(entity) {
      this.optionalChildren = listOf()
    }

    builder.modifyXParentEntity(entity) {
      this.optionalChildren = listOf(firstChild.builderFrom(builder), secondChild.builderFrom(builder))
    }

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    log.values.single().assertReplaceEntity()
  }

  @Test
  fun `modify twice remove and remove children`() {
    val entity = builder addEntity XParentEntity("Parent", MySource)
    val firstChild = builder addEntity XChildWithOptionalParentEntity("child", MySource) {
      optionalParent = entity.builderFrom(builder)
    }
    val secondChild = builder addEntity XChildWithOptionalParentEntity("child", MySource) {
      optionalParent = entity.builderFrom(builder)
    }
    builder.changeLog.clear()

    builder.modifyXParentEntity(entity) {
      this.optionalChildren = listOf(firstChild.builderFrom(builder))
    }

    builder.modifyXParentEntity(entity) {
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

    builder.modifyXParentEntity(entity) {
      this.optionalChildren = listOf(firstChild.builderFrom(builder))
    }

    builder.modifyXParentEntity(entity) {
      this.optionalChildren = listOf(firstChild.builderFrom(builder), secondChild.builderFrom(builder))
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
    val entity = builder addEntity NamedEntity("Parent", MySource)
    builder.changeLog.clear()

    builder.modifyNamedEntity(entity) {}

    val log = builder.changeLog.changeLog
    assertEquals(0, log.size)
  }

  @Test
  fun `collaps two modify`() {
    var entity = builder addEntity NamedEntity("Parent", MySource)
    builder.changeLog.clear()

    entity = builder.modifyNamedEntity(entity) {
      myName = "AnotherName"
    }
    builder.modifyNamedEntity(entity) {
      myName = "Parent"
    }

    val log = builder.changeLog.changeLog
    assertEquals(0, log.size)
  }

  @Test
  fun `collaps two modify with parent refs`() {
    val parent1 = builder addEntity NamedEntity("Parent", MySource)
    val parent2 = builder addEntity NamedEntity("Parent2", MySource)
    val child1 = builder addEntity NamedChildEntity("child", MySource) {
      this.parentEntity = parent1.builderFrom(builder)
    }
    builder.changeLog.clear()

    builder.modifyNamedChildEntity(child1) {
      this.parentEntity = parent2.builderFrom(builder)
    }

    builder.modifyNamedChildEntity(child1) {
      this.parentEntity = parent1.builderFrom(builder)
    }

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
  }

  @Test
  fun `collaps two modify with children refs`() {
    val parent = builder addEntity XParentEntity("parent", MySource)
    val child1 = builder addEntity XChildWithOptionalParentEntity("child", MySource) {
      optionalParent = parent.builderFrom(builder)
    }
    val child2 = builder addEntity XChildWithOptionalParentEntity("child", MySource) {
      optionalParent = parent.builderFrom(builder)
    }
    val child3 = builder addEntity XChildWithOptionalParentEntity("child", MySource) {
      optionalParent = parent.builderFrom(builder)
    }
    builder.changeLog.clear()

    builder.modifyXParentEntity(parent) {
      this.optionalChildren = listOf()
    }

    builder.modifyXParentEntity(parent) {
      this.optionalChildren = listOf(child1.builderFrom(builder), child2.builderFrom(builder), child3.builderFrom(builder))
    }

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    log.values.single().assertReplaceEntity()
  }

  @Test
  fun `collaps three modify`() {
    var entity = builder addEntity NamedEntity("Parent", MySource)
    builder.changeLog.clear()

    entity = builder.modifyNamedEntity(entity) {
      myName = "AnotherName"
    }
    entity = builder.modifyNamedEntity(entity) {
      myName = "AndAnotherName"
    }
    builder.modifyNamedEntity(entity) {
      myName = "Parent"
    }

    val log = builder.changeLog.changeLog
    assertEquals(0, log.size)
  }

  @Test
  fun `collaps two modify and source change`() {
    var entity = builder addEntity NamedEntity("Parent", MySource)
    builder.changeLog.clear()

    entity = builder.modifyNamedEntity(entity) {
      myName = "AnotherName"
    }
    entity = builder.modifyNamedEntity(entity) {
      this.entitySource = AnotherSource
    }
    builder.modifyNamedEntity(entity) {
      myName = "Parent"
    }

    val log = builder.changeLog.changeLog
    assertEquals(1, log.size)
    assertInstanceOf<ChangeEntry.ReplaceEntity>(log.entries.single().value)
  }

  @Test
  fun `collaps two modify and two source change`() {
    var entity = builder addEntity NamedEntity("Parent", MySource)
    builder.changeLog.clear()

    entity = builder.modifyNamedEntity(entity) {
      myName = "AnotherName"
    }
    entity = builder.modifyNamedEntity(entity) {
      this.entitySource = AnotherSource
    }
    entity = builder.modifyNamedEntity(entity) {
      this.entitySource = MySource
    }
    builder.modifyNamedEntity(entity) {
      myName = "Parent"
    }

    val log = builder.changeLog.changeLog
    assertEquals(0, log.size)
  }

  @Test
  fun `collaps two modify and two source change in mix`() {
    var entity = builder addEntity NamedEntity("Parent", MySource)
    builder.changeLog.clear()

    entity = builder.modifyNamedEntity(entity) {
      myName = "AnotherName"
    }
    entity = builder.modifyNamedEntity(entity) {
      this.entitySource = AnotherSource
    }
    entity =builder.modifyNamedEntity(entity) {
      myName = "Parent"
    }
    builder.modifyNamedEntity(entity) {
      this.entitySource = MySource
    }

    val log = builder.changeLog.changeLog
    assertEquals(0, log.size)
  }

  @Test
  fun `collaps empty source change`() {
    val entity = builder addEntity NamedEntity("Parent", MySource)
    builder.changeLog.clear()

    builder.modifyNamedEntity(entity) {
      this.entitySource = MySource
    }

    val log = builder.changeLog.changeLog
    assertEquals(0, log.size)
  }

  @Test
  fun `collaps source change twice`() {
    val entity = builder addEntity NamedEntity("Parent", MySource)
    builder.changeLog.clear()

    builder.modifyNamedEntity(entity) {
      this.entitySource = AnotherSource
    }
    builder.modifyNamedEntity(entity) {
      this.entitySource = MySource
    }

    val log = builder.changeLog.changeLog
    assertEquals(0, log.size)
  }

  @Test
  fun `add and change source`() {
    val oldSource = SampleEntitySource("oldSource")
    val entity = builder addEntity SourceEntity("one", oldSource)
    builder.modifySourceEntity(entity) {
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
    val newContentRoot = builder addEntity ContentRootTestEntity(MySource) {
      module = moduleTestEntity
    }
    builder.getMutableExternalMapping(externalMappingKey).addMapping(newContentRoot, 1)
    assertFalse(builder.hasSameEntities())
  }

  // ------------- Testing events collapsing end ----

  @Test
  fun `make a modification of references then modification of data then revert modification of references`() {
    val child = builder addEntity OoChildWithNullableParentEntity(MySource)

    val newBuilder = builder.toSnapshot().toBuilder() as MutableEntityStorageImpl

    val newParent1 = newBuilder addEntity OoParentEntity("data", MySource)
    newBuilder.modifyOoChildWithNullableParentEntity(child.from(newBuilder)) {
      this.parentEntity = newParent1.builderFrom(newBuilder)
    }
    newBuilder.modifyOoChildWithNullableParentEntity(child.from(newBuilder)) {
      this.entitySource = AnotherSource
    }
    newBuilder.modifyOoChildWithNullableParentEntity(child.from(newBuilder)) {
      this.parentEntity = null
    }

    val log = newBuilder.changeLog.changeLog
    assertNull(log.values.filterIsInstance<ChangeEntry.ReplaceEntity>().single().references)
  }

  @Test
  fun updateParentOfOneToOneChild() {
    val child = builder addEntity ChildSampleEntity("data", MySource)
    val newBuilder = builder.toSnapshot().toBuilder() as MutableEntityStorageImpl
    newBuilder addEntity SampleEntity(true, "", listOf(), emptyMap(), VirtualFileUrlManagerImpl().getOrCreateFromUrl("file:///tmp"), MySource) {
      this.children = listOf(child.builderFrom(newBuilder))
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
      this.parent = parent.builderFrom(newBuilder)
    }

    val log = newBuilder.changeLog.changeLog
    assertEquals(3, log.size)
    assertNotNull(log.values.filterIsInstance<ChangeEntry.AddEntity>().singleOrNull())

    val parentReferences = (log[(parent.from(newBuilder) as WorkspaceEntityBase).id] as ChangeEntry.ReplaceEntity).references!!
    assertTrue(parentReferences.removedChildren.isNotEmpty())
    assertTrue(parentReferences.newChildren.isNotEmpty())
    assertTrue(parentReferences.removedParents.isEmpty())
    assertTrue(parentReferences.newParents.isEmpty())

    val childReferences = (log[(parent.child!!.from(newBuilder) as WorkspaceEntityBase).id] as ChangeEntry.ReplaceEntity).references!!
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
    builder.modifyLeftEntity(parent) {
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

    builder.modifyParentWithExtensionEntity(parent) {
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

    builder.modifyParentEntity(parent) {
      this.child = ChildEntity("info", MySource)
    }

    builder.modifyParentEntity(parent) {
      this.parentData = "ChangedInfo"
    }
    builder.modifyParentEntity(parent) {
      this.parentData = "info"
    }

    val log = builder.changeLog.changeLog
    assertEquals(2, log.size)

    val replaceEvent = log[parent.asBase().id].assertReplaceEntity(newChildren = 1)
    assertNull(replaceEvent.data)
  }

  private fun ChangeEntry?.assertReplaceEntity(
    removedChildren: Int = 0,
    newChildren: Int = 0,
    newParents: Int = 0,
    removedParents: Int = 0,
  ): ChangeEntry.ReplaceEntity {
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
