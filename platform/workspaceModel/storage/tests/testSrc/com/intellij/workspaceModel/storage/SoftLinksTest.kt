// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.testFramework.UsefulTestCase.assertOneElement
import com.intellij.workspaceModel.storage.entities.test.api.*
import com.intellij.workspaceModel.storage.impl.assertConsistency
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertNotNull
import com.intellij.workspaceModel.storage.entities.test.api.modifyEntity
import org.junit.Test
import org.junit.jupiter.api.assertAll

/**
 * Soft reference
 * Persistent id via soft reference
 * Persistent id via strong reference
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

    // Change persistent id in a different builder
    val newBuilder = createBuilderFrom(builder.toSnapshot())
    val entity = newBuilder.resolve(NameId(id))!!
    newBuilder.modifyEntity(entity) {
      this.myName = newId
    }

    // Apply changes
    builder.addDiff(newBuilder)

    // Check
    assertNotNull(builder.resolve(NameId(newId)))
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

    // Change persistent id in a different builder
    val newBuilder = createBuilderFrom(builder.toSnapshot())
    val entity = newBuilder.resolve(NameId(id))!!
    newBuilder.modifyEntity(entity) {
      this.myName = newId
    }

    // Apply changes
    builder.addDiff(newBuilder)

    // Check
    assertNotNull(builder.resolve(NameId(newId)))
    assertOneElement(builder.referrers(NameId(newId), WithSoftLinkEntity::class.java).toList())

    // Change persistent id to the initial value
    val anotherNewBuilder = createBuilderFrom(builder.toSnapshot())
    val anotherEntity = anotherNewBuilder.resolve(NameId(newId))!!
    anotherNewBuilder.modifyEntity(anotherEntity) {
      this.myName = id
    }

    // Apply changes
    builder.addDiff(anotherNewBuilder)

    // Check
    assertNotNull(builder.resolve(NameId(id)))
    assertOneElement(builder.referrers(NameId(id), WithSoftLinkEntity::class.java).toList())
  }

  @Test
  fun `change persistent id part`() {
    val builder = createEmptyBuilder()
    val entity = builder.addNamedEntity("Name")
    builder.addWithSoftLinkEntity(entity.persistentId)

    builder.modifyEntity(entity) {
      this.myName = "newName"
    }

    builder.assertConsistency()

    assertEquals("newName", builder.entities(WithSoftLinkEntity::class.java).single().link.presentableName)
  }

  @Test
  fun `change persistent id part of composed id entity`() {
    val builder = createEmptyBuilder()
    val entity = builder.addNamedEntity("Name")
    builder.addComposedIdSoftRefEntity("AnotherName", entity.persistentId)

    builder.modifyEntity(entity) {
      this.myName = "newName"
    }

    builder.assertConsistency()

    val updatedPersistentId = builder.entities(ComposedIdSoftRefEntity::class.java).single().persistentId
    assertEquals("newName", updatedPersistentId.link.presentableName)
  }

  @Test
  fun `change persistent id in list`() {
    val builder = createEmptyBuilder()
    val entity = builder.addNamedEntity("Name")
    builder.addEntity(WithListSoftLinksEntity("xyz", listOf(NameId("Name")), MySource))

    builder.modifyEntity(entity) {
      this.myName = "newName"
    }

    builder.assertConsistency()

    val updatedPersistentId = builder.entities(WithListSoftLinksEntity::class.java).single()
    assertEquals("newName", updatedPersistentId.links.single().presentableName)
  }

  @Test
  fun `change persistent id part of composed id entity and with linked entity`() {
    val builder = createEmptyBuilder()
    val entity = builder.addNamedEntity("Name")
    val composedIdEntity = builder.addComposedIdSoftRefEntity("AnotherName", entity.persistentId)
    builder.addComposedLinkEntity(composedIdEntity.persistentId)

    builder.modifyEntity(entity) {
      this.myName = "newName"
    }

    builder.assertConsistency()

    val updatedPersistentId = builder.entities(ComposedIdSoftRefEntity::class.java).single().persistentId
    assertEquals("newName", updatedPersistentId.link.presentableName)
    assertEquals("newName", builder.entities(ComposedLinkEntity::class.java).single().link.link.presentableName)
  }

  @Test
  fun `links change`() {
    val builder = MutableEntityStorage.create()

    val entity = OneEntityWithPersistentId("Data", MySource)
    builder.addEntity(entity)
    val persistentId = entity.persistentId
    val softLinkEntity = EntityWithSoftLinks(persistentId,
                                             listOf(persistentId),
                                             Container(persistentId),
                                             listOf(Container(persistentId)),
                                             listOf(TooDeepContainer(listOf(DeepContainer(listOf(Container(persistentId)), persistentId)))),
                                             SealedContainer.BigContainer(persistentId),
                                             listOf(SealedContainer.SmallContainer(persistentId)),
                                             "Hello",
                                             listOf("Hello"),
                                             DeepSealedOne.DeepSealedTwo.DeepSealedThree.DeepSealedFour(persistentId),
                                             MySource
    ) {
      optionalLink = persistentId
      inOptionalContainer = Container(persistentId)
      justNullableProperty = "Hello"
      this.children = emptyList()
    }
    builder.addEntity(softLinkEntity)

    builder.modifyEntity(OneEntityWithPersistentId.Builder::class.java, entity) {
      myName = "AnotherData"
    }

    val updatedEntity = builder.entities(EntityWithSoftLinks::class.java).single()

    assertEquals("AnotherData", updatedEntity.link.name)
    assertEquals("AnotherData", updatedEntity.manyLinks.single().name)
    assertEquals("AnotherData", updatedEntity.optionalLink!!.name)
    assertEquals("AnotherData", updatedEntity.inContainer.id.name)
    assertEquals("AnotherData", updatedEntity.inOptionalContainer!!.id.name)
    assertEquals("AnotherData", updatedEntity.inContainerList.single().id.name)
    assertEquals(
      "AnotherData",
      updatedEntity.deepContainer.single().goDeeper.single().goDeep.single().id.name
    )
    assertEquals("AnotherData", (updatedEntity.sealedContainer as SealedContainer.BigContainer).id.name)
    assertEquals(
      "AnotherData",
      (updatedEntity.listSealedContainer.single() as SealedContainer.SmallContainer).notId.name
    )
    assertEquals(
      "AnotherData",
      (updatedEntity.deepSealedClass as DeepSealedOne.DeepSealedTwo.DeepSealedThree.DeepSealedFour).id.name
    )
  }
}
