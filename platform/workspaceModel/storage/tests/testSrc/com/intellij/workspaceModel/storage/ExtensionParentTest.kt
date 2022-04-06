package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.createEmptyBuilder
import com.intellij.workspaceModel.storage.entities.api.AttachedEntityToParent
import com.intellij.workspaceModel.storage.entities.api.MainEntityToParent
import com.intellij.workspaceModel.storage.entities.api.MySource
import com.intellij.workspaceModel.storage.entities.api.ref
import org.jetbrains.deft.IntellijWs.modifyEntity
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.deft.IntellijWs.ref

class ExtensionParentTest {
  @Test
  fun `access by extension`() {
    val builder = createEmptyBuilder()
    builder.addEntity(AttachedEntityToParent {
      this.entitySource = MySource
      data = "xyz"
      ref = MainEntityToParent {
        this.entitySource = MySource
        this.x = "123"
      }
    })
    val child: AttachedEntityToParent = builder.toStorage().entities(MainEntityToParent::class.java).single().child!!
    assertEquals("xyz", child.data)

    assertTrue(builder.entities(MainEntityToParent::class.java).toList().isNotEmpty())
    assertTrue(builder.entities(AttachedEntityToParent::class.java).toList().isNotEmpty())
  }

  @Test
  fun `access by extension without builder`() {
    val entity = AttachedEntityToParent {
      this.entitySource = MySource
      data = "xyz"
      ref = MainEntityToParent {
        this.entitySource = MySource
        this.x = "123"
      }
    }

    assertEquals("xyz", entity.data)
    val ref = entity.ref
    val children = ref.child!!
    assertEquals("xyz", children.data)
  }

  @Test
  fun `access by extension opposite`() {
    val builder = createEmptyBuilder()
    builder.addEntity(MainEntityToParent {
      this.x = "123"
      this.entitySource = MySource
      this.child = AttachedEntityToParent {
        this.entitySource = MySource
        data = "xyz"
      }
    })
    val child: AttachedEntityToParent = builder.toStorage().entities(MainEntityToParent::class.java).single().child!!
    assertEquals("xyz", child.data)

    assertTrue(builder.entities(MainEntityToParent::class.java).toList().isNotEmpty())
    assertTrue(builder.entities(AttachedEntityToParent::class.java).toList().isNotEmpty())
  }

  @Test
  fun `access by extension opposite in builder`() {
    val builder = createEmptyBuilder()
    val entity = MainEntityToParent {
      this.x = "123"
      this.entitySource = MySource
      this.child = AttachedEntityToParent {
        this.entitySource = MySource
        data = "xyz"
      }
    }
    builder.addEntity(entity)
    assertEquals("xyz", entity.child!!.data)
  }

  @Test
  fun `access by extension opposite in modification`() {
    val builder = createEmptyBuilder()
    val entity = MainEntityToParent {
      this.x = "123"
      this.entitySource = MySource
      this.child = AttachedEntityToParent {
        this.entitySource = MySource
        data = "xyz"
      }
    }
    builder.addEntity(entity)
    val anotherChild = AttachedEntityToParent {
      this.entitySource = MySource
      data = "abc"
    }

    builder.modifyEntity(entity) {
      assertEquals("xyz", this.child!!.data)
      this.child = anotherChild
    }

    assertTrue(builder.entities(MainEntityToParent::class.java).toList().isNotEmpty())
    assertEquals("abc", builder.entities(AttachedEntityToParent::class.java).single().data)
  }

  @Test
  fun `access by extension opposite without builder`() {
    val entity = MainEntityToParent {
      this.x = "123"
      this.entitySource = MySource
      this.child = AttachedEntityToParent {
        this.entitySource = MySource
        data = "xyz"
      }
    }

    assertEquals("123", entity.x)
    val ref = entity.child!!
    val children = ref.ref
    assertEquals("123", children.x)
  }
}
