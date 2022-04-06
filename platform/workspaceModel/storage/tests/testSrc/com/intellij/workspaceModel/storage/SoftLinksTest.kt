// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.testFramework.UsefulTestCase.assertOneElement
import com.intellij.workspaceModel.storage.entities.api.*
import com.intellij.workspaceModel.storage.impl.assertConsistency
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertNotNull
import org.jetbrains.deft.IntellijWs.testEntities.modifyEntity
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
    builder.addEntity(WithSoftLinkEntity {
      this.entitySource = MySource
      this.link = NameId(id)
    })
    builder.addEntity(NamedEntity {
      this.entitySource = MySource
      this.myName = id
      this.children = emptyList()
    })

    // Change persistent id in a different builder
    val newBuilder = createBuilderFrom(builder.toStorage())
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
    builder.addEntity(WithSoftLinkEntity {
      this.entitySource = MySource
      this.link = NameId(id)
    })
    builder.addEntity(NamedEntity {
      this.entitySource = MySource
      this.myName = id
      this.children = emptyList()
    })

    // Change persistent id in a different builder
    val newBuilder = createBuilderFrom(builder.toStorage())
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
    val anotherNewBuilder = createBuilderFrom(builder.toStorage())
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
    assertEquals("newName", (builder.entities(ComposedLinkEntity::class.java).single().link as ComposedId).link.presentableName)
  }

  @Test
  fun `links change`() {
    val builder = WorkspaceEntityStorageBuilder.create()

    val entity = OneEntityWithPersistentId {
      entitySource = MySource
      myName = "Data"
    }
    builder.addEntity(entity)
    val persistentId = entity.persistentId
    val softLinkEntity = EntityWithSoftLinks {
      entitySource = MySource
      link = persistentId
      manyLinks = listOf(persistentId)
      optionalLink = persistentId
      inContainer = Container(persistentId)
      inOptionalContainer = Container(persistentId)
      inContainerList = listOf(Container(persistentId))
      deepContainer =
        listOf(TooDeepContainer(listOf(DeepContainer(listOf(Container(persistentId)), persistentId))))

      sealedContainer = SealedContainer.BigContainer(persistentId)
      listSealedContainer = listOf(SealedContainer.SmallContainer(persistentId))

      justProperty = "Hello"
      justNullableProperty = "Hello"
      justListProperty = listOf("Hello")
    }
    builder.addEntity(softLinkEntity)

    builder.modifyEntity(OneEntityWithPersistentIdImpl.Builder::class.java, entity) {
      myName = "AnotherData"
    }

    val updatedEntity = builder.entities(EntityWithSoftLinks::class.java).single()
    assertAll(
      { kotlin.test.assertEquals("AnotherData", updatedEntity.link.name) },
      { kotlin.test.assertEquals("AnotherData", updatedEntity.manyLinks.single().name) },
      { kotlin.test.assertEquals("AnotherData", updatedEntity.optionalLink!!.name) },
      { kotlin.test.assertEquals("AnotherData", updatedEntity.inContainer.id.name) },
      { kotlin.test.assertEquals("AnotherData", updatedEntity.inOptionalContainer!!.id.name) },
      { kotlin.test.assertEquals("AnotherData", updatedEntity.inContainerList.single().id.name) },
      {
        kotlin.test.assertEquals(
          "AnotherData",
          updatedEntity.deepContainer.single().goDeeper.single().goDeep.single().id.name
        )
      },
      { kotlin.test.assertEquals("AnotherData", (updatedEntity.sealedContainer as SealedContainer.BigContainer).id.name) },
      {
        kotlin.test.assertEquals(
          "AnotherData",
          (updatedEntity.listSealedContainer.single() as SealedContainer.SmallContainer).notId.name
        )
      },
    )
  }
}
