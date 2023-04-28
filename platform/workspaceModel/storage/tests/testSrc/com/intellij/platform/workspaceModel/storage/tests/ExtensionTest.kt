// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspaceModel.storage.tests

import com.intellij.platform.workspaceModel.storage.testEntities.entities.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExtensionTest {
  @Test
  fun `access by extension`() {
    val builder = createEmptyBuilder()
    builder.addEntity(AttachedEntity("xyz", MySource) {
      ref = MainEntity("123", MySource)
    })
    val child: AttachedEntity = builder.toSnapshot().entities(MainEntity::class.java).single().child!!
    assertEquals("xyz", child.data)

    assertTrue(builder.entities(MainEntity::class.java).toList().isNotEmpty())
    assertTrue(builder.entities(AttachedEntity::class.java).toList().isNotEmpty())
  }

  @Test
  fun `access by extension without builder`() {
    val entity = AttachedEntity("xyz", MySource) {
      ref = MainEntity("123", MySource)
    }

    assertEquals("xyz", entity.data)
    val ref = entity.ref
    val children = ref.child!!
    assertEquals("xyz", children.data)
  }

  @Test
  fun `access by extension opposite`() {
    val builder = createEmptyBuilder()
    builder.addEntity(MainEntity("123", MySource) {
      this.child = AttachedEntity("xyz", MySource)
    })
    val child: AttachedEntity = builder.toSnapshot().entities(MainEntity::class.java).single().child!!
    assertEquals("xyz", child.data)

    assertTrue(builder.entities(MainEntity::class.java).toList().isNotEmpty())
    assertTrue(builder.entities(AttachedEntity::class.java).toList().isNotEmpty())
  }

  @Test
  fun `access by extension opposite in builder`() {
    val builder = createEmptyBuilder()
    val entity = MainEntity("123", MySource) {
      this.child = AttachedEntity("xyz", MySource)
    }
    builder.addEntity(entity)
    assertEquals("xyz", entity.child!!.data)
  }

  @Test
  fun `access by extension opposite in modification`() {
    val builder = createEmptyBuilder()
    val entity = MainEntity("123", MySource) {
      this.child = AttachedEntity("xyz", MySource)
    }
    builder.addEntity(entity)
    val anotherChild = AttachedEntity("abc", MySource)

    builder.modifyEntity(entity) {
      assertEquals("xyz", this.child!!.data)
      this.child = anotherChild
    }

    assertTrue(builder.entities(MainEntity::class.java).toList().isNotEmpty())
    assertEquals("abc", builder.entities(AttachedEntity::class.java).single().data)
  }

  @Test
  fun `access by extension opposite without builder`() {
    val entity = MainEntity("123", MySource) {
      this.child = AttachedEntity("xyz", MySource)
    }

    assertEquals("123", entity.x)
    val ref = entity.child!!
    val children = ref.ref
    assertEquals("123", children.x)
  }
}
