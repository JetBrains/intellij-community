// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.entities.api.AttachedEntityList
import com.intellij.workspaceModel.storage.entities.api.MainEntityList
import com.intellij.workspaceModel.storage.entities.api.MySource
import com.intellij.workspaceModel.storage.entities.api.child
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import org.jetbrains.deft.IntellijWs.child

class ExtensionListTest {
  @Test
  fun `access by extension without builder`() {
    val entity = AttachedEntityList {
      this.entitySource = MySource
      data = "xyz"
      ref = MainEntityList {
        this.entitySource = MySource
        this.x = "123"
      }
    }

    assertEquals("xyz", entity.data)
    val ref = entity.ref
    val children = ref!!.child
    assertEquals("xyz", children.single().data)
  }

  @Test
  fun `access by extension without builder on parent`() {
    val entity = MainEntityList {
      this.entitySource = MySource
      this.x = "123"
      this.child = listOf(
        AttachedEntityList {
          this.entitySource = MySource
          data = "xyz"
        }
      )
    }

    assertEquals("123", entity.x)
    val ref = entity.child.single()
    val children = ref.ref
    assertEquals("123", children!!.x)
  }

  @Test
  fun `access by extension without builder on parent with an additional child`() {
    val entity = MainEntityList {
      this.entitySource = MySource
      this.x = "123"
      this.child = listOf(
        AttachedEntityList {
          this.entitySource = MySource
          data = "xyz"
        }
      )
    }
    val newChild = AttachedEntityList {
      this.data = "abc"
      this.ref = entity
    }

    assertEquals("123", entity.x)
    val ref = entity.child.first()
    val children = ref.ref
    assertEquals("123", children!!.x)

    assertEquals(2, newChild.ref!!.child.size)
  }

  @Test
  fun `access by extension`() {
    val entity = AttachedEntityList {
      this.entitySource = MySource
      data = "xyz"
      ref = MainEntityList {
        this.entitySource = MySource
        this.x = "123"
      }
    }
    val builder = createEmptyBuilder()
    builder.addEntity(entity)

    assertEquals("xyz", entity.data)
    val ref = entity.ref
    val children = ref!!.child
    assertEquals("xyz", children.single().data)

    assertEquals("xyz", builder.entities(AttachedEntityList::class.java).single().data)
    assertEquals("123", builder.entities(MainEntityList::class.java).single().x)
    assertEquals("xyz", builder.entities(MainEntityList::class.java).single().child.single().data)
    assertEquals("123", builder.entities(AttachedEntityList::class.java).single().ref!!.x)
  }

  @Test
  fun `access by extension on parent`() {
    val entity = MainEntityList {
      this.entitySource = MySource
      this.x = "123"
      this.child = listOf(
        AttachedEntityList {
          this.entitySource = MySource
          data = "xyz"
        }
      )
    }
    val builder = createEmptyBuilder()
    builder.addEntity(entity)

    assertEquals("123", entity.x)
    val ref = entity.child.single()
    val children = ref.ref
    assertEquals("123", children!!.x)

    assertEquals("xyz", builder.entities(AttachedEntityList::class.java).single().data)
    assertEquals("123", builder.entities(MainEntityList::class.java).single().x)
    assertEquals("xyz", builder.entities(MainEntityList::class.java).single().child.single().data)
    assertEquals("123", builder.entities(AttachedEntityList::class.java).single().ref!!.x)
  }

  @Test
  fun `add via single child`() {
    val child = AttachedEntityList {
      this.entitySource = MySource
      data = "abc"
    }
    val entity = MainEntityList {
      this.entitySource = MySource
      this.x = "123"
      this.child = listOf(
        AttachedEntityList {
          this.entitySource = MySource
          data = "xyz"
        },
        child
      )
    }
    val builder = createEmptyBuilder()
    builder.addEntity(child)

    assertEquals("123", entity.x)
    val ref = entity.child.first()
    val children = ref.ref
    assertEquals("123", children!!.x)

    assertEquals("xyz", builder.entities(AttachedEntityList::class.java).single { it.data == "xyz" }.data)
    assertEquals("123", builder.entities(MainEntityList::class.java).single().x)
    assertEquals("xyz", builder.entities(MainEntityList::class.java).single().child.single { it.data == "xyz" }.data)
    assertEquals("123", builder.entities(AttachedEntityList::class.java).single { it.data == "xyz" }.ref!!.x)
  }

  @Test
  fun `partially in builder`() {
    val entity = MainEntityList {
      this.entitySource = MySource
      this.x = "123"
      this.child = listOf(
        AttachedEntityList {
          this.entitySource = MySource
          data = "xyz"
        },
      )
    }
    val builder = createEmptyBuilder()
    builder.addEntity(entity)
    val child = AttachedEntityList {
      this.entitySource = MySource
      this.data = "abc"
      this.ref = entity
    }

    assertEquals(2, entity.child.size)

    assertEquals("xyz", entity.child.single { it.data == "xyz" }.data)
    assertEquals("abc", entity.child.single { it.data == "abc" }.data)

    assertEquals("123", child.ref!!.x)

    assertEquals("123", entity.x)
    val ref = entity.child.first()
    val children = ref.ref
    assertEquals("123", children!!.x)

    assertEquals("xyz", builder.entities(AttachedEntityList::class.java).single { it.data == "xyz" }.data)
    assertEquals("123", builder.entities(MainEntityList::class.java).single().x)
    assertEquals("xyz", builder.entities(MainEntityList::class.java).single().child.single { it.data == "xyz" }.data)
    assertEquals("123", builder.entities(AttachedEntityList::class.java).single { it.data == "xyz" }.ref!!.x)
  }
}
