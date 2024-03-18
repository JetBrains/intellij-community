// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.assertConsistency
import com.intellij.platform.workspace.storage.testEntities.entities.*
import com.intellij.testFramework.UsefulTestCase.assertOneElement
import org.junit.jupiter.api.Test
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
    newBuilder.modifyEntity(entity) {
      this.myName = newId
    }

    // Apply changes
    builder.applyChangesFrom(newBuilder)

    // Check
    assertTrue(NameId(newId) in builder)
    assertOneElement(builder.referrers(NameId(newId), WithSoftLinkEntity::class.java).toList())
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
    newBuilder.modifyEntity(entity) {
      this.myName = newId
    }

    // Apply changes
    builder.applyChangesFrom(newBuilder)

    // Check
    assertNotNull(NameId(newId) in builder)
    assertOneElement(builder.referrers(NameId(newId), WithSoftLinkEntity::class.java).toList())

    // Change symbolic id to the initial value
    val anotherNewBuilder = createBuilderFrom(builder.toSnapshot())
    val anotherEntity = anotherNewBuilder.resolve(NameId(newId))!!
    anotherNewBuilder.modifyEntity(anotherEntity) {
      this.myName = id
    }

    // Apply changes
    builder.applyChangesFrom(anotherNewBuilder)

    // Check
    assertNotNull(NameId(id) in builder)
    assertOneElement(builder.referrers(NameId(id), WithSoftLinkEntity::class.java).toList())
  }

  @Test
  fun `change symbolic id part`() {
    val builder = createEmptyBuilder()
    val entity = builder.addNamedEntity("Name")
    builder.addWithSoftLinkEntity(entity.symbolicId)

    builder.modifyEntity(entity) {
      this.myName = "newName"
    }

    builder.assertConsistency()

    assertEquals("newName", builder.entities(WithSoftLinkEntity::class.java).single().link.presentableName)
  }

  @Test
  fun `change symbolic id part of composed id entity`() {
    val builder = createEmptyBuilder()
    val entity = builder.addNamedEntity("Name")
    builder.addComposedIdSoftRefEntity("AnotherName", entity.symbolicId)

    builder.modifyEntity(entity) {
      this.myName = "newName"
    }

    builder.assertConsistency()

    val updatedSymbolicId = builder.entities(ComposedIdSoftRefEntity::class.java).single().symbolicId
    assertEquals("newName", updatedSymbolicId.link.presentableName)
  }

  @Test
  fun `change symbolic id in list`() {
    val builder = createEmptyBuilder()
    val entity = builder.addNamedEntity("Name")
    builder.addEntity(WithListSoftLinksEntity("xyz", listOf(NameId("Name")), MySource))

    builder.modifyEntity(entity) {
      this.myName = "newName"
    }

    builder.assertConsistency()

    val updatedSymbolicId = builder.entities(WithListSoftLinksEntity::class.java).single()
    assertEquals("newName", updatedSymbolicId.links.single().presentableName)
  }

  @Test
  fun `change symbolic id part of composed id entity and with linked entity`() {
    val builder = createEmptyBuilder()
    val entity = builder.addNamedEntity("Name")
    val composedIdEntity = builder.addComposedIdSoftRefEntity("AnotherName", entity.symbolicId)
    builder.addComposedLinkEntity(composedIdEntity.symbolicId)

    builder.modifyEntity(entity) {
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

    val entity = OneEntityWithSymbolicId("Data", MySource)
    builder.addEntity(entity)
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
}
