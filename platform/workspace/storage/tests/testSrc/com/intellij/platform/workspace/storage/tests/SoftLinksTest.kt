// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.assertConsistency
import com.intellij.platform.workspace.storage.testEntities.entities.*
import com.intellij.testFramework.UsefulTestCase.assertOneElement
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Soft reference
 * Symbolic id via soft reference
 * Symbolic id via strong reference
 */
class SoftLinksTest {
  @Test
  fun `test add diff with soft links`() {
    val id = "MyId"
    val newId = "MyNewId"

    // Setup builder for test
    val builder = createEmptyBuilder()
    builder.addEntity(WithSoftLinkEntity(NameId(id), MySource))
    builder.addEntity(NamedEntity(id, MySource) {
      this.children = emptyList()
    })

    // Change symbolic id in a different builder
    val newBuilder = createBuilderFrom(builder.toSnapshot())
    val entity = newBuilder.resolve(NameId(id))!!
    newBuilder.modifyNamedEntity(entity) {
      this.myName = newId
    }

    // Apply changes
    builder.applyChangesFrom(newBuilder)

    // Check
    assertTrue(NameId(newId) in builder)
    assertOneElement(builder.referrers(NameId(newId), WithSoftLinkEntity::class.java).toList())
    // Check first added/last removed
    assertTrue(NameId(id) in builder.changeLog.removedSymbolicIds[WithSoftLinkEntity::class.java]!!)
    assertTrue(NameId(newId) in builder.changeLog.addedSymbolicIds[WithSoftLinkEntity::class.java]!!)
    assertTrue(NameId(id) !in builder.changeLog.addedSymbolicIds[WithSoftLinkEntity::class.java]!!)
  }

  @Test
  fun `test add diff with soft links and back`() {
    val id = "MyId"
    val newId = "MyNewId"

    // Setup builder for test
    val builder = createEmptyBuilder()
    builder.addEntity(WithSoftLinkEntity(NameId(id), MySource))
    builder.addEntity(NamedEntity(id, MySource) {
      this.children = emptyList()
    })

    // Change symbolic id in a different builder
    val newBuilder = createBuilderFrom(builder.toSnapshot())
    val entity = newBuilder.resolve(NameId(id))!!
    newBuilder.modifyNamedEntity(entity) {
      this.myName = newId
    }

    // Apply changes
    builder.applyChangesFrom(newBuilder)

    // Check
    assertNotNull(NameId(newId) in builder)
    assertOneElement(builder.referrers(NameId(newId), WithSoftLinkEntity::class.java).toList())
    assertTrue(NameId(id) in builder.changeLog.removedSymbolicIds[WithSoftLinkEntity::class.java]!!)
    assertTrue(NameId(newId) in builder.changeLog.addedSymbolicIds[WithSoftLinkEntity::class.java]!!)

    // Change symbolic id to the initial value
    val anotherNewBuilder = createBuilderFrom(builder.toSnapshot())
    val anotherEntity = anotherNewBuilder.resolve(NameId(newId))!!
    anotherNewBuilder.modifyNamedEntity(anotherEntity) {
      this.myName = id
    }

    // Apply changes
    builder.applyChangesFrom(anotherNewBuilder)

    // Check
    assertNotNull(NameId(id) in builder)
    assertOneElement(builder.referrers(NameId(id), WithSoftLinkEntity::class.java).toList())
    assertTrue(NameId(id) in builder.changeLog.addedSymbolicIds[WithSoftLinkEntity::class.java]!!)
    assertTrue(NameId(newId) in builder.changeLog.removedSymbolicIds[WithSoftLinkEntity::class.java]!!)
  }

  @Test
  fun `change symbolic id part`() {
    val builder = createEmptyBuilder()
    val entity = builder addEntity NamedEntity("Name", MySource) {
      this.additionalProperty = null
      children = emptyList()
    }
    builder addEntity WithSoftLinkEntity(entity.symbolicId, MySource)

    builder.modifyNamedEntity(entity) {
      this.myName = "newName"
    }

    builder.assertConsistency()
    assertTrue(NameId("newName") in builder.changeLog.addedSymbolicIds[WithSoftLinkEntity::class.java]!!)
    assertTrue(NameId("Name") in builder.changeLog.removedSymbolicIds[WithSoftLinkEntity::class.java]!!)
    assertEquals("newName", builder.entities(WithSoftLinkEntity::class.java).single().link.presentableName)
  }

  @Test
  fun `change symbolic id part of composed id entity`() {
    val builder = createEmptyBuilder()
    val entity = builder addEntity NamedEntity("Name", MySource) {
      this.additionalProperty = null
      children = emptyList()
    }
    builder addEntity ComposedIdSoftRefEntity("AnotherName", entity.symbolicId, MySource)

    builder.modifyNamedEntity(entity) {
      this.myName = "newName"
    }

    builder.assertConsistency()

    val updatedSymbolicId = builder.entities(ComposedIdSoftRefEntity::class.java).single().symbolicId
    assertEquals("newName", updatedSymbolicId.link.presentableName)
  }

  @Test
  fun `change symbolic id in list`() {
    val builder = createEmptyBuilder()
    val entity = builder addEntity NamedEntity("Name", MySource) {
      this.additionalProperty = null
      children = emptyList()
    }
    builder.addEntity(WithListSoftLinksEntity("xyz", listOf(NameId("Name")), MySource))

    builder.modifyNamedEntity(entity) {
      this.myName = "newName"
    }

    builder.assertConsistency()

    val updatedSymbolicId = builder.entities(WithListSoftLinksEntity::class.java).single()
    assertEquals("newName", updatedSymbolicId.links.single().presentableName)
  }

  @Test
  fun `change symbolic id part of composed id entity and with linked entity`() {
    val builder = createEmptyBuilder()
    val entity = builder addEntity NamedEntity("Name", MySource)
    val composedIdEntity = builder addEntity ComposedIdSoftRefEntity("AnotherName", entity.symbolicId, MySource)
    builder addEntity ComposedLinkEntity(composedIdEntity.symbolicId, MySource)

    builder.modifyNamedEntity(entity) {
      this.myName = "newName"
    }

    builder.assertConsistency()

    val updatedSymbolicId = builder.entities(ComposedIdSoftRefEntity::class.java).single().symbolicId
    assertEquals("newName", updatedSymbolicId.link.presentableName)
    assertEquals("newName", builder.entities(ComposedLinkEntity::class.java).single().link.link.presentableName)
  }

  @Test
  fun `links change`() {
    val builder = MutableEntityStorage.create()

    val entity = builder addEntity OneEntityWithSymbolicId("Data", MySource)
    val symbolicId = entity.symbolicId
    val softLinkEntity = EntityWithSoftLinks(symbolicId,
                                             listOf(symbolicId),
                                             Container(symbolicId),
                                             listOf(Container(symbolicId)),
                                             listOf(TooDeepContainer(listOf(DeepContainer(listOf(Container(symbolicId)), symbolicId)))),
                                             SealedContainer.BigContainer(symbolicId),
                                             listOf(SealedContainer.SmallContainer(symbolicId)),
                                             "Hello",
                                             listOf("Hello"),
                                             DeepSealedOne.DeepSealedTwo.DeepSealedThree.DeepSealedFour(symbolicId),
                                             MySource
    ) {
      optionalLink = symbolicId
      inOptionalContainer = Container(symbolicId)
      justNullableProperty = "Hello"
      this.children = emptyList()
    }
    builder.addEntity(softLinkEntity)

    builder.modifyEntity(OneEntityWithSymbolicId.Builder::class.java, entity) {
      myName = "AnotherData"
    }

    val updatedEntity = builder.entities(EntityWithSoftLinks::class.java).single()

    assertEquals("AnotherData", updatedEntity.link.name)
    assertEquals("AnotherData", updatedEntity.manyLinks.single().name)
    assertEquals("AnotherData", updatedEntity.optionalLink!!.name)
    assertEquals("AnotherData", updatedEntity.inContainer.id.name)
    assertEquals("AnotherData", updatedEntity.inOptionalContainer!!.id.name)
    assertEquals("AnotherData", updatedEntity.inContainerList.single().id.name)
    assertEquals("AnotherData", updatedEntity.deepContainer.single().goDeeper.single().goDeep.single().id.name)
    assertEquals("AnotherData", (updatedEntity.sealedContainer as SealedContainer.BigContainer).id.name)
    assertEquals("AnotherData", (updatedEntity.listSealedContainer.single() as SealedContainer.SmallContainer).notId.name)
    assertEquals("AnotherData",
                 (updatedEntity.deepSealedClass as DeepSealedOne.DeepSealedTwo.DeepSealedThree.DeepSealedFour).id.name)
  }

  @Test
  fun `many entities one symbolic id`() {
    val builder = createEmptyBuilder()
    val namedEntity = builder addEntity NamedEntity("Name", MySource)
    val entity = builder addEntity WithSoftLinkEntity(namedEntity.symbolicId, MySource)
    builder addEntity WithSoftLinkEntity(namedEntity.symbolicId, MySource)

    assertEquals( 1, builder.changeLog.addedSymbolicIds[WithSoftLinkEntity::class.java]?.size)
    assertTrue(NameId("Name") in builder.changeLog.addedSymbolicIds[WithSoftLinkEntity::class.java]!!)

    builder.removeEntity(entity)
    assertEquals( 1, builder.changeLog.removedSymbolicIds[WithSoftLinkEntity::class.java]?.size)
    assertNull(builder.changeLog.addedSymbolicIds[WithSoftLinkEntity::class.java])
    assertTrue(NameId("Name") in builder.changeLog.removedSymbolicIds[WithSoftLinkEntity::class.java]!!)
  }

  @Test
  fun `many entity types one symbolic id`() {
    val builder = createEmptyBuilder()
    val namedEntity = builder addEntity NamedEntity("Name", MySource)
    val entity = builder addEntity WithSoftLinkEntity(namedEntity.symbolicId, MySource)
    builder addEntity ComposedIdSoftRefEntity("AnotherType", namedEntity.symbolicId, MySource)
    assertTrue(NameId("Name") in builder.changeLog.addedSymbolicIds[WithSoftLinkEntity::class.java]!!)
    assertTrue(NameId("Name") in builder.changeLog.addedSymbolicIds[ComposedIdSoftRefEntity::class.java]!!)
    builder.removeEntity(entity)
    assertTrue(NameId("Name") in builder.changeLog.removedSymbolicIds[WithSoftLinkEntity::class.java]!!)
    assertNull(builder.changeLog.addedSymbolicIds[WithSoftLinkEntity::class.java])
  }
}
