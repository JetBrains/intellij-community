//.indexes Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.testFramework.UsefulTestCase.assertOneElement
import com.intellij.workspace.api.EntitySource
import com.intellij.workspace.api.PersistentEntityId
import com.intellij.workspace.api.TypedEntityStorage
import com.intellij.workspace.api.TypedEntityWithPersistentId
import junit.framework.Assert.assertNotNull
import org.junit.Test

class SoftLinksTest {
  @Test
  fun `test add diff with soft links`() {
    val id = "MyId"
    val newId = "MyNewId"

    // Setup builder for test
    val builder = PEntityStorageBuilder.create()
    builder.addEntity(ModifiableWithSoftLinkEntity::class.java, MySource) {
      this.link = NameId(id)
    }
    builder.addEntity(ModifiableNamedEntity::class.java, MySource) {
      name = id
    }

    // Change persistent id in a different builder
    val newBuilder = PEntityStorageBuilder.from(builder.toStorage())
    val entity = newBuilder.resolve(NameId(id))!!
    newBuilder.modifyEntity(ModifiableNamedEntity::class.java, entity) {
      this.name = newId
    }

    // Apply changes
    builder.addDiff(newBuilder)

    // Check
    assertNotNull(builder.resolve(NameId(newId)))
    assertOneElement(builder.indexes.softLinks.get(NameId(newId)))
  }

  @Test
  fun `test add diff with soft links and back`() {
    val id = "MyId"
    val newId = "MyNewId"

    // Setup builder for test
    val builder = PEntityStorageBuilder.create()
    builder.addEntity(ModifiableWithSoftLinkEntity::class.java, MySource) {
      this.link = NameId(id)
    }
    builder.addEntity(ModifiableNamedEntity::class.java, MySource) {
      name = id
    }

    // Change persistent id in a different builder
    val newBuilder = PEntityStorageBuilder.from(builder.toStorage())
    val entity = newBuilder.resolve(NameId(id))!!
    newBuilder.modifyEntity(ModifiableNamedEntity::class.java, entity) {
      this.name = newId
    }

    // Apply changes
    builder.addDiff(newBuilder)

    // Check
    assertNotNull(builder.resolve(NameId(newId)))
    assertOneElement(builder.indexes.softLinks.get(NameId(newId)))

    // Change persistent id to the initial value
    val anotherNewBuilder = PEntityStorageBuilder.from(builder.toStorage())
    val anotherEntity = anotherNewBuilder.resolve(NameId(newId))!!
    anotherNewBuilder.modifyEntity(ModifiableNamedEntity::class.java, anotherEntity) {
      this.name = id
    }

    // Apply changes
    builder.addDiff(anotherNewBuilder)

    // Check
    assertNotNull(builder.resolve(NameId(id)))
    assertOneElement(builder.indexes.softLinks.get(NameId(id)))
  }
}