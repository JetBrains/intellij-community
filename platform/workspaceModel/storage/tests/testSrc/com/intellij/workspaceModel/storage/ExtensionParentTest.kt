package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.entities.test.api.AttachedEntityToParent
import com.intellij.workspaceModel.storage.entities.test.api.MainEntityToParent
import com.intellij.workspaceModel.storage.entities.test.api.MySource
import com.intellij.workspaceModel.storage.entities.test.api.ref
import org.jetbrains.deft.TestEntities.modifyEntity
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.deft.TestEntities.ref

class ExtensionParentTest {
  @Test
  fun `access by extension`() {
    val builder = createEmptyBuilder()
    builder.addEntity(AttachedEntityToParent("xyz", MySource) {
      ref = MainEntityToParent(MySource, "123")
    })
    val child: AttachedEntityToParent = builder.toSnapshot().entities(MainEntityToParent::class.java).single().child!!
    assertEquals("xyz", child.data)

    assertTrue(builder.entities(MainEntityToParent::class.java).toList().isNotEmpty())
    assertTrue(builder.entities(AttachedEntityToParent::class.java).toList().isNotEmpty())
  }

  @Test
  fun `access by extension without builder`() {
    val entity = AttachedEntityToParent("xyz", MySource) {
      ref = MainEntityToParent(MySource, "123")
    }

    assertEquals("xyz", entity.data)
    val ref = entity.ref
    val children = ref.child!!
    assertEquals("xyz", children.data)
  }

  @Test
  fun `access by extension opposite`() {
    val builder = createEmptyBuilder()
    builder.addEntity(MainEntityToParent(MySource, "123") {
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
    val entity = MainEntityToParent(MySource, "123") {
      this.child = AttachedEntityToParent("xyz", MySource)
    }
    builder.addEntity(entity)
    assertEquals("xyz", entity.child!!.data)
  }

  @Test
  fun `access by extension opposite in modification`() {
    val builder = createEmptyBuilder()
    val entity = MainEntityToParent(MySource, "123") {
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
    val entity = MainEntityToParent(MySource, "123") {
      this.child = AttachedEntityToParent("xyz", MySource)
    }

    assertEquals("123", entity.x)
    val ref = entity.child!!
    val children = ref.ref
    assertEquals("123", children.x)
  }
}
