package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.entities.test.api.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExtensionParentTest {
  @Test
  fun `access by extension`() {
    val builder = createEmptyBuilder()
    builder.addEntity(AttachedEntityToParent("xyz", MySource) {
      ref = MainEntityToParent("123", MySource)
    })
    val child: AttachedEntityToParent = builder.toSnapshot().entities(MainEntityToParent::class.java).single().child!!
    assertEquals("xyz", child.data)

    assertTrue(builder.entities(MainEntityToParent::class.java).toList().isNotEmpty())
    assertTrue(builder.entities(AttachedEntityToParent::class.java).toList().isNotEmpty())
  }

  @Test
  fun `access by extension without builder`() {
    val entity = AttachedEntityToParent("xyz", MySource) {
      ref = MainEntityToParent("123", MySource)
    }

    assertEquals("xyz", entity.data)
    val ref = entity.ref
    val children = ref.child!!
    assertEquals("xyz", children.data)
  }

  @Test
  fun `access by extension opposite`() {
    val builder = createEmptyBuilder()
    builder.addEntity(MainEntityToParent("123", MySource) {
      this.child = AttachedEntityToParent("xyz", MySource)
    })
    val child: AttachedEntityToParent = builder.toSnapshot().entities(MainEntityToParent::class.java).single().child!!
    assertEquals("xyz", child.data)

    assertTrue(builder.entities(MainEntityToParent::class.java).toList().isNotEmpty())
    assertTrue(builder.entities(AttachedEntityToParent::class.java).toList().isNotEmpty())
  }

  @Test
  fun `access by extension opposite in builder`() {
    val builder = createEmptyBuilder()
    val entity = MainEntityToParent("123", MySource) {
      this.child = AttachedEntityToParent("xyz", MySource)
    }
    builder.addEntity(entity)
    assertEquals("xyz", entity.child!!.data)
  }

  @Test
  fun `access by extension opposite in modification`() {
    val builder = createEmptyBuilder()
    val entity = MainEntityToParent("123", MySource) {
      this.child = AttachedEntityToParent("xyz", MySource)
    }
    builder.addEntity(entity)
    val anotherChild = AttachedEntityToParent("abc", MySource)

    builder.modifyEntity(entity) {
      assertEquals("xyz", this.child!!.data)
      this.child = anotherChild
    }

    assertTrue(builder.entities(MainEntityToParent::class.java).toList().isNotEmpty())
    assertEquals("abc", builder.entities(AttachedEntityToParent::class.java).single().data)
  }

  @Test
  fun `access by extension opposite without builder`() {
    val entity = MainEntityToParent("123", MySource) {
      this.child = AttachedEntityToParent("xyz", MySource)
    }

    assertEquals("123", entity.x)
    val ref = entity.child!!
    val children = ref.ref
    assertEquals("123", children.x)
  }

  @Test
  fun `check reference via extension property removes correctly`() {
    val builder = createEmptyBuilder()
    val entity = MainEntityToParent("123", MySource) {
      this.childNullableParent = AttachedEntityToNullableParent("xyz", MySource)
    }
    builder.addEntity(entity)

    var existingMainEntity = builder.entities(MainEntityToParent::class.java).single()
    assertNotNull(existingMainEntity.childNullableParent)

    val existingAttachedEntity = builder.entities(AttachedEntityToNullableParent::class.java).single()
    assertNotNull(existingAttachedEntity.nullableRef)

    builder.modifyEntity(existingAttachedEntity) {
      this.nullableRef = null
    }

    existingMainEntity = builder.entities(MainEntityToParent::class.java).single()
    assertNull(existingMainEntity.child)
  }
}
